package com.efesert.wallreel.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.documentfile.provider.DocumentFile
import com.efesert.wallreel.playlist.PlaylistController
import com.efesert.wallreel.playlist.Prefs
import com.efesert.wallreel.scheduler.WallpaperScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

class Repository(private val context: Context) {

    private val dao = AppDatabase.get(context).dao()

    fun observeAlbums(): Flow<List<Album>> = dao.observeAlbums()
    fun observeAlbum(id: Long): Flow<Album?> = dao.observeAlbum(id)
    fun observePhotos(albumId: Long): Flow<List<Photo>> = dao.observePhotos(albumId)

    // ---- Albümler ----
    suspend fun createAlbum(name: String): Long = withContext(Dispatchers.IO) {
        dao.insertAlbum(Album(name = name.ifBlank { "Album" }, position = dao.getAlbumsOnce().size))
    }

    /**
     * Seçilen klasörü (SAF tree uri) yeni bir albüm olarak ekler ve içindeki
     * tüm resimleri otomatik kopyalar. Klasör adı albüm adı olur.
     * Eklenen fotoğraf sayısını döndürür (-1 = klasör okunamadı).
     */
    suspend fun createAlbumFromFolder(treeUri: Uri): Int = withContext(Dispatchers.IO) {
        val tree = DocumentFile.fromTreeUri(context, treeUri) ?: return@withContext -1
        // Klasöre tekrar erişebilmek (yenileme) için kalıcı okuma izni al.
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                treeUri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }
        val name = tree.name?.takeIf { it.isNotBlank() } ?: "Folder"
        val albumId = dao.insertAlbum(
            Album(
                name = name,
                position = dao.getAlbumsOnce().size,
                folderUri = treeUri.toString()
            )
        )
        val dir = File(context.filesDir, "albums/$albumId").apply { mkdirs() }
        var added = 0
        for (doc in tree.listFiles()) {
            val type = doc.type
            if (!doc.isFile || type == null || !type.startsWith("image/")) continue
            try {
                val file = File(dir, "${UUID.randomUUID()}.jpg")
                context.contentResolver.openInputStream(doc.uri)?.use { input ->
                    file.outputStream().use { output -> input.copyTo(output) }
                }
                if (file.exists() && file.length() > 0) {
                    dao.insertPhoto(
                        Photo(
                            albumId = albumId,
                            path = file.absolutePath,
                            displayName = doc.name,
                            sourceDate = doc.lastModified(),
                            position = added
                        )
                    )
                    added++
                }
            } catch (e: Exception) {
                // tek bir resim kopyalanamazsa diğerlerine devam et
            }
        }
        added
    }

    /**
     * Klasörden oluşturulmuş bir albümü kaynak klasörle yeniden eşitler:
     * klasöre eklenmiş YENİ resimleri (mevcut dosya adına göre) içe aktarır.
     * Eklenen yeni fotoğraf sayısını döndürür (-1 = klasöre erişilemedi).
     */
    suspend fun refreshFolderAlbum(album: Album): Int = withContext(Dispatchers.IO) {
        val uriStr = album.folderUri ?: return@withContext -1
        val tree = DocumentFile.fromTreeUri(context, Uri.parse(uriStr)) ?: return@withContext -1
        val existingNames = dao.getPhotos(album.id).mapNotNull { it.displayName }.toHashSet()
        val dir = File(context.filesDir, "albums/${album.id}").apply { mkdirs() }
        var nextPos = (dao.getPhotos(album.id).maxOfOrNull { it.position } ?: -1) + 1
        var added = 0
        for (doc in tree.listFiles()) {
            val type = doc.type
            if (!doc.isFile || type == null || !type.startsWith("image/")) continue
            val docName = doc.name
            // Zaten içe aktarılmış (aynı isimli) resimleri atla.
            if (docName != null && existingNames.contains(docName)) continue
            try {
                val file = File(dir, "${UUID.randomUUID()}.jpg")
                context.contentResolver.openInputStream(doc.uri)?.use { input ->
                    file.outputStream().use { output -> input.copyTo(output) }
                }
                if (file.exists() && file.length() > 0) {
                    dao.insertPhoto(
                        Photo(
                            albumId = album.id,
                            path = file.absolutePath,
                            displayName = docName,
                            sourceDate = doc.lastModified(),
                            position = nextPos++
                        )
                    )
                    if (docName != null) existingNames.add(docName)
                    added++
                }
            } catch (e: Exception) {
                // tek bir resim kopyalanamazsa diğerlerine devam et
            }
        }
        if (added > 0) refreshIfActive(album.id)
        added
    }

    suspend fun renameAlbum(album: Album, name: String) = withContext(Dispatchers.IO) {
        dao.updateAlbum(album.copy(name = name.ifBlank { album.name }))
    }

    /** Albümü ana ekranda bir yukarı/aşağı taşır ve tüm konumları normalize eder. */
    suspend fun moveAlbum(album: Album, up: Boolean) = withContext(Dispatchers.IO) {
        val list = dao.getAlbumsOnce().toMutableList()
        val idx = list.indexOfFirst { it.id == album.id }
        val target = if (up) idx - 1 else idx + 1
        if (idx < 0 || target < 0 || target >= list.size) return@withContext
        val tmp = list[idx]
        list[idx] = list[target]
        list[target] = tmp
        list.forEachIndexed { i, a ->
            if (a.position != i) dao.updateAlbum(a.copy(position = i))
        }
    }

    suspend fun deleteAlbum(album: Album) = withContext(Dispatchers.IO) {
        // Diskteki kopyalanmış fotoğrafları da temizle.
        dao.getPhotos(album.id).forEach { runCatching { File(it.path).delete() } }
        dao.deleteAlbum(album)
        if (album.isActive) refreshQueue()
    }

    suspend fun setActiveAlbum(album: Album) = withContext(Dispatchers.IO) {
        dao.clearActiveFlags()
        dao.setActiveFlag(album.id)
        refreshQueue()
    }

    suspend fun setActiveAlbum(albumId: Long) = withContext(Dispatchers.IO) {
        if (dao.getAlbumById(albumId) == null) return@withContext
        dao.clearActiveFlags()
        dao.setActiveFlag(albumId)
        refreshQueue()
    }

    suspend fun setAlbumScale(album: Album, scaleMode: String) = withContext(Dispatchers.IO) {
        dao.updateAlbum(album.copy(scaleMode = scaleMode))
        // Sırayı/konumu bozmadan sadece scale'leri güncelle.
        if (album.isActive) reapplyScales()
    }

    // ---- Fotoğraflar ----
    /** Seçilen resimleri uygulamanın iç depolamasına kopyalar (reboot sonrası da erişim için). */
    suspend fun addPhotos(albumId: Long, uris: List<Uri>) = withContext(Dispatchers.IO) {
        val dir = File(context.filesDir, "albums/$albumId").apply { mkdirs() }
        // Yeni fotoğraflar listenin SONUNA eklenir (mevcut en yüksek pozisyondan sonra).
        var nextPos = (dao.getPhotos(albumId).maxOfOrNull { it.position } ?: -1) + 1
        for (uri in uris) {
            try {
                val (origName, origDate) = queryMeta(uri)
                val file = File(dir, "${UUID.randomUUID()}.jpg")
                context.contentResolver.openInputStream(uri)?.use { input ->
                    file.outputStream().use { output -> input.copyTo(output) }
                }
                if (file.exists() && file.length() > 0) {
                    dao.insertPhoto(
                        Photo(
                            albumId = albumId,
                            path = file.absolutePath,
                            displayName = origName,
                            sourceDate = origDate,
                            position = nextPos++
                        )
                    )
                }
            } catch (e: Exception) {
                // tek bir resim kopyalanamazsa diğerlerine devam et
            }
        }
        refreshIfActive(albumId)
    }

    /** Fotoğrafı albüm içinde bir yukarı/aşağı taşır ve pozisyonları normalize eder. */
    suspend fun movePhoto(photo: Photo, up: Boolean) = withContext(Dispatchers.IO) {
        val list = dao.getPhotos(photo.albumId).toMutableList()
        val idx = list.indexOfFirst { it.id == photo.id }
        val target = if (up) idx - 1 else idx + 1
        if (idx < 0 || target < 0 || target >= list.size) return@withContext
        val tmp = list[idx]
        list[idx] = list[target]
        list[target] = tmp
        list.forEachIndexed { i, p ->
            if (p.position != i) dao.updatePhoto(p.copy(position = i))
        }
        refreshIfActive(photo.albumId)
    }

    /** Bir content uri'sinden orijinal dosya adı ve değiştirilme tarihini okur. */
    private fun queryMeta(uri: Uri): Pair<String?, Long> {
        var name: String? = null
        var modified = 0L
        try {
            context.contentResolver.query(uri, null, null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    val nameIdx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIdx >= 0 && !c.isNull(nameIdx)) name = c.getString(nameIdx)
                    val modIdx = c.getColumnIndex(android.provider.DocumentsContract.Document.COLUMN_LAST_MODIFIED)
                    if (modIdx >= 0 && !c.isNull(modIdx)) modified = c.getLong(modIdx)
                }
            }
        } catch (e: Exception) {
            // meta okunamazsa boş bırak
        }
        return name to modified
    }

    suspend fun setPhotoScale(photo: Photo, scaleMode: String) = withContext(Dispatchers.IO) {
        dao.updatePhoto(photo.copy(scaleMode = scaleMode))
        // Sadece o an gösterilen fotoğraf düzenlendiyse yerinde güncellenir,
        // başka fotoğrafa geçilmez.
        if (dao.getActiveAlbum()?.id == photo.albumId) reapplyScales()
    }

    suspend fun deletePhoto(photo: Photo) = withContext(Dispatchers.IO) {
        runCatching { File(photo.path).delete() }
        dao.deletePhoto(photo)
        refreshIfActive(photo.albumId)
    }

    /** Birden fazla fotoğrafı albümden kaldırır (kopyalanan dosyaları da siler). */
    suspend fun deletePhotos(photos: List<Photo>) = withContext(Dispatchers.IO) {
        if (photos.isEmpty()) return@withContext
        photos.forEach { photo ->
            runCatching { File(photo.path).delete() }
            dao.deletePhoto(photo)
        }
        refreshIfActive(photos.first().albumId)
    }

    /**
     * Seçilen fotoğrafı hemen duvar kağıdı yapar (çift dokunma gibi ama foto seçilebilir).
     * Foto aktif albümde değilse o albümü önce aktif yapar. Timer sıfırlanır.
     */
    suspend fun setAsWallpaper(photo: Photo) = withContext(Dispatchers.IO) {
        val active = dao.getActiveAlbum()
        if (active == null || active.id != photo.albumId) {
            dao.clearActiveFlags()
            dao.setActiveFlag(photo.albumId)
            refreshQueue()
        }
        var ok = PlaylistController.jumpTo(context, photo.path)
        if (!ok) {
            // Kuyrukta yoksa (ör. yeni eklenmiş) tazele ve tekrar dene.
            refreshQueue()
            ok = PlaylistController.jumpTo(context, photo.path)
        }
        // Timer'ı yeni değişim zamanına göre yeniden kur.
        WallpaperScheduler.schedule(context)
    }

    /** Kuyruktaki belirli bir yola atlar (in-app queue önizlemesinden dokunma). */
    suspend fun jumpToQueuePath(path: String) = withContext(Dispatchers.IO) {
        if (PlaylistController.jumpTo(context, path)) {
            WallpaperScheduler.schedule(context)
        }
    }

    private suspend fun refreshIfActive(albumId: Long) {
        if (dao.getActiveAlbum()?.id == albumId) refreshQueue()
    }

    /**
     * Mevcut sırayı/konumu KORUYARAK sadece scale değerlerini yeniden uygular.
     * Scale düzenlendiğinde fotoğrafın atlamaması için kullanılır.
     */
    private suspend fun reapplyScales() {
        val active = dao.getActiveAlbum() ?: return
        val photos = dao.getPhotos(active.id)
        val map = photos.associate { photo ->
            val resolved = if (photo.scaleMode == ScaleMode.ALBUM) active.scaleMode else photo.scaleMode
            photo.path to resolved
        }
        PlaylistController.updateScales(context, map)
    }

    /**
     * Aktif albümün fotoğraflarından playlist sırasını yeniden kurar.
     * Shuffle kapalıysa seçili sıralama modunu (PhotoSort) izler -> gördüğün
     * sırayla çalar. O an gösterilen fotoğraf korunur (foto ekleme/çıkarma/taşıma
     * sırada atlamaya yol açmaz); foto artık yoksa veya albüm değiştiyse baştan başlar.
     */
    suspend fun refreshQueue() = withContext(Dispatchers.IO) {
        val active = dao.getActiveAlbum()
        if (active == null) {
            PlaylistController.rebuildPreserving(context, emptyList(), Prefs.shuffle(context))
            return@withContext
        }
        val photos = PhotoSort.sort(dao.getPhotos(active.id), Prefs.photoSort(context))
        val entries = photos.map { photo ->
            val resolved = if (photo.scaleMode == ScaleMode.ALBUM) active.scaleMode else photo.scaleMode
            PlaylistController.Entry(photo.path, resolved)
        }
        PlaylistController.rebuildPreserving(context, entries, Prefs.shuffle(context))
    }
}

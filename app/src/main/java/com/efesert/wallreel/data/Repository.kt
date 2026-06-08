package com.efesert.wallreel.data

import android.content.Context
import android.net.Uri
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
        dao.insertAlbum(Album(name = name.ifBlank { "Album" }))
    }

    /**
     * Seçilen klasörü (SAF tree uri) yeni bir albüm olarak ekler ve içindeki
     * tüm resimleri otomatik kopyalar. Klasör adı albüm adı olur.
     * Eklenen fotoğraf sayısını döndürür (-1 = klasör okunamadı).
     */
    suspend fun createAlbumFromFolder(treeUri: Uri): Int = withContext(Dispatchers.IO) {
        val tree = DocumentFile.fromTreeUri(context, treeUri) ?: return@withContext -1
        val name = tree.name?.takeIf { it.isNotBlank() } ?: "Folder"
        val albumId = dao.insertAlbum(Album(name = name))
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
                    dao.insertPhoto(Photo(albumId = albumId, path = file.absolutePath))
                    added++
                }
            } catch (e: Exception) {
                // tek bir resim kopyalanamazsa diğerlerine devam et
            }
        }
        added
    }

    suspend fun renameAlbum(album: Album, name: String) = withContext(Dispatchers.IO) {
        dao.updateAlbum(album.copy(name = name))
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

    suspend fun setAlbumScale(album: Album, scaleMode: String) = withContext(Dispatchers.IO) {
        dao.updateAlbum(album.copy(scaleMode = scaleMode))
        // Sırayı/konumu bozmadan sadece scale'leri güncelle.
        if (album.isActive) reapplyScales()
    }

    // ---- Fotoğraflar ----
    /** Seçilen resimleri uygulamanın iç depolamasına kopyalar (reboot sonrası da erişim için). */
    suspend fun addPhotos(albumId: Long, uris: List<Uri>) = withContext(Dispatchers.IO) {
        val dir = File(context.filesDir, "albums/$albumId").apply { mkdirs() }
        for (uri in uris) {
            try {
                val file = File(dir, "${UUID.randomUUID()}.jpg")
                context.contentResolver.openInputStream(uri)?.use { input ->
                    file.outputStream().use { output -> input.copyTo(output) }
                }
                if (file.exists() && file.length() > 0) {
                    dao.insertPhoto(Photo(albumId = albumId, path = file.absolutePath))
                }
            } catch (e: Exception) {
                // tek bir resim kopyalanamazsa diğerlerine devam et
            }
        }
        refreshIfActive(albumId)
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

    /** Aktif albümün fotoğraflarından playlist sırasını yeniden kurar. */
    suspend fun refreshQueue() = withContext(Dispatchers.IO) {
        val active = dao.getActiveAlbum()
        if (active == null) {
            PlaylistController.rebuild(context, emptyList(), Prefs.shuffle(context))
            return@withContext
        }
        val photos = dao.getPhotos(active.id)
        val entries = photos.map { photo ->
            val resolved = if (photo.scaleMode == ScaleMode.ALBUM) active.scaleMode else photo.scaleMode
            PlaylistController.Entry(photo.path, resolved)
        }
        PlaylistController.rebuild(context, entries, Prefs.shuffle(context))
    }
}

package com.efesert.wallreel.ui

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.efesert.wallreel.data.Album
import com.efesert.wallreel.data.Photo
import com.efesert.wallreel.data.Repository
import com.efesert.wallreel.playlist.PlaylistController
import com.efesert.wallreel.playlist.Prefs
import com.efesert.wallreel.scheduler.WallpaperScheduler
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = Repository(app)
    private val context get() = getApplication<Application>()

    val albums: StateFlow<List<Album>> =
        repo.observeAlbums().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _intervalMinutes = MutableStateFlow(Prefs.intervalMinutes(context))
    val intervalMinutes: StateFlow<Int> = _intervalMinutes.asStateFlow()

    private val _shuffle = MutableStateFlow(Prefs.shuffle(context))
    val shuffle: StateFlow<Boolean> = _shuffle.asStateFlow()

    // Klasörden albüm içe aktarılırken true (UI'da ilerleme göstergesi için).
    private val _importing = MutableStateFlow(false)
    val importing: StateFlow<Boolean> = _importing.asStateFlow()

    // Ana ekranda çift dokunma ile fotoğraf değiştirme açık mı?
    private val _doubleTapEnabled = MutableStateFlow(Prefs.doubleTapEnabled(context))
    val doubleTapEnabled: StateFlow<Boolean> = _doubleTapEnabled.asStateFlow()

    fun setDoubleTapEnabled(value: Boolean) {
        _doubleTapEnabled.value = value
        Prefs.setDoubleTapEnabled(context, value)
    }

    // Albüm ekranındaki fotoğraf sıralama modu (gösterim + shuffle kapalıyken kuyruk).
    private val _photoSort = MutableStateFlow(Prefs.photoSort(context))
    val photoSort: StateFlow<String> = _photoSort.asStateFlow()

    fun setPhotoSort(mode: String) = viewModelScope.launch {
        _photoSort.value = mode
        Prefs.setPhotoSort(context, mode)
        // Kuyruğu yeni sıraya göre yeniden kur (mevcut fotoğraf korunur).
        repo.refreshQueue()
    }

    // O an duvar kağıdında gösterilen fotoğrafın dosya yolu.
    // Duvar kağıdı her değiştiğinde (çift dokunma / zamanlayıcı / scale) güncellenir.
    private val _currentPath = MutableStateFlow(Prefs.currentPath(context))
    val currentPath: StateFlow<String?> = _currentPath.asStateFlow()

    // Mevcut duvar kağıdının en son ne zaman değiştiği (epoch millis).
    private val _lastChangeTime = MutableStateFlow(Prefs.lastChangeTime(context))
    val lastChangeTime: StateFlow<Long> = _lastChangeTime.asStateFlow()

    // O anki playlist kuyruğunun anlık görüntüsü (yollar + mevcut index).
    private val _queue = MutableStateFlow(PlaylistController.snapshot(context))
    val queue: StateFlow<PlaylistController.Snapshot> = _queue.asStateFlow()

    private val changeReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, i: Intent?) {
            _currentPath.value = Prefs.currentPath(context)
            _lastChangeTime.value = Prefs.lastChangeTime(context)
            _queue.value = PlaylistController.snapshot(context)
        }
    }

    init {
        ContextCompat.registerReceiver(
            context,
            changeReceiver,
            IntentFilter(PlaylistController.ACTION_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    override fun onCleared() {
        try {
            context.unregisterReceiver(changeReceiver)
        } catch (_: Exception) {
        }
        super.onCleared()
    }

    fun observeAlbum(id: Long): Flow<Album?> = repo.observeAlbum(id)
    fun observePhotos(albumId: Long): Flow<List<Photo>> = repo.observePhotos(albumId)

    // ---- Albüm işlemleri ----
    fun createAlbum(name: String) = viewModelScope.launch { repo.createAlbum(name) }
    fun addFolder(treeUri: android.net.Uri) = viewModelScope.launch {
        _importing.value = true
        try {
            repo.createAlbumFromFolder(treeUri)
        } finally {
            _importing.value = false
        }
    }
    fun renameAlbum(album: Album, name: String) = viewModelScope.launch { repo.renameAlbum(album, name) }
    fun moveAlbum(album: Album, up: Boolean) = viewModelScope.launch { repo.moveAlbum(album, up) }
    fun deleteAlbum(album: Album) = viewModelScope.launch { repo.deleteAlbum(album) }
    fun setActiveAlbum(album: Album) = viewModelScope.launch { repo.setActiveAlbum(album) }
    fun setAlbumScale(album: Album, scale: String) = viewModelScope.launch { repo.setAlbumScale(album, scale) }

    // ---- Fotoğraf işlemleri ----
    fun addPhotos(albumId: Long, uris: List<Uri>) = viewModelScope.launch { repo.addPhotos(albumId, uris) }
    fun setPhotoScale(photo: Photo, scale: String) = viewModelScope.launch { repo.setPhotoScale(photo, scale) }
    fun deletePhoto(photo: Photo) = viewModelScope.launch { repo.deletePhoto(photo) }
    fun deletePhotos(photos: List<Photo>) = viewModelScope.launch { repo.deletePhotos(photos) }
    fun setAsWallpaper(photo: Photo) = viewModelScope.launch { repo.setAsWallpaper(photo) }
    fun movePhoto(photo: Photo, up: Boolean) = viewModelScope.launch { repo.movePhoto(photo, up) }
    fun jumpToQueue(path: String) = viewModelScope.launch { repo.jumpToQueuePath(path) }
    fun refreshFolder(album: Album) = viewModelScope.launch {
        _importing.value = true
        try {
            repo.refreshFolderAlbum(album)
        } finally {
            _importing.value = false
        }
    }

    // ---- Ayarlar ----
    fun setInterval(minutes: Int) {
        _intervalMinutes.value = minutes
        Prefs.setIntervalMinutes(context, minutes)
        WallpaperScheduler.schedule(context)
    }

    fun setShuffle(value: Boolean) = viewModelScope.launch {
        _shuffle.value = value
        Prefs.setShuffle(context, value)
        repo.refreshQueue()
    }
}

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

    // O an duvar kağıdında gösterilen fotoğrafın dosya yolu.
    // Duvar kağıdı her değiştiğinde (çift dokunma / zamanlayıcı / scale) güncellenir.
    private val _currentPath = MutableStateFlow(Prefs.currentPath(context))
    val currentPath: StateFlow<String?> = _currentPath.asStateFlow()

    private val changeReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, i: Intent?) {
            _currentPath.value = Prefs.currentPath(context)
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
    fun renameAlbum(album: Album, name: String) = viewModelScope.launch { repo.renameAlbum(album, name) }
    fun deleteAlbum(album: Album) = viewModelScope.launch { repo.deleteAlbum(album) }
    fun setActiveAlbum(album: Album) = viewModelScope.launch { repo.setActiveAlbum(album) }
    fun setAlbumScale(album: Album, scale: String) = viewModelScope.launch { repo.setAlbumScale(album, scale) }

    // ---- Fotoğraf işlemleri ----
    fun addPhotos(albumId: Long, uris: List<Uri>) = viewModelScope.launch { repo.addPhotos(albumId, uris) }
    fun setPhotoScale(photo: Photo, scale: String) = viewModelScope.launch { repo.setPhotoScale(photo, scale) }
    fun deletePhoto(photo: Photo) = viewModelScope.launch { repo.deletePhoto(photo) }

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

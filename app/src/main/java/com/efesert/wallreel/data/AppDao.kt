package com.efesert.wallreel.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface AppDao {

    // ---- Albums ----
    @Query("SELECT * FROM albums ORDER BY position ASC, createdAt ASC")
    fun observeAlbums(): Flow<List<Album>>

    @Query("SELECT * FROM albums ORDER BY position ASC, createdAt ASC")
    suspend fun getAlbumsOnce(): List<Album>

    @Query("SELECT * FROM albums WHERE isActive = 1 LIMIT 1")
    suspend fun getActiveAlbum(): Album?

    @Query("SELECT * FROM albums WHERE id = :id LIMIT 1")
    fun observeAlbum(id: Long): Flow<Album?>

    @Insert
    suspend fun insertAlbum(album: Album): Long

    @Update
    suspend fun updateAlbum(album: Album)

    @Delete
    suspend fun deleteAlbum(album: Album)

    @Query("UPDATE albums SET isActive = 0")
    suspend fun clearActiveFlags()

    @Query("UPDATE albums SET isActive = 1 WHERE id = :id")
    suspend fun setActiveFlag(id: Long)

    // ---- Photos ----
    @Query("SELECT * FROM photos WHERE albumId = :albumId ORDER BY addedAt ASC, id ASC")
    fun observePhotos(albumId: Long): Flow<List<Photo>>

    @Query("SELECT * FROM photos WHERE albumId = :albumId ORDER BY addedAt ASC, id ASC")
    suspend fun getPhotos(albumId: Long): List<Photo>

    @Insert
    suspend fun insertPhoto(photo: Photo): Long

    @Update
    suspend fun updatePhoto(photo: Photo)

    @Delete
    suspend fun deletePhoto(photo: Photo)
}

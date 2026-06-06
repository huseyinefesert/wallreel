package com.efesert.wallreel.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Scale (ölçek) modları.
 * - FILL: Resmi ekranı tamamen kaplayacak şekilde büyütür, taşan kısımları kırpar (center-crop).
 * - FIT:  Resmin tamamı görünür, boşluklar siyah kalır (center-inside).
 * - ALBUM: Sadece foto seviyesinde kullanılır -> albümün genel ayarını miras alır (varsayılan).
 */
object ScaleMode {
    const val FILL = "FILL"
    const val FIT = "FIT"
    const val ALBUM = "ALBUM"
}

@Entity(tableName = "albums")
data class Album(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    /** Albümün geneli için varsayılan scale: FILL veya FIT */
    val scaleMode: String = ScaleMode.FILL,
    val isActive: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "photos",
    foreignKeys = [
        ForeignKey(
            entity = Album::class,
            parentColumns = ["id"],
            childColumns = ["albumId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("albumId")]
)
data class Photo(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val albumId: Long,
    /** Uygulamanın iç depolamasına kopyalanmış resmin dosya yolu */
    val path: String,
    /** Foto özel scale: ALBUM (varsayılan, albümü miras al), FILL veya FIT */
    val scaleMode: String = ScaleMode.ALBUM,
    val addedAt: Long = System.currentTimeMillis()
)

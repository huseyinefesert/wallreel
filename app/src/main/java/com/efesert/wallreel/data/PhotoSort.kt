package com.efesert.wallreel.data

import java.io.File

/**
 * Albüm içi fotoğraf sıralama modları. Hem UI'daki ızgara gösterimi hem de
 * (shuffle kapalıyken) playlist kuyruğu bu sıraya göre dizilir; böylece
 * "gördüğün sırayla çalar". CUSTOM modu Photo.position alanını kullanır ve
 * kullanıcı move up/down ile elle sıralar.
 */
object PhotoSort {
    const val CUSTOM = "CUSTOM"
    const val ADDED_OLD = "ADDED_OLD"
    const val ADDED_NEW = "ADDED_NEW"
    const val NAME_AZ = "NAME_AZ"
    const val NAME_ZA = "NAME_ZA"
    const val SIZE_DESC = "SIZE_DESC"
    const val SIZE_ASC = "SIZE_ASC"

    const val DEFAULT = ADDED_OLD

    private fun name(p: Photo): String = (p.displayName ?: File(p.path).name).lowercase()
    private fun size(p: Photo): Long = runCatching { File(p.path).length() }.getOrDefault(0L)

    fun sort(photos: List<Photo>, mode: String): List<Photo> = when (mode) {
        CUSTOM -> photos.sortedBy { it.position }
        ADDED_NEW -> photos.sortedByDescending { it.addedAt }
        NAME_AZ -> photos.sortedBy { name(it) }
        NAME_ZA -> photos.sortedByDescending { name(it) }
        SIZE_DESC -> photos.sortedByDescending { size(it) }
        SIZE_ASC -> photos.sortedBy { size(it) }
        else -> photos.sortedBy { it.addedAt } // ADDED_OLD
    }
}

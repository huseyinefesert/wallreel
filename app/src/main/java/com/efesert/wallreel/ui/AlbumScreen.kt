package com.efesert.wallreel.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.efesert.wallreel.data.Photo
import com.efesert.wallreel.data.ScaleMode
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumScreen(
    viewModel: AppViewModel,
    albumId: Long,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val album by remember(albumId) { viewModel.observeAlbum(albumId) }
        .collectAsState(initial = null)
    val photos by remember(albumId) { viewModel.observePhotos(albumId) }
        .collectAsState(initial = emptyList())

    var photoForScale by remember { mutableStateOf<Photo?>(null) }

    // ---- Çoklu seçim modu ----
    var selectionMode by remember { mutableStateOf(false) }
    val selectedIds = remember { mutableStateListOf<Long>() }
    var showRemoveConfirm by remember { mutableStateOf(false) }

    fun exitSelection() {
        selectionMode = false
        selectedIds.clear()
    }

    fun toggleSelection(id: Long) {
        if (selectedIds.contains(id)) selectedIds.remove(id) else selectedIds.add(id)
        if (selectedIds.isEmpty()) selectionMode = false
    }

    // Seçim modundayken sistem geri tuşu önce seçimi kapatsın.
    BackHandler(enabled = selectionMode) { exitSelection() }

    // O an duvar kağıdında gösterilen fotoğraf (bu albüme aitse).
    val currentPath by viewModel.currentPath.collectAsState()
    val lastChangeTime by viewModel.lastChangeTime.collectAsState()
    val currentPhoto = remember(photos, currentPath) {
        currentPath?.let { p -> photos.firstOrNull { it.path == p } }
    }
    val gridState = rememberLazyGridState()
    val scope = rememberCoroutineScope()

    // Fotoğraf sıralama (sadece görüntüleme; playlist sırasını değiştirmez).
    val sortMode by viewModel.photoSort.collectAsState()
    var sortMenuOpen by remember { mutableStateOf(false) }
    val displayPhotos = remember(photos, sortMode) { sortPhotos(photos, sortMode) }

    // "X dakika önce" yazısının zamanla ilerlemesi için her 30 sn'de bir "şimdi"yi tazele.
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            kotlinx.coroutines.delay(30_000)
        }
    }

    fun scrollToCurrent() {
        val index = displayPhotos.indexOfFirst { it.path == currentPath }
        if (index >= 0) scope.launch { gridState.animateScrollToItem(index) }
    }

    // OpenMultipleDocuments her cihazda güvenilir biçimde çoklu seçime izin verir.
    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) viewModel.addPhotos(albumId, uris)
    }

    Scaffold(
        topBar = {
            if (selectionMode) {
                TopAppBar(
                    title = { Text("${selectedIds.size} selected") },
                    navigationIcon = {
                        IconButton(onClick = { exitSelection() }) {
                            Icon(Icons.Filled.Close, contentDescription = "Cancel")
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = { if (selectedIds.isNotEmpty()) showRemoveConfirm = true }
                        ) {
                            Icon(Icons.Filled.Delete, contentDescription = "Remove from album")
                        }
                    }
                )
            } else {
                TopAppBar(
                    title = { Text(album?.name ?: "Album") },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        Box {
                            IconButton(onClick = { sortMenuOpen = true }) {
                                Icon(Icons.Filled.SwapVert, contentDescription = "Sort")
                            }
                            DropdownMenu(
                                expanded = sortMenuOpen,
                                onDismissRequest = { sortMenuOpen = false }
                            ) {
                                sortOptions.forEach { (mode, label) ->
                                    DropdownMenuItem(
                                        text = { Text(label) },
                                        trailingIcon = {
                                            if (sortMode == mode) {
                                                Icon(Icons.Filled.Check, contentDescription = null)
                                            }
                                        },
                                        onClick = {
                                            viewModel.setPhotoSort(mode)
                                            sortMenuOpen = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                )
            }
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            val current = album
            if (current != null && !selectionMode) {
                Spacer(Modifier.height(8.dp))
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Album default scale", fontWeight = FontWeight.Bold)
                        Text(
                            "Default scale for photos",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(8.dp))
                        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                            SegmentedButton(
                                selected = current.scaleMode == ScaleMode.FILL,
                                onClick = { viewModel.setAlbumScale(current, ScaleMode.FILL) },
                                shape = SegmentedButtonDefaults.itemShape(0, 2)
                            ) { Text("Fill") }
                            SegmentedButton(
                                selected = current.scaleMode == ScaleMode.FIT,
                                onClick = { viewModel.setAlbumScale(current, ScaleMode.FIT) },
                                shape = SegmentedButtonDefaults.itemShape(1, 2)
                            ) { Text("Fit") }
                        }

                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { picker.launch(arrayOf("image/*")) },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Filled.AddPhotoAlternate, contentDescription = null)
                                Spacer(Modifier.height(0.dp))
                                Text("  Add photos")
                            }
                            if (!current.isActive) {
                                OutlinedButton(
                                    onClick = { viewModel.setActiveAlbum(current) },
                                    modifier = Modifier.weight(1f)
                                ) { Text("Set active") }
                            } else {
                                OutlinedButton(
                                    onClick = {},
                                    enabled = false,
                                    modifier = Modifier.weight(1f)
                                ) { Text("Active ✓") }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
            }

            // ---- Şu an duvar kağıdında gösterilen fotoğraf ----
            val cur = currentPhoto
            if (cur != null && !selectionMode) {
                val albumScale = current?.scaleMode ?: ScaleMode.FILL
                val effective = if (cur.scaleMode == ScaleMode.ALBUM) albumScale else cur.scaleMode
                val effLabel = (if (effective == ScaleMode.FIT) "Fit" else "Fill") +
                    if (cur.scaleMode == ScaleMode.ALBUM) " (album)" else " (custom)"
                Card(
                    Modifier.fillMaxWidth(),
                    colors = androidx.compose.material3.CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            AsyncImage(
                                model = File(cur.path),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(RoundedCornerShape(8.dp))
                            )
                            Spacer(Modifier.size(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text("Currently on wallpaper", fontWeight = FontWeight.Bold)
                                Text(
                                    "Scale: $effLabel",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        if (lastChangeTime > 0L) {
                            Spacer(Modifier.height(8.dp))
                            Box(
                                Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.primary)
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    "Changed ${formatElapsed(now - lastChangeTime)}",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { photoForScale = cur },
                                modifier = Modifier.weight(1f)
                            ) { Text("Change scale") }
                            OutlinedButton(
                                onClick = { scrollToCurrent() },
                                modifier = Modifier.weight(1f)
                            ) { Text("Show in list") }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
            }

            if (photos.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "No photos in this album.\nAdd some from above.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyVerticalGrid(
                    state = gridState,
                    columns = GridCells.Fixed(3),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(displayPhotos, key = { it.id }) { photo ->
                        PhotoCell(
                            photo = photo,
                            isCurrent = photo.path == currentPath,
                            selectionMode = selectionMode,
                            selected = selectedIds.contains(photo.id),
                            onClick = {
                                if (selectionMode) toggleSelection(photo.id)
                                else photoForScale = photo
                            },
                            onLongClick = {
                                if (!selectionMode) selectionMode = true
                                toggleSelection(photo.id)
                            }
                        )
                    }
                }
            }
        }
    }

    val target = photoForScale
    if (target != null) {
        PhotoScaleDialog(
            photo = target,
            onDismiss = { photoForScale = null },
            onPick = { mode ->
                viewModel.setPhotoScale(target, mode)
                photoForScale = null
            },
            onSetWallpaper = {
                viewModel.setAsWallpaper(target)
                photoForScale = null
            },
            onDelete = {
                viewModel.deletePhoto(target)
                photoForScale = null
            }
        )
    }

    if (showRemoveConfirm) {
        val count = selectedIds.size
        AlertDialog(
            onDismissRequest = { showRemoveConfirm = false },
            title = { Text("Remove photos") },
            text = { Text("Remove $count photo(s) from this album? This deletes the copies stored by the app.") },
            confirmButton = {
                TextButton(onClick = {
                    val toRemove = photos.filter { selectedIds.contains(it.id) }
                    viewModel.deletePhotos(toRemove)
                    showRemoveConfirm = false
                    exitSelection()
                }) {
                    Text("Remove", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showRemoveConfirm = false }) { Text("Cancel") }
            }
        )
    }
}

private val sortOptions = listOf(
    "ADDED_OLD" to "Date added (oldest)",
    "ADDED_NEW" to "Date added (newest)",
    "NAME_AZ" to "Name (A–Z)",
    "NAME_ZA" to "Name (Z–A)",
    "SIZE_DESC" to "Size (largest)",
    "SIZE_ASC" to "Size (smallest)"
)

private fun photoDisplayName(p: Photo): String = p.displayName ?: File(p.path).name

private fun sortPhotos(photos: List<Photo>, mode: String): List<Photo> = when (mode) {
    "ADDED_NEW" -> photos.sortedByDescending { it.addedAt }
    "NAME_AZ" -> photos.sortedBy { photoDisplayName(it).lowercase() }
    "NAME_ZA" -> photos.sortedByDescending { photoDisplayName(it).lowercase() }
    "SIZE_DESC" -> photos.sortedByDescending { runCatching { File(it.path).length() }.getOrDefault(0L) }
    "SIZE_ASC" -> photos.sortedBy { runCatching { File(it.path).length() }.getOrDefault(0L) }
    else -> photos.sortedBy { it.addedAt } // ADDED_OLD
}

private fun formatFileSize(bytes: Long): String {
    if (bytes <= 0) return "—"
    val kb = bytes / 1024.0
    return if (kb < 1024) "%.0f KB".format(kb) else "%.1f MB".format(kb / 1024.0)
}

private fun formatPhotoDate(millis: Long): String {
    if (millis <= 0) return "—"
    return java.text.SimpleDateFormat("d MMM yyyy, HH:mm", java.util.Locale.getDefault())
        .format(java.util.Date(millis))
}

/** "5 min ago", "1 h 20 min ago", "2 d 3 h ago" gibi okunaklı geçen süre metni. */
private fun formatElapsed(millis: Long): String {
    if (millis < 0) return "just now"
    val totalMinutes = millis / 60_000L
    return when {
        totalMinutes < 1 -> "just now"
        totalMinutes < 60 -> "$totalMinutes min ago"
        totalMinutes < 1440 -> {
            val h = totalMinutes / 60
            val m = totalMinutes % 60
            if (m == 0L) "$h h ago" else "$h h $m min ago"
        }
        else -> {
            val d = totalMinutes / 1440
            val h = (totalMinutes % 1440) / 60
            if (h == 0L) "$d d ago" else "$d d $h h ago"
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PhotoCell(
    photo: Photo,
    isCurrent: Boolean,
    selectionMode: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val borderColor = when {
        selected -> MaterialTheme.colorScheme.primary
        isCurrent -> MaterialTheme.colorScheme.primary
        else -> null
    }
    val borderMod = if (borderColor != null) {
        Modifier.border(3.dp, borderColor, RoundedCornerShape(8.dp))
    } else Modifier
    Box(
        Modifier
            .aspectRatio(1f)
            .then(borderMod)
            .clip(RoundedCornerShape(8.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
    ) {
        AsyncImage(
            model = File(photo.path),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        // Seçim modunda: seçili fotoğrafın üstüne yarı saydam katman + onay işareti.
        if (selectionMode) {
            if (selected) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.35f))
                )
            }
            Icon(
                imageVector = Icons.Filled.CheckCircle,
                contentDescription = null,
                tint = if (selected) MaterialTheme.colorScheme.primary
                       else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .size(22.dp)
                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(50))
            )
        }
        // O an gösterilen fotoğraf -> sol üstte "Şu an" rozeti.
        if (isCurrent && !selectionMode) {
            Box(
                Modifier
                    .align(Alignment.TopStart)
                    .padding(4.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(MaterialTheme.colorScheme.primary)
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    "Now",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            }
        }
        // Foto özel scale ayarı varsa küçük rozet göster.
        if (photo.scaleMode != ScaleMode.ALBUM) {
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(4.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(MaterialTheme.colorScheme.primary)
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    if (photo.scaleMode == ScaleMode.FIT) "Fit" else "Fill",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            }
        }
    }
}

@Composable
private fun PhotoScaleDialog(
    photo: Photo,
    onDismiss: () -> Unit,
    onPick: (String) -> Unit,
    onSetWallpaper: () -> Unit,
    onDelete: () -> Unit
) {
    val options = listOf(
        ScaleMode.ALBUM to "Album setting (default)",
        ScaleMode.FILL to "Fill (cover, crop)",
        ScaleMode.FIT to "Fit (show whole image)"
    )
    val fileSize = remember(photo.path) {
        runCatching { File(photo.path).length() }.getOrDefault(0L)
    }
    val dateMillis = if (photo.sourceDate > 0) photo.sourceDate else photo.addedAt
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Photo") },
        text = {
            Column {
                // ---- Açıklama: ad, boyut, tarih ----
                Text(
                    photoDisplayName(photo),
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    "Size: ${formatFileSize(fileSize)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "Date: ${formatPhotoDate(dateMillis)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(10.dp))
                HorizontalDivider()
                Spacer(Modifier.height(6.dp))
                Text(
                    "Scale",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )

                options.forEach { (mode, label) ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onPick(mode) }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = photo.scaleMode == mode,
                            onClick = { onPick(mode) }
                        )
                        Text(label)
                    }
                }
                Spacer(Modifier.height(12.dp))
                // Seçilen fotoğrafı hemen duvar kağıdı yap (timer sıfırlanır).
                Button(
                    onClick = onSetWallpaper,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Filled.Wallpaper, contentDescription = null)
                    Text("  Set as wallpaper now")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDelete) {
                Text("Delete photo", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}

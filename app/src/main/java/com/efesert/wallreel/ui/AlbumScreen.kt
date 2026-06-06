package com.efesert.wallreel.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
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

    // O an duvar kağıdında gösterilen fotoğraf (bu albüme aitse).
    val currentPath by viewModel.currentPath.collectAsState()
    val lastChangeTime by viewModel.lastChangeTime.collectAsState()
    val currentPhoto = remember(photos, currentPath) {
        currentPath?.let { p -> photos.firstOrNull { it.path == p } }
    }
    val gridState = rememberLazyGridState()
    val scope = rememberCoroutineScope()

    // "X dakika önce" yazısının zamanla ilerlemesi için her 30 sn'de bir "şimdi"yi tazele.
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            kotlinx.coroutines.delay(30_000)
        }
    }

    fun scrollToCurrent() {
        val index = photos.indexOfFirst { it.path == currentPath }
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
            TopAppBar(
                title = { Text(album?.name ?: "Album") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            val current = album
            if (current != null) {
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
            if (cur != null) {
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
                    items(photos, key = { it.id }) { photo ->
                        PhotoCell(
                            photo = photo,
                            isCurrent = photo.path == currentPath,
                            onClick = { photoForScale = photo }
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
            onDelete = {
                viewModel.deletePhoto(target)
                photoForScale = null
            }
        )
    }
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

@Composable
private fun PhotoCell(photo: Photo, isCurrent: Boolean, onClick: () -> Unit) {
    val borderMod = if (isCurrent) {
        Modifier.border(3.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp))
    } else Modifier
    Box(
        Modifier
            .aspectRatio(1f)
            .then(borderMod)
            .clip(RoundedCornerShape(8.dp))
            .clickable { onClick() }
    ) {
        AsyncImage(
            model = File(photo.path),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        // O an gösterilen fotoğraf -> sol üstte "Şu an" rozeti.
        if (isCurrent) {
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
    onDelete: () -> Unit
) {
    val options = listOf(
        ScaleMode.ALBUM to "Album setting (default)",
        ScaleMode.FILL to "Fill (cover, crop)",
        ScaleMode.FIT to "Fit (show whole image)"
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Photo scale") },
        text = {
            Column {
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

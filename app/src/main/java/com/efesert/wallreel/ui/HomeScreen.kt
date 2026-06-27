package com.efesert.wallreel.ui

import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Intent
import android.widget.Toast
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.efesert.wallreel.data.Album
import com.efesert.wallreel.playlist.PlaylistController
import com.efesert.wallreel.service.PlaylistWallpaperService
import java.io.File

private data class IntervalOption(val label: String, val minutes: Int)

private val intervalOptions = listOf(
    IntervalOption("15 min", 15),
    IntervalOption("30 min", 30),
    IntervalOption("1 hour", 60),
    IntervalOption("3 hours", 180),
    IntervalOption("6 hours", 360),
    IntervalOption("12 hours", 720),
    IntervalOption("1 day", 1440),
)

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(
    viewModel: AppViewModel,
    onOpenAlbum: (Long) -> Unit
) {
    val context = LocalContext.current
    val albums by viewModel.albums.collectAsState()
    val interval by viewModel.intervalMinutes.collectAsState()
    val timerPaused by viewModel.timerPaused.collectAsState()
    val timerRemainingMillis by viewModel.timerRemainingMillis.collectAsState()
    val shuffle by viewModel.shuffle.collectAsState()
    val doubleTap by viewModel.doubleTapEnabled.collectAsState()
    val queue by viewModel.queue.collectAsState()

    var showCreate by remember { mutableStateOf(false) }
    var showCustomInterval by remember { mutableStateOf(false) }
    var showAddMenu by remember { mutableStateOf(false) }
    var albumToRename by remember { mutableStateOf<Album?>(null) }
    val importing by viewModel.importing.collectAsState()

    // Telefondan klasör seçtirir; seçilen klasör albüm olarak içe aktarılır.
    val folderPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) viewModel.addFolder(uri)
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("WallReel") }) },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddMenu = true }) {
                Icon(Icons.Filled.Add, contentDescription = "Add")
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ---- Ayarlar kartı ----
            item {
                Spacer(Modifier.height(8.dp))
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Timer", fontWeight = FontWeight.Bold)
                        Text(
                            "Changes automatically after this interval",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(8.dp))
                        androidx.compose.foundation.layout.FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            intervalOptions.forEach { opt ->
                                FilterChip(
                                    selected = interval == opt.minutes,
                                    onClick = { viewModel.setInterval(opt.minutes) },
                                    label = { Text(opt.label) }
                                )
                            }
                            FilterChip(
                                selected = intervalOptions.none { it.minutes == interval },
                                onClick = { showCustomInterval = true },
                                label = {
                                    val custom = intervalOptions.none { it.minutes == interval }
                                    Text(if (custom) "Custom: $interval min" else "Custom")
                                }
                            )
                        }

                        Spacer(Modifier.height(12.dp))
                        Button(
                            onClick = {
                                if (timerPaused) viewModel.resumeTimer() else viewModel.pauseTimer()
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                if (timerPaused) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                                contentDescription = null
                            )
                            Text(if (timerPaused) "  Continue timer" else "  Pause timer")
                        }
                        if (timerPaused) {
                            Text(
                                "Paused with ${formatDuration(timerRemainingMillis)} left",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }

                        Spacer(Modifier.height(16.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("Shuffle", fontWeight = FontWeight.Bold)
                                Text(
                                    "Plays in random order",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(checked = shuffle, onCheckedChange = { viewModel.setShuffle(it) })
                        }

                        Spacer(Modifier.height(16.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("Double-tap to change", fontWeight = FontWeight.Bold)
                                Text(
                                    "Double-tap the home screen to jump to the next photo",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = doubleTap,
                                onCheckedChange = { viewModel.setDoubleTapEnabled(it) }
                            )
                        }

                        Spacer(Modifier.height(16.dp))
                        TextButton(
                            onClick = { launchLiveWallpaper(context) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Set as live wallpaper")
                        }
                        Text(
                            "Tip: After setting the wallpaper, double-tap on the home " +
                                "screen to jump to the next one immediately.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // ---- Sıradakiler (queue önizleme) ----
            if (queue.paths.isNotEmpty() && queue.currentIndex >= 0) {
                item {
                    QueueStrip(
                        queue = queue,
                        onJump = { path -> viewModel.jumpToQueue(path) }
                    )
                }
            }

            item {
                Text(
                    "Albums",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium
                )
            }

            if (albums.isEmpty()) {
                item {
                    Text(
                        "No albums yet. Add one with the + button at the bottom right.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            itemsIndexed(albums, key = { _, a -> a.id }) { index, album ->
                AlbumRow(
                    album = album,
                    canMoveUp = index > 0,
                    canMoveDown = index < albums.lastIndex,
                    onOpen = { onOpenAlbum(album.id) },
                    onSetActive = { viewModel.setActiveAlbum(album) },
                    onRename = { albumToRename = album },
                    onMoveUp = { viewModel.moveAlbum(album, up = true) },
                    onMoveDown = { viewModel.moveAlbum(album, up = false) },
                    onRefreshFolder = { viewModel.refreshFolder(album) },
                    onDelete = { viewModel.deleteAlbum(album) }
                )
            }

            item { Spacer(Modifier.height(80.dp)) }
        }
    }

    // ---- FAB menüsü: Albüm oluştur / Klasör ekle ----
    if (showAddMenu) {
        ModalBottomSheet(onDismissRequest = { showAddMenu = false }) {
            Column(Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
                ListItem(
                    headlineContent = { Text("Create album") },
                    supportingContent = { Text("Empty album, add photos manually") },
                    leadingContent = { Icon(Icons.Filled.PhotoLibrary, contentDescription = null) },
                    modifier = Modifier.clickable {
                        showAddMenu = false
                        showCreate = true
                    }
                )
                ListItem(
                    headlineContent = { Text("Add folder") },
                    supportingContent = { Text("Import all photos from a device folder") },
                    leadingContent = { Icon(Icons.Filled.Folder, contentDescription = null) },
                    modifier = Modifier.clickable {
                        showAddMenu = false
                        folderPicker.launch(null)
                    }
                )
            }
        }
    }

    // ---- Klasör içe aktarılıyor göstergesi ----
    if (importing) {
        AlertDialog(
            onDismissRequest = { },
            confirmButton = { },
            title = { Text("Importing folder…") },
            text = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    Spacer(Modifier.size(16.dp))
                    Text("Copying photos, please wait…")
                }
            }
        )
    }

    val renaming = albumToRename
    if (renaming != null) {
        var newName by remember(renaming.id) { mutableStateOf(renaming.name) }
        AlertDialog(
            onDismissRequest = { albumToRename = null },
            title = { Text("Rename album") },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text("Album name") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.renameAlbum(renaming, newName)
                    albumToRename = null
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { albumToRename = null }) { Text("Cancel") }
            }
        )
    }

    if (showCreate) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCreate = false },
            title = { Text("New album") },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Album name") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.createAlbum(name.ifBlank { "Album" })
                    showCreate = false
                }) { Text("Create") }
            },
            dismissButton = {
                TextButton(onClick = { showCreate = false }) { Text("Cancel") }
            }
        )
    }

    if (showCustomInterval) {
        var minutesText by remember { mutableStateOf(interval.toString()) }
        AlertDialog(
            onDismissRequest = { showCustomInterval = false },
            title = { Text("Custom interval (minutes)") },
            text = {
                OutlinedTextField(
                    value = minutesText,
                    onValueChange = { v -> minutesText = v.filter { it.isDigit() }.take(5) },
                    label = { Text("Minutes") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val m = minutesText.toIntOrNull()?.coerceAtLeast(1) ?: 60
                    viewModel.setInterval(m)
                    showCustomInterval = false
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showCustomInterval = false }) { Text("Cancel") }
            }
        )
    }
}

private fun formatDuration(millis: Long): String {
    val totalMinutes = (millis / 60_000L).coerceAtLeast(0L)
    return when {
        totalMinutes < 1 -> "less than 1 min"
        totalMinutes < 60 -> "$totalMinutes min"
        totalMinutes < 1440 -> {
            val h = totalMinutes / 60
            val m = totalMinutes % 60
            if (m == 0L) "$h h" else "$h h $m min"
        }
        else -> {
            val d = totalMinutes / 1440
            val h = (totalMinutes % 1440) / 60
            if (h == 0L) "$d d" else "$d d $h h"
        }
    }
}

/**
 * O anki kuyruğu yatay olarak gösterir: ~2 geri + ~15 ileri. Mevcut fotoğraf
 * vurgulanır; bir karoya dokununca o fotoğrafa atlanır.
 */
@Composable
private fun QueueStrip(
    queue: PlaylistController.Snapshot,
    onJump: (String) -> Unit
) {
    val start = (queue.currentIndex - 2).coerceAtLeast(0)
    val end = (queue.currentIndex + 15).coerceAtMost(queue.paths.lastIndex)
    if (end < start) return
    val window = queue.paths.subList(start, end + 1)
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("Up next", fontWeight = FontWeight.Bold)
            Text(
                "Tap a photo to jump to it",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(10.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                itemsIndexed(window, key = { i, _ -> start + i }) { i, path ->
                    val absolute = start + i
                    val isCurrent = absolute == queue.currentIndex
                    val borderMod = if (isCurrent) {
                        Modifier.border(3.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(10.dp))
                    } else Modifier
                    Box(
                        Modifier
                            .size(64.dp)
                            .then(borderMod)
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { onJump(path) }
                    ) {
                        AsyncImage(
                            model = File(path),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                        if (isCurrent) {
                            Box(
                                Modifier
                                    .align(Alignment.TopStart)
                                    .padding(3.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(MaterialTheme.colorScheme.primary)
                                    .padding(horizontal = 5.dp, vertical = 1.dp)
                            ) {
                                Text(
                                    "Now",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AlbumRow(
    album: Album,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onOpen: () -> Unit,
    onSetActive: () -> Unit,
    onRename: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onRefreshFolder: () -> Unit,
    onDelete: () -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpen() },
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(album.name, fontWeight = FontWeight.Bold)
                    if (album.isActive) {
                        Icon(
                            Icons.Filled.CheckCircle,
                            contentDescription = "Active",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                }
                Text(
                    "Default scale: " + if (album.scaleMode == "FIT") "Fit" else "Fill",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (!album.isActive) {
                AssistChip(onClick = onSetActive, label = { Text("Set active") })
            }
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "More options")
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text("Rename") },
                        leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null) },
                        onClick = { menuOpen = false; onRename() }
                    )
                    DropdownMenuItem(
                        text = { Text("Move up") },
                        enabled = canMoveUp,
                        leadingIcon = { Icon(Icons.Filled.ArrowUpward, contentDescription = null) },
                        onClick = { menuOpen = false; onMoveUp() }
                    )
                    DropdownMenuItem(
                        text = { Text("Move down") },
                        enabled = canMoveDown,
                        leadingIcon = { Icon(Icons.Filled.ArrowDownward, contentDescription = null) },
                        onClick = { menuOpen = false; onMoveDown() }
                    )
                    // Yalnızca klasörden oluşturulmuş albümlerde: klasörü yeniden tara.
                    if (album.folderUri != null) {
                        DropdownMenuItem(
                            text = { Text("Refresh folder") },
                            leadingIcon = { Icon(Icons.Filled.Refresh, contentDescription = null) },
                            onClick = { menuOpen = false; onRefreshFolder() }
                        )
                    }
                    DropdownMenuItem(
                        text = { Text("Delete") },
                        leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null) },
                        onClick = { menuOpen = false; onDelete() }
                    )
                }
            }
        }
    }
}

/** Sistemin canlı duvar kağıdı seçiciyi bizim servisimizle açar. */
private fun launchLiveWallpaper(context: android.content.Context) {
    try {
        val intent = Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER).apply {
            putExtra(
                WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
                ComponentName(context, PlaylistWallpaperService::class.java)
            )
        }
        context.startActivity(intent)
    } catch (e: Exception) {
        // Bazı cihazlarda doğrudan önizleme desteklenmez; genel seçiciyi aç.
        try {
            context.startActivity(Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER))
        } catch (e2: Exception) {
            Toast.makeText(context, "Couldn't open the live wallpaper picker", Toast.LENGTH_LONG).show()
        }
    }
}

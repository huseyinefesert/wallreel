package com.efesert.wallreel.ui

import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.efesert.wallreel.data.Album
import com.efesert.wallreel.service.PlaylistWallpaperService

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
    val shuffle by viewModel.shuffle.collectAsState()

    var showCreate by remember { mutableStateOf(false) }
    var showCustomInterval by remember { mutableStateOf(false) }
    var showAddMenu by remember { mutableStateOf(false) }
    val importing by viewModel.importing.collectAsState()

    // Telefondan klasör seçtirir; seçilen klasör albüm olarak içe aktarılır.
    val folderPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) viewModel.addFolder(uri)
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Wallreel") }) },
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

            items(albums, key = { it.id }) { album ->
                AlbumRow(
                    album = album,
                    onOpen = { onOpenAlbum(album.id) },
                    onSetActive = { viewModel.setActiveAlbum(album) },
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

@Composable
private fun AlbumRow(
    album: Album,
    onOpen: () -> Unit,
    onSetActive: () -> Unit,
    onDelete: () -> Unit
) {
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
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(album.name, fontWeight = FontWeight.Bold)
                    if (album.isActive) {
                        Spacer(Modifier.height(0.dp))
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
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = "Delete")
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

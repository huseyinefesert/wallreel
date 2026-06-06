package com.efesert.wallreel.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.NavType
import com.efesert.wallreel.scheduler.WallpaperScheduler

class MainActivity : ComponentActivity() {

    private val viewModel: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Uygulama her açıldığında zamanlayıcıyı tazele.
        WallpaperScheduler.schedule(this)
        setContent {
            AppTheme {
                AppNav(viewModel)
            }
        }
    }
}

@Composable
private fun AppNav(viewModel: AppViewModel) {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = "home") {
        composable("home") {
            HomeScreen(
                viewModel = viewModel,
                onOpenAlbum = { albumId -> nav.navigate("album/$albumId") }
            )
        }
        composable(
            route = "album/{albumId}",
            arguments = listOf(navArgument("albumId") { type = NavType.LongType })
        ) { entry ->
            val albumId = entry.arguments?.getLong("albumId") ?: return@composable
            AlbumScreen(
                viewModel = viewModel,
                albumId = albumId,
                onBack = { nav.popBackStack() }
            )
        }
    }
}

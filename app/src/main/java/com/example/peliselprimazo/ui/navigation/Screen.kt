package com.example.peliselprimazo.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Tv
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(val route: String, val title: String = "", val icon: ImageVector? = null) {
    object Home : Screen("home", "Inicio", Icons.Default.Home)
    object Movies : Screen("movies", "Películas", Icons.Default.Movie)
    object Series : Screen("series", "Series", Icons.Default.Tv)
    object Anime : Screen("anime", "Anime", Icons.Default.PlayArrow)
    object Profile : Screen("profile", "Perfil", Icons.Default.Person)
    
    object Auth : Screen("auth") {
        const val ARG_REDIRECT_TO = "redirectTo"
        fun createRoute(redirectTo: String? = null) = if (redirectTo != null) "auth?redirectTo=$redirectTo" else "auth"
    }

    object Detail : Screen("detail/{movieId}") {
        fun createRoute(movieId: Int) = "detail/$movieId"
    }
    
    object Player : Screen("player/{serverName}/{fileId}/{movieId}?videoUrl={videoUrl}&adUrl={adUrl}") {
        fun createRoute(serverName: String, fileId: String, movieId: Int, videoUrl: String? = null, adUrl: String? = null) = 
            "player/$serverName/$fileId/$movieId" + 
            (if (videoUrl != null) "?videoUrl=$videoUrl" else "?videoUrl=") +
            (if (adUrl != null) "&adUrl=$adUrl" else "")
    }
}

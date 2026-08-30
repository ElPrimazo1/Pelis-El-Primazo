package com.example.peliselprimazo

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.EaseInOutQuart
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.peliselprimazo.ui.navigation.Screen
import com.example.peliselprimazo.ui.screens.detail.DetailScreen
import com.example.peliselprimazo.ui.screens.detail.DetailViewModel
import com.example.peliselprimazo.ui.screens.home.HomeScreen
import com.example.peliselprimazo.ui.screens.home.HomeViewModel
import com.example.peliselprimazo.ui.screens.player.PlayerScreen
import com.example.peliselprimazo.ui.screens.player.PlayerViewModel
import com.example.peliselprimazo.ui.screens.splash.SplashScreen
import com.example.peliselprimazo.ui.theme.PelisElPrimazoTheme
import dagger.hilt.android.AndroidEntryPoint
import java.net.URLEncoder

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private var currentIntent by mutableStateOf<Intent?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        
        currentIntent = intent
        enableEdgeToEdge()
        
        setContent {
            PelisElPrimazoTheme {
                val homeViewModel: HomeViewModel = hiltViewModel()
                val isLoading by homeViewModel.isLoading.collectAsState()
                var hasFinishedIntro by rememberSaveable { mutableStateOf(false) }
                
                val showContent = hasFinishedIntro && !isLoading

                RequestNotificationPermission()

                Crossfade(
                    targetState = showContent,
                    animationSpec = tween(durationMillis = 800),
                    label = "GlobalTransition"
                ) { ready ->
                    if (!ready) {
                        SplashScreen(onAnimationFinished = { hasFinishedIntro = true })
                    } else {
                        MainContent(
                            homeViewModel = homeViewModel, 
                            intent = currentIntent
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        currentIntent = intent
    }
}

@Composable
fun RequestNotificationPermission() {
    val context = LocalContext.current
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val launcher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { }
        SideEffect {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) 
                != PackageManager.PERMISSION_GRANTED) {
                launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}

@Composable
fun MainContent(
    homeViewModel: HomeViewModel, 
    intent: Intent?
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: ""
    val isPlayerVisible = currentRoute.startsWith("player")

    LaunchedEffect(intent) {
        intent?.let {
            val movieId = it.getIntExtra("MOVIE_ID", -1)
            if (movieId != -1) {
                navController.navigate(Screen.Detail.createRoute(movieId)) {
                    launchSingleTop = true
                }
            }
        }
    }

    Scaffold(containerColor = Color.Black) { innerPadding ->
        val finalPadding = if (isPlayerVisible) PaddingValues(0.dp) else innerPadding
        
        NavHost(
            navController = navController,
            startDestination = Screen.Home.route,
            modifier = Modifier.padding(finalPadding),
            enterTransition = { slideInHorizontally(initialOffsetX = { it }, animationSpec = tween(400, easing = EaseInOutQuart)) + fadeIn(animationSpec = tween(400)) },
            exitTransition = { slideOutHorizontally(targetOffsetX = { -it / 3 }, animationSpec = tween(400, easing = EaseInOutQuart)) + fadeOut(animationSpec = tween(400)) },
            popEnterTransition = { slideInHorizontally(initialOffsetX = { -it / 3 }, animationSpec = tween(400, easing = EaseInOutQuart)) + fadeIn(animationSpec = tween(400)) },
            popExitTransition = { slideOutHorizontally(targetOffsetX = { it }, animationSpec = tween(400, easing = EaseInOutQuart)) + fadeOut(animationSpec = tween(400)) }
        ) {
            composable(Screen.Home.route) {
                HomeScreen(
                    viewModel = homeViewModel,
                    onMovieClick = { movieId -> navController.navigate(Screen.Detail.createRoute(movieId)) },
                    onDirectPlayClick = { movie, url ->
                        movie.serverLinks.firstOrNull()?.let { link ->
                            navController.navigateToPlayer(link.serverName, link.fileId, movie.id, url)
                        }
                    }
                )
            }

            composable(
                route = Screen.Detail.route,
                arguments = listOf(navArgument("movieId") { type = NavType.IntType })
            ) { backStackEntry ->
                val movieId = backStackEntry.arguments?.getInt("movieId") ?: 0
                val detailViewModel: DetailViewModel = hiltViewModel()
                DetailScreen(
                    viewModel = detailViewModel,
                    onBack = { navController.popBackStack() },
                    onPlay = { server, fileId, adUrl -> 
                        detailViewModel.prepareAndPlay(server, fileId, adUrl) { videoUrl, finalAdUrl ->
                            navController.navigateToPlayer(server, fileId, movieId, videoUrl, finalAdUrl)
                        }
                    }
                )
            }

            composable(
                route = Screen.Player.route,
                arguments = listOf(
                    navArgument("serverName") { type = NavType.StringType },
                    navArgument("fileId") { type = NavType.StringType },
                    navArgument("movieId") { type = NavType.IntType },
                    navArgument("videoUrl") { 
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                    navArgument("adUrl") { 
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    }
                )
            ) { 
                val playerViewModel: PlayerViewModel = hiltViewModel()
                PlayerScreen(
                    viewModel = playerViewModel,
                    onBack = { navController.popBackStack() }
                )
            }
        }
    }
}

private fun NavController.navigateToPlayer(server: String, fileId: String, movieId: Int, videoUrl: String? = null, adUrl: String? = null) {
    try {
        val encodedId = URLEncoder.encode(fileId, "UTF-8")
        val encodedVideoUrl = videoUrl?.let { URLEncoder.encode(it, "UTF-8") }
        val encodedAdUrl = adUrl?.let { URLEncoder.encode(it, "UTF-8") }
        navigate(Screen.Player.createRoute(server, encodedId, movieId, encodedVideoUrl, encodedAdUrl))
    } catch (e: Exception) {
        Log.e("Navigation", "Error encoding parameters", e)
    }
}

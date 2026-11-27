package app.santals.speaks.presentation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.hilt.navigation.compose.hiltViewModel
import app.santals.speaks.presentation.about.AboutScreen
import app.santals.speaks.presentation.history.HistoryScreen
import app.santals.speaks.presentation.onboarding.OnboardingScreen
import app.santals.speaks.presentation.reading.FloatingPlayerBar
import app.santals.speaks.presentation.reading.HomeScreen
import app.santals.speaks.presentation.reading.PlayerScreen
import app.santals.speaks.presentation.reading.ReadingViewModel
import app.santals.speaks.presentation.reading.TextInputScreen
import app.santals.speaks.presentation.settings.SettingsScreen

object Routes {
    const val ONBOARDING = "onboarding"
    const val HOME = "home"
    const val INPUT = "input"
    const val PLAYER = "player"
    const val HISTORY = "history"
    const val SETTINGS = "settings"
    const val ABOUT = "about"
}

/**
 * Top-level navigation. The floating player is shown on every screen except the full player and
 * onboarding, whenever a reading is in progress.
 */
@Composable
fun SantaliRoot(
    startOnboarding: Boolean,
    root: RootViewModel = hiltViewModel(),
    reading: ReadingViewModel = hiltViewModel(),
) {
    val nav = rememberNavController()
    val state by reading.state.collectAsStateWithLifecycle()
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route

    val showFloating = route != Routes.PLAYER && route != Routes.ONBOARDING &&
        (state.session != null || state.playback.hasQueue)

    Scaffold(
        bottomBar = {
            if (showFloating) {
                FloatingPlayerBar(
                    state = state,
                    onToggle = reading::togglePlayPause,
                    onOpen = { nav.navigate(Routes.PLAYER) },
                )
            }
        },
    ) { padding ->
        NavHost(
            navController = nav,
            startDestination = if (startOnboarding) Routes.ONBOARDING else Routes.HOME,
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            composable(Routes.ONBOARDING) {
                OnboardingScreen(
                    onFinished = {
                        root.completeOnboarding()
                        nav.navigate(Routes.HOME) {
                            popUpTo(Routes.ONBOARDING) { inclusive = true }
                        }
                    },
                )
            }
            composable(Routes.HOME) {
                HomeScreen(
                    vm = reading,
                    onEdit = { nav.navigate(Routes.INPUT) },
                    onOpenPlayer = { nav.navigate(Routes.PLAYER) },
                    onOpenHistory = { nav.navigate(Routes.HISTORY) },
                    onOpenSettings = { nav.navigate(Routes.SETTINGS) },
                    onOpenAbout = { nav.navigate(Routes.ABOUT) },
                    onReplayStarted = { nav.navigate(Routes.PLAYER) },
                )
            }
            composable(Routes.INPUT) {
                TextInputScreen(
                    vm = reading,
                    onBack = { nav.popBackStack() },
                    onSpeakStarted = { nav.navigate(Routes.PLAYER) },
                )
            }
            composable(Routes.PLAYER) {
                PlayerScreen(vm = reading, onBack = { nav.popBackStack() })
            }
            composable(Routes.HISTORY) {
                HistoryScreen(
                    onBack = { nav.popBackStack() },
                    onReplayStarted = { nav.navigate(Routes.PLAYER) },
                )
            }
            composable(Routes.SETTINGS) {
                SettingsScreen(
                    onBack = { nav.popBackStack() },
                    onOpenAbout = { nav.navigate(Routes.ABOUT) },
                )
            }
            composable(Routes.ABOUT) {
                AboutScreen(onBack = { nav.popBackStack() })
            }
        }
    }
}

package com.example.navigation

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.example.ui.screens.AboutScreen
import com.example.ui.screens.ActiveCallScreen
import com.example.ui.screens.CallHistoryScreen
import com.example.ui.screens.CallRecordingsScreen
import com.example.ui.screens.CallSummaryScreen
import com.example.ui.screens.ContactDetailsScreen
import com.example.ui.screens.ContactsScreen
import com.example.ui.screens.DialerScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.IncomingCallScreen
import com.example.ui.screens.PermissionsScreen
import com.example.ui.screens.ScheduledCallsScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.SplashScreen
import com.example.ui.screens.VoicemailScreen
import com.example.ui.viewmodel.LyraMainViewModel

@Composable
fun LyraNavGraph(
    navController: NavHostController,
    viewModel: LyraMainViewModel,
    startDestination: String = LyraDestinations.DIALER,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier,
        enterTransition = {
            fadeIn(tween(150)) + slideInHorizontally(tween(170, easing = FastOutSlowInEasing)) { it / 6 }
        },
        exitTransition = {
            fadeOut(tween(110)) + slideOutHorizontally(tween(150, easing = FastOutSlowInEasing)) { -it / 6 }
        },
        popEnterTransition = {
            fadeIn(tween(150)) + slideInHorizontally(tween(170, easing = FastOutSlowInEasing)) { -it / 6 }
        },
        popExitTransition = {
            fadeOut(tween(110)) + slideOutHorizontally(tween(150, easing = FastOutSlowInEasing)) { it / 6 }
        }
    ) {
        composable(LyraDestinations.SPLASH) {
            SplashScreen(
                onNavigateToHome = {
                    val currentRoute = navController.currentBackStackEntry?.destination?.route
                    if (currentRoute == LyraDestinations.SPLASH &&
                        com.example.service.CallStateManager.callState.value == com.example.domain.model.CallStateEnum.IDLE
                    ) {
                        navController.navigate(LyraDestinations.DIALER) {
                            popUpTo(LyraDestinations.SPLASH) { inclusive = true }
                        }
                    }
                }
            )
        }

        val navigateTab: (String) -> Unit = { route ->
            navController.navigate(route) {
                popUpTo(navController.graph.findStartDestination().id) {
                    saveState = true
                }
                launchSingleTop = true
                restoreState = true
            }
        }

        composable(LyraDestinations.HOME) {
            HomeScreen(
                viewModel = viewModel,
                onNavigateToDialer = { navigateTab(LyraDestinations.DIALER) },
                onNavigateToHistory = { navigateTab(LyraDestinations.CALL_HISTORY) },
                onNavigateToContacts = { navigateTab(LyraDestinations.CONTACTS) },
                onNavigateToSettings = { navController.navigate(LyraDestinations.SETTINGS) },
                onNavigateToPermissions = { navController.navigate(LyraDestinations.PERMISSIONS) },
                onNavigateToAbout = { navController.navigate(LyraDestinations.ABOUT) },
                onNavigateToIncomingCall = { navController.navigate(LyraDestinations.INCOMING_CALL) },
                onNavigateToScheduledCalls = { navController.navigate(LyraDestinations.SCHEDULED_CALLS) },
                onNavigateToVoicemail = { navController.navigate(LyraDestinations.VOICEMAIL) },
                onNavigateToCallRecordings = { navController.navigate(LyraDestinations.CALL_RECORDINGS) },
                onNavigateTab = navigateTab
            )
        }

        composable(LyraDestinations.DIALER) {
            DialerScreen(
                viewModel = viewModel,
                onNavigateTab = navigateTab,
                onNavigateToActiveCall = {
                    navController.navigate(LyraDestinations.ACTIVE_CALL)
                },
                onNavigateToContactDetails = { _ ->
                    navController.navigate(LyraDestinations.CONTACT_DETAILS)
                }
            )
        }

        composable(LyraDestinations.INCOMING_CALL) {
            IncomingCallScreen(
                viewModel = viewModel,
                onAcceptCall = {
                    navController.navigate(LyraDestinations.ACTIVE_CALL) {
                        popUpTo(LyraDestinations.INCOMING_CALL) { inclusive = true }
                    }
                },
                onDeclineCall = {
                    viewModel.resetCallState()
                    if (navController.currentBackStackEntry?.destination?.route == LyraDestinations.INCOMING_CALL) {
                        if (!navController.popBackStack()) {
                            navController.navigate(LyraDestinations.DIALER) {
                                popUpTo(LyraDestinations.INCOMING_CALL) { inclusive = true }
                                launchSingleTop = true
                            }
                        }
                    }
                },
                onVoicemailCall = {
                    navController.navigate(LyraDestinations.VOICEMAIL) {
                        popUpTo(LyraDestinations.INCOMING_CALL) { inclusive = true }
                    }
                }
            )
        }

        composable(LyraDestinations.VOICEMAIL) {
            VoicemailScreen(
                viewModel = viewModel,
                onBackClick = { navController.popBackStack() },
                onOpenSettings = { navController.navigate(LyraDestinations.SETTINGS) },
                onDone = {
                    if (!navController.popBackStack()) {
                        navController.navigate(LyraDestinations.DIALER) {
                            popUpTo(LyraDestinations.VOICEMAIL) { inclusive = true }
                            launchSingleTop = true
                        }
                    }
                }
            )
        }

        composable(LyraDestinations.ACTIVE_CALL) {
            ActiveCallScreen(
                viewModel = viewModel,
                onEndCall = {
                    viewModel.resetCallState()
                    if (navController.currentBackStackEntry?.destination?.route == LyraDestinations.ACTIVE_CALL) {
                        if (!navController.popBackStack()) {
                            navController.navigate(LyraDestinations.DIALER) {
                                popUpTo(LyraDestinations.ACTIVE_CALL) { inclusive = true }
                                launchSingleTop = true
                            }
                        }
                    }
                },
                onCancelCall = {
                    viewModel.resetCallState()
                    if (navController.currentBackStackEntry?.destination?.route == LyraDestinations.ACTIVE_CALL) {
                        if (!navController.popBackStack()) {
                            navController.navigate(LyraDestinations.DIALER) {
                                popUpTo(LyraDestinations.ACTIVE_CALL) { inclusive = true }
                                launchSingleTop = true
                            }
                        }
                    }
                }
            )
        }

        composable(LyraDestinations.CALL_SUMMARY) {
            CallSummaryScreen(
                viewModel = viewModel,
                onDone = {
                    if (!navController.popBackStack()) {
                        navController.navigate(LyraDestinations.DIALER) {
                            popUpTo(LyraDestinations.CALL_SUMMARY) { inclusive = true }
                            launchSingleTop = true
                        }
                    }
                }
            )
        }

        composable(LyraDestinations.CALL_HISTORY) {
            CallHistoryScreen(
                viewModel = viewModel,
                onBackClick = { navController.popBackStack() },
                onNavigateTab = navigateTab,
                onOpenContactDetails = {
                    navController.navigate(LyraDestinations.CONTACT_DETAILS)
                },
                onOpenRecordings = {
                    navController.navigate(LyraDestinations.CALL_RECORDINGS)
                }
            )
        }

        composable(LyraDestinations.CONTACTS) {
            ContactsScreen(
                mainViewModel = viewModel,
                onContactClick = { contact ->
                    navController.navigate(LyraDestinations.CONTACT_DETAILS)
                },
                onBackClick = { navController.popBackStack() },
                onNavigateTab = navigateTab
            )
        }

        composable(LyraDestinations.SCHEDULED_CALLS) {
            ScheduledCallsScreen(
                viewModel = viewModel,
                onBackClick = { navController.popBackStack() }
            )
        }

        composable(LyraDestinations.CALL_RECORDINGS) {
            CallRecordingsScreen(
                viewModel = viewModel,
                onBackClick = { navController.popBackStack() }
            )
        }

        composable(LyraDestinations.CONTACT_DETAILS) {
            ContactDetailsScreen(
                viewModel = viewModel,
                onBackClick = { navController.popBackStack() }
            )
        }

        composable(LyraDestinations.SETTINGS) {
            SettingsScreen(
                viewModel = viewModel,
                onBackClick = { navController.popBackStack() },
                onNavigateToAbout = { navController.navigate(LyraDestinations.ABOUT) },
                onNavigateToPermissions = { navController.navigate(LyraDestinations.PERMISSIONS) },
                onPreviewIncomingCall = { navController.navigate(LyraDestinations.INCOMING_CALL) },
                onPreviewOutgoingCall = { navController.navigate(LyraDestinations.ACTIVE_CALL) }
            )
        }

        composable(LyraDestinations.PERMISSIONS) {
            PermissionsScreen(
                viewModel = viewModel,
                onBackClick = { navController.popBackStack() }
            )
        }

        composable(LyraDestinations.ABOUT) {
            AboutScreen(
                onBackClick = { navController.popBackStack() },
                viewModel = viewModel
            )
        }
    }
}

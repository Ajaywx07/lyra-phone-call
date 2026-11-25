package com.example.navigation

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.example.service.CallStateManager
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
import com.example.ui.theme.LyraMotion
import com.example.ui.theme.rememberAnimationsEnabled
import com.example.ui.viewmodel.LyraMainViewModel

private fun tabOrderIndex(route: String?): Int = when (route) {
    LyraDestinations.DIALER -> 0
    LyraDestinations.CALL_HISTORY -> 1
    LyraDestinations.CONTACTS -> 2
    LyraDestinations.HOME -> 3
    else -> -1
}

private fun isCallOverlayRoute(route: String?): Boolean = when (route) {
    LyraDestinations.INCOMING_CALL,
    LyraDestinations.ACTIVE_CALL,
    LyraDestinations.VOICEMAIL,
    LyraDestinations.CALL_SUMMARY -> true
    else -> false
}

@Composable
fun LyraNavGraph(
    navController: NavHostController,
    viewModel: LyraMainViewModel,
    startDestination: String = LyraDestinations.DIALER,
    modifier: Modifier = Modifier
) {
    val animationsEnabled = rememberAnimationsEnabled()

    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier,
        enterTransition = {
            val fromRoute = initialState.destination.route
            val toRoute = targetState.destination.route
            val fromTab = tabOrderIndex(fromRoute)
            val toTab = tabOrderIndex(toRoute)
            when {
                fromTab >= 0 && toTab >= 0 -> {
                    val forward = toTab >= fromTab
                    fadeIn(LyraMotion.calmTween(190, animationsEnabled)) +
                        slideInHorizontally(LyraMotion.calmTween(220, animationsEnabled)) {
                            if (forward) it / 14 else -it / 14
                        } +
                        scaleIn(
                            animationSpec = LyraMotion.calmTween(220, animationsEnabled),
                            initialScale = 0.985f
                        )
                }
                isCallOverlayRoute(toRoute) -> {
                    fadeIn(LyraMotion.calmTween(200, animationsEnabled)) +
                        slideInVertically(LyraMotion.calmTween(240, animationsEnabled)) { it / 10 }
                }
                else -> {
                    fadeIn(LyraMotion.calmTween(200, animationsEnabled)) +
                        slideInHorizontally(LyraMotion.calmTween(240, animationsEnabled)) { it / 5 }
                }
            }
        },
        exitTransition = {
            val fromRoute = initialState.destination.route
            val toRoute = targetState.destination.route
            val fromTab = tabOrderIndex(fromRoute)
            val toTab = tabOrderIndex(toRoute)
            when {
                fromTab >= 0 && toTab >= 0 -> {
                    val forward = toTab >= fromTab
                    fadeOut(LyraMotion.calmTween(150, animationsEnabled)) +
                        slideOutHorizontally(LyraMotion.calmTween(200, animationsEnabled)) {
                            if (forward) -it / 14 else it / 14
                        }
                }
                isCallOverlayRoute(toRoute) -> {
                    fadeOut(LyraMotion.calmTween(160, animationsEnabled))
                }
                else -> {
                    fadeOut(LyraMotion.calmTween(160, animationsEnabled)) +
                        slideOutHorizontally(LyraMotion.calmTween(220, animationsEnabled)) { -it / 6 }
                }
            }
        },
        popEnterTransition = {
            val fromRoute = initialState.destination.route
            val toRoute = targetState.destination.route
            val fromTab = tabOrderIndex(fromRoute)
            val toTab = tabOrderIndex(toRoute)
            when {
                fromTab >= 0 && toTab >= 0 -> {
                    val forward = toTab >= fromTab
                    fadeIn(LyraMotion.calmTween(190, animationsEnabled)) +
                        slideInHorizontally(LyraMotion.calmTween(220, animationsEnabled)) {
                            if (forward) it / 14 else -it / 14
                        }
                }
                isCallOverlayRoute(fromRoute) -> {
                    fadeIn(LyraMotion.calmTween(190, animationsEnabled))
                }
                else -> {
                    fadeIn(LyraMotion.calmTween(200, animationsEnabled)) +
                        slideInHorizontally(LyraMotion.calmTween(240, animationsEnabled)) { -it / 6 }
                }
            }
        },
        popExitTransition = {
            val fromRoute = initialState.destination.route
            val toRoute = targetState.destination.route
            val fromTab = tabOrderIndex(fromRoute)
            val toTab = tabOrderIndex(toRoute)
            when {
                fromTab >= 0 && toTab >= 0 -> {
                    val forward = toTab >= fromTab
                    fadeOut(LyraMotion.calmTween(150, animationsEnabled)) +
                        slideOutHorizontally(LyraMotion.calmTween(200, animationsEnabled)) {
                            if (forward) -it / 14 else it / 14
                        }
                }
                isCallOverlayRoute(fromRoute) -> {
                    fadeOut(LyraMotion.calmTween(180, animationsEnabled)) +
                        slideOutVertically(LyraMotion.calmTween(220, animationsEnabled)) { it / 10 }
                }
                else -> {
                    fadeOut(LyraMotion.calmTween(160, animationsEnabled)) +
                        slideOutHorizontally(LyraMotion.calmTween(240, animationsEnabled)) { it / 5 }
                }
            }
        }
    ) {
        composable(LyraDestinations.SPLASH) {
            SplashScreen(
                onNavigateToHome = {
                    val currentRoute = navController.currentBackStackEntry?.destination?.route
                    if (currentRoute == LyraDestinations.SPLASH) {
                        navController.navigate(LyraDestinations.DIALER) {
                            popUpTo(LyraDestinations.SPLASH) { inclusive = true }
                            launchSingleTop = true
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
                onNavigateToSettings = {
                    navController.navigate(LyraDestinations.SETTINGS) { launchSingleTop = true }
                },
                onNavigateToPermissions = {
                    navController.navigate(LyraDestinations.PERMISSIONS) { launchSingleTop = true }
                },
                onNavigateToAbout = {
                    navController.navigate(LyraDestinations.ABOUT) { launchSingleTop = true }
                },
                onNavigateToIncomingCall = {
                    navController.navigate(LyraDestinations.INCOMING_CALL) { launchSingleTop = true }
                },
                onNavigateToScheduledCalls = {
                    navController.navigate(LyraDestinations.SCHEDULED_CALLS) { launchSingleTop = true }
                },
                onNavigateToVoicemail = {
                    navController.navigate(LyraDestinations.VOICEMAIL) { launchSingleTop = true }
                },
                onNavigateTab = navigateTab
            )
        }

        composable(LyraDestinations.DIALER) {
            DialerScreen(
                viewModel = viewModel,
                onNavigateTab = navigateTab,
                onNavigateToActiveCall = {
                    CallStateManager.setUserMinimizedCallUi(false)
                    navController.navigate(LyraDestinations.ACTIVE_CALL) { launchSingleTop = true }
                },
                onNavigateToContactDetails = { _ ->
                    navController.navigate(LyraDestinations.CONTACT_DETAILS) { launchSingleTop = true }
                }
            )
        }

        composable(LyraDestinations.INCOMING_CALL) {
            IncomingCallScreen(
                viewModel = viewModel,
                onAcceptCall = {
                    CallStateManager.setUserMinimizedCallUi(false)
                    navController.navigate(LyraDestinations.ACTIVE_CALL) {
                        popUpTo(LyraDestinations.INCOMING_CALL) { inclusive = true }
                        launchSingleTop = true
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
                    CallStateManager.setUserMinimizedCallUi(false)
                    navController.navigate(LyraDestinations.VOICEMAIL) {
                        popUpTo(LyraDestinations.INCOMING_CALL) { inclusive = true }
                        launchSingleTop = true
                    }
                }
            )
        }

        composable(LyraDestinations.VOICEMAIL) {
            VoicemailScreen(
                viewModel = viewModel,
                onBackClick = { navController.popBackStack() },
                onOpenSettings = {
                    navController.navigate(LyraDestinations.SETTINGS) { launchSingleTop = true }
                },
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
                },
                onNavigateBackToApp = {
                    CallStateManager.setUserMinimizedCallUi(true)
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
                    navController.navigate(LyraDestinations.CONTACT_DETAILS) { launchSingleTop = true }
                }
            )
        }

        composable(LyraDestinations.CONTACTS) {
            ContactsScreen(
                mainViewModel = viewModel,
                onContactClick = { contact ->
                    navController.navigate(LyraDestinations.CONTACT_DETAILS) { launchSingleTop = true }
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
                onNavigateToAbout = {
                    navController.navigate(LyraDestinations.ABOUT) { launchSingleTop = true }
                },
                onNavigateToPermissions = {
                    navController.navigate(LyraDestinations.PERMISSIONS) { launchSingleTop = true }
                },
                onPreviewIncomingCall = {
                    navController.navigate(LyraDestinations.INCOMING_CALL) { launchSingleTop = true }
                },
                onPreviewOutgoingCall = {
                    CallStateManager.setUserMinimizedCallUi(false)
                    navController.navigate(LyraDestinations.ACTIVE_CALL) { launchSingleTop = true }
                }
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

        composable(LyraDestinations.RECORDINGS) {
            CallRecordingsScreen(
                viewModel = viewModel,
                onBackClick = { navController.popBackStack() }
            )
        }
    }
}

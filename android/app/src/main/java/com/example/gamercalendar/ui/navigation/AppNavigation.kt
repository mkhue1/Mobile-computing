package com.example.gamercalendar.ui.navigation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.gamercalendar.ui.components.app.AppBottomBar
import com.example.gamercalendar.ui.components.app.AppScaffold
import com.example.gamercalendar.ui.components.app.AppTopBar
import com.example.gamercalendar.ui.components.layout.ScreenContainer
import com.example.gamercalendar.ui.screens.CardTestScreen
import com.example.gamercalendar.ui.screens.CreateSessionScreen
import com.example.gamercalendar.ui.screens.HomeScreen
import com.example.gamercalendar.ui.screens.ManageSessionScreen
import com.example.gamercalendar.ui.screens.UsersScreen
import com.example.gamercalendar.viewmodel.AuthViewModel


@Composable
fun AppNavigation(
    authViewModel: AuthViewModel
) {
    val navController = rememberNavController()

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    AppScaffold(
        topBar = {
            AppTopBar(
                onProfileClick = {
                    navController.navigate(Routes.ITEM_5) {
                        launchSingleTop = true
                        restoreState = true

                        popUpTo(Routes.ITEM_1) {
                            saveState = true
                        }
                    }
                }
            )
        },
        bottomBar = {
            AppBottomBar(
                currentRoute = currentRoute,
                onNavigate = { route ->
                    // Home always resets to its root; saving/restoring state here would bring back
                    // screens stacked on top of it, such as Create session.
                    val isHome = route == Routes.ITEM_1

                    navController.navigate(route) {
                        launchSingleTop = true
                        restoreState = !isHome

                        popUpTo(Routes.ITEM_1) {
                            saveState = !isHome
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Routes.ITEM_1,
            modifier = Modifier.padding(innerPadding),
            enterTransition = {
                fadeIn(
                    animationSpec = tween(30)
                )
            },
            exitTransition = {
                fadeOut(
                    animationSpec = tween(30)
                )
            },
            popEnterTransition = {
                fadeIn(
                    animationSpec = tween(30)
                )
            },
            popExitTransition = {
                fadeOut(
                    animationSpec = tween(30)
                )
            }
        ) {
            composable(Routes.ITEM_1) {
                HomeScreen(
                    onCreateSessionClick = {
                        navController.navigate(Routes.CREATE_SESSION)
                    },
                    onSessionClick = { sessionId ->
                        navController.navigate(Routes.manageSession(sessionId))
                    }
                )
            }

            composable(Routes.CREATE_SESSION) {
                CreateSessionScreen(
                    onSaved = { navController.popBackStack() },
                    onCancel = { navController.popBackStack() }
                )
            }

            composable(
                route = Routes.MANAGE_SESSION,
                arguments = listOf(navArgument(Routes.ARG_SESSION_ID) { type = NavType.StringType })
            ) {
                ManageSessionScreen(
                    onEditClick = { sessionId ->
                        navController.navigate(Routes.editSession(sessionId))
                    },
                    onLeft = { navController.popBackStack() }
                )
            }

            composable(
                route = Routes.EDIT_SESSION,
                arguments = listOf(navArgument(Routes.ARG_SESSION_ID) { type = NavType.StringType })
            ) {
                CreateSessionScreen(
                    onSaved = { navController.popBackStack() },
                    onCancel = { navController.popBackStack() }
                )
            }

            composable(Routes.ITEM_2) {
                CardTestScreen()
            }

            composable(Routes.USERS) {
                UsersScreen(
                    authViewModel = authViewModel
                )
            }

            composable(Routes.ITEM_4) {
                PlaceholderScreen(
                    text = "Item 4"
                )
            }

            composable(Routes.ITEM_5) {
                PlaceholderScreen(
                    text = "Item 5"
                )
            }
        }
    }
}

@Composable
private fun PlaceholderScreen(
    text: String
) {
    ScreenContainer {
        Text(
            text = text,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground
        )
    }
}
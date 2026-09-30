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
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.gamercalendar.ui.components.app.AppBottomBar
import com.example.gamercalendar.ui.components.app.AppScaffold
import com.example.gamercalendar.ui.components.app.AppTopBar
import com.example.gamercalendar.ui.components.layout.ScreenContainer
import com.example.gamercalendar.ui.screens.UsersScreen
import com.example.gamercalendar.ui.screens.CardTestScreen
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
                    navController.navigate(route) {
                        launchSingleTop = true
                        restoreState = true

                        popUpTo(Routes.ITEM_1) {
                            saveState = true
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
                PlaceholderScreen(
                    text = "Item 1"
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
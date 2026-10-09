package com.example.gamercalendar.ui.navigation

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
import androidx.compose.runtime.remember
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.gamercalendar.ui.feature.calendar.CalendarScreen
import com.example.gamercalendar.ui.feature.calendar.CalendarViewModel
import com.example.gamercalendar.ui.feature.calendar.CalendarViewModelFactory
import com.example.gamercalendar.ui.components.app.AppBottomBar
import com.example.gamercalendar.ui.components.app.AppScaffold
import com.example.gamercalendar.ui.components.app.AppTopBar
import com.example.gamercalendar.ui.components.layout.ScreenContainer
import com.example.gamercalendar.ui.feature.auth.AuthViewModel
import com.example.gamercalendar.ui.feature.friends.AddFriendScreen
import com.example.gamercalendar.ui.feature.friends.FriendsHubScreen
import com.example.gamercalendar.ui.feature.groups.GroupDetailScreen
import com.example.gamercalendar.ui.feature.home.HomeScreen
import com.example.gamercalendar.ui.feature.profile.UsersScreen
import com.example.gamercalendar.ui.feature.sessions.CreateSessionScreen
import com.example.gamercalendar.ui.feature.sessions.ManageSessionScreen


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
                    navController.navigate(Routes.USERS) {
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
                    // Home and Friends always reset to their root; saving/restoring state here would
                    // bring back screens stacked on top of them, such as Create session or Add friend.
                    val resetsToRoot = route == Routes.ITEM_1 || route == Routes.FRIENDS_HUB

                    // If this tab is already underneath the current screen (for example Calendar underneath
                    // Manage session), go back to it. Its state, such as the selected day, is kept.
                    val returnedToTab = !resetsToRoot && navController.popBackStack(route, inclusive = false)

                    if (!returnedToTab) {
                        navController.navigate(route) {
                            launchSingleTop = true
                            restoreState = !resetsToRoot

                            popUpTo(Routes.ITEM_1) {
                                saveState = !resetsToRoot
                            }
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
            },
            predictivePopEnterTransition = {
                fadeIn()
            },
            predictivePopExitTransition = {
                fadeOut()
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
                val calendarViewModel: CalendarViewModel = viewModel(factory = CalendarViewModelFactory())
                CalendarScreen(
                    viewModel = calendarViewModel,
                    onSessionClick = { sessionId -> navController.navigate(Routes.manageSession(sessionId))
                    }
                )
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

            composable(Routes.FRIENDS_HUB) {
                FriendsHubScreen(
                    onAddFriendClick = {
                        navController.navigate(Routes.ADD_FRIEND)
                    },
                    onGroupClick = { groupId ->
                        navController.navigate(Routes.groupDetail(groupId))
                    }
                )
            }

            composable(Routes.ADD_FRIEND) {
                AddFriendScreen(
                    onDone = { navController.popBackStack() }
                )
            }

            composable(
                route = Routes.GROUP_DETAIL,
                arguments = listOf(navArgument(Routes.ARG_GROUP_ID) { type = NavType.StringType })
            ) {
                GroupDetailScreen(
                    onLeft = { navController.popBackStack() }
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
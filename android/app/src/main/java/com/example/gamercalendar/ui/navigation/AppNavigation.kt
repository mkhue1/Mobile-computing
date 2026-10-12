package com.example.gamercalendar.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.gamercalendar.data.repository.UserRepository
import com.example.gamercalendar.data.session.CurrentUserStore
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.gamercalendar.ui.feature.calendar.CalendarScreen
import com.example.gamercalendar.ui.feature.calendar.CalendarViewModel
import com.example.gamercalendar.ui.feature.calendar.CalendarViewModelFactory
import com.example.gamercalendar.ui.feature.search.SearchScreen
import com.example.gamercalendar.ui.feature.search.SearchViewModel
import com.example.gamercalendar.ui.feature.search.SearchViewModelFactory
import com.example.gamercalendar.ui.components.app.AppBottomBar
import com.example.gamercalendar.ui.components.app.AppScaffold
import com.example.gamercalendar.ui.components.app.AppTopBar
import com.example.gamercalendar.ui.components.layout.ScreenContainer
import com.example.gamercalendar.ui.feature.auth.AuthViewModel
import com.example.gamercalendar.ui.feature.friends.AddFriendScreen
import com.example.gamercalendar.ui.feature.friends.FriendsHubScreen
import com.example.gamercalendar.ui.feature.groups.GroupDetailScreen
import com.example.gamercalendar.firebase.PushTokenRegistration
import com.example.gamercalendar.firebase.SocialOpenEvents
import com.example.gamercalendar.firebase.SocialOpenRequest
import com.example.gamercalendar.geofence.NearbyOpenEvents
import com.example.gamercalendar.geofence.SessionGeofences
import com.example.gamercalendar.ui.feature.explore.ExploreScreen
import com.example.gamercalendar.ui.feature.home.HomeScreen
import com.example.gamercalendar.ui.feature.map.SessionsMapScreen
import com.example.gamercalendar.ui.feature.profile.UsersScreen
import com.example.gamercalendar.ui.feature.sessions.CreateSessionScreen
import com.example.gamercalendar.ui.feature.sessions.ManageSessionScreen
import kotlinx.coroutines.CancellationException


@Composable
fun AppNavigation(
    authViewModel: AuthViewModel
) {
    val navController = rememberNavController()

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    val currentUser by CurrentUserStore.user.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // A cold start with a stored token skips login, so fetch the user here to fill CurrentUserStore.
    LaunchedEffect(Unit) {
        try {
            UserRepository().getCurrentUser()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Keep the default profile icon; screens surface their own errors.
        }
        // Runs after every login and app launch, so the server always has this phone's current
        // push token.
        PushTokenRegistration.register()
        try {
            SessionGeofences.refreshIfEnabled(context)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Explore surfaces a failure when nearby sessions are turned on.
        }
    }

    LaunchedEffect(Unit) {
        NearbyOpenEvents.requests.collect { sessionId ->
            NearbyOpenEvents.clear()
            if (sessionId == null) {
                navController.navigate(Routes.EXPLORE) {
                    launchSingleTop = true
                    restoreState = true
                    popUpTo(Routes.ITEM_1) { saveState = true }
                }
                navController.popBackStack(Routes.EXPLORE, inclusive = false)
            } else {
                navController.navigate(Routes.manageSession(sessionId)) {
                    launchSingleTop = true
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        SocialOpenEvents.requests.collect { request ->
            SocialOpenEvents.clear()
            when (request) {
                is SocialOpenRequest.SessionInvite ->
                    navController.navigate(Routes.manageSession(request.sessionId)) {
                        launchSingleTop = true
                    }
                SocialOpenRequest.FriendRequests -> {
                    // the same way the bottom bar opens the Friends tab
                    navController.navigate(Routes.FRIENDS_HUB) {
                        launchSingleTop = true
                        restoreState = true
                        popUpTo(Routes.ITEM_1) { saveState = true }
                    }
                    navController.popBackStack(Routes.FRIENDS_HUB, inclusive = false)
                }
            }
        }
    }

    AppScaffold(
        topBar = {
            AppTopBar(
                user = currentUser,
                onProfileClick = {
                    navController.navigate(Routes.USERS) {
                        launchSingleTop = true
                    }
                }
            )
        },
        bottomBar = {
            AppBottomBar(
                currentRoute = currentRoute,
                onNavigate = { route ->
                    // Home always opens fresh, restoring it would bring back tabs stacked on top,
                    // it is never removed from back stack so state is preserved anyway
                    val keepsState = route != Routes.ITEM_1

                    navController.navigate(route) {
                        launchSingleTop = true
                        restoreState = keepsState

                        popUpTo(Routes.ITEM_1) {
                            saveState = true
                        }
                    }

                    // A restored tab brings back its whole stack, e.g. Calendar with a Manage session on top.
                    // Drop back to the tab's own screen, so every tab opens at its root.
                    if (keepsState) {
                        navController.popBackStack(route, inclusive = false)
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

            composable(Routes.EXPLORE) {
                ExploreScreen(
                    onMapClick = {
                        navController.navigate(Routes.SESSIONS_MAP)
                    },
                    onSessionClick = { sessionId ->
                        navController.navigate(Routes.manageSession(sessionId))
                    }
                )
            }

            composable(Routes.SESSIONS_MAP) {
                SessionsMapScreen(
                    onBack = { navController.popBackStack() },
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
                val calendarViewModel: CalendarViewModel = viewModel(
                    factory = CalendarViewModelFactory(context = LocalContext.current)
                )
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
                val searchViewModel: SearchViewModel = viewModel(
                    factory = SearchViewModelFactory(contentResolver = LocalContext.current.applicationContext.contentResolver)
                )
                SearchScreen(
                    viewModel = searchViewModel,
                    onSessionClick = { sessionId -> navController.navigate(Routes.manageSession(sessionId)) }
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
                    onDone = { navController.popBackStack() },
                    onOpenProfile = {
                        navController.navigate(Routes.USERS) {
                            launchSingleTop = true
                        }
                    }
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
package com.example.gamercalendar.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.ui.graphics.vector.ImageVector

data class BottomNavItem(
    val route: String,
    val label: String,
    val icon: ImageVector
)

val bottomNavItems = listOf(
    BottomNavItem(
        route = Routes.ITEM_1,
        label = "Home",
        icon = Icons.Default.Home
    ),
    BottomNavItem(
        route = Routes.ITEM_2,
        label = "Calendar",
        icon = Icons.Default.CalendarMonth
    ),
    BottomNavItem(
        route = Routes.USERS,
        label = "Users",
        icon = Icons.Default.Person
    ),
    BottomNavItem(
        route = Routes.FRIENDS_HUB,
        label = "Friends",
        icon = Icons.Default.Group
    ),
    BottomNavItem(
        route = Routes.ITEM_5,
        label = "Search",
        icon = Icons.Default.Search
    )
)
package com.example.gamercalendar.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
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
        label = "Item 1",
        icon = Icons.Default.Home
    ),
    BottomNavItem(
        route = Routes.ITEM_2,
        label = "Item 2",
        icon = Icons.Default.Star
    ),
    BottomNavItem(
        route = Routes.USERS,
        label = "Users",
        icon = Icons.Default.Person
    ),
    BottomNavItem(
        route = Routes.ITEM_4,
        label = "Item 4",
        icon = Icons.Default.Star
    ),
    BottomNavItem(
        route = Routes.ITEM_5,
        label = "Item 5",
        icon = Icons.Default.Star
    )
)
package com.swordfish.lemuroid.app.mobile.feature.main

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import com.swordfish.lemuroid.R

@Composable
fun MainNavigationBar(
    currentRoute: MainRoute?,
    // Tab shown as selected (see mainTab).
    selectedTab: MainRoute,
    navController: NavHostController,
) {
    AnimatedVisibility(
        visible = currentRoute?.showBottomNavigation != false,
        enter = expandVertically(),
        exit = shrinkVertically(),
    ) {
        LemuroidNavigationBar(selectedTab, navController)
    }
}

@Composable
private fun LemuroidNavigationBar(
    selectedTab: MainRoute,
    navController: NavHostController,
) {
    // Same color as the screen background, like the top bar, so the whole interface has a single color.
    NavigationBar(
        modifier = Modifier.fillMaxWidth(),
        containerColor = MaterialTheme.colorScheme.background,
        tonalElevation = 0.dp,
    ) {
        MainNavigationRoutes.values().forEach { destination ->
            val isSelected = selectedTab == destination.route
            val iconDrawable = if (isSelected) destination.selectedIcon else destination.unselectedIcon

            NavigationBarItem(
                icon = {
                    Icon(
                        imageVector = iconDrawable,
                        contentDescription = stringResource(destination.titleId),
                    )
                },
                label = { Text(stringResource(destination.titleId)) },
                selected = isSelected,
                // The selected tab has the almost grey tone of the logo green with a white icon, like the TV tabs.
                colors =
                    NavigationBarItemDefaults.colors(
                        indicatorColor = colorResource(R.color.playretro_soft_green),
                        selectedIconColor = colorResource(R.color.playretro_on_soft_green),
                    ),
                onClick = {
                    navController.navigate(destination.route.route) {
                        // Pop up to the start destination of the graph to
                        // avoid building up a large stack of destinations
                        // on the back stack as users select items
                        popUpTo(navController.graph.findStartDestination().id) {
                            saveState = false
                        }
                        // Avoid multiple copies of the same destination when
                        // reselecting the same item
                        launchSingleTop = true
                        // Restore state when reselecting a previously selected item
                        restoreState = false
                    }
                },
            )
        }
    }
}

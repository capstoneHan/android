package com.example.mobilecapstone

import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
internal fun FashionBottomBar(
    rootTabs: List<NavItem>,
    currentScreen: AppScreen,
    onSelectScreen: (AppScreen) -> Unit
) {
    NavigationBar(
        modifier = Modifier.navigationBarsPadding(),
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        rootTabs.forEach { item ->
            NavigationBarItem(
                selected = currentScreen == item.screen,
                onClick = { onSelectScreen(item.screen) },
                icon = item.icon,
                label = { Text(item.label) }
            )
        }
    }
}

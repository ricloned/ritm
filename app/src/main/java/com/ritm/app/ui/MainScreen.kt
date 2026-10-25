package com.ritm.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import com.ritm.app.ui.goals.GoalsScreen
import com.ritm.app.ui.plans.PlansScreen
import com.ritm.app.ui.settings.SettingsScreen
import com.ritm.app.ui.workout.WorkoutScreen

enum class MainTab(val title: String, val icon: ImageVector) {
    Plans("Планы", Icons.Rounded.CalendarMonth),
    Workout("Тренировки", Icons.Rounded.FitnessCenter),
    Goals("Цели", Icons.Rounded.Flag),
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun MainScreen() {
    var tab by rememberSaveable { mutableStateOf(MainTab.Plans) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    val cs = MaterialTheme.colorScheme

    if (showSettings) {
        BackHandler { showSettings = false }
        SettingsScreen(onBack = { showSettings = false })
        return
    }

    Scaffold(
        containerColor = cs.background,
        topBar = {
            TopAppBar(
                title = { Text(tab.title, fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = { showSettings = true }) {
                        Icon(Icons.Rounded.Settings, contentDescription = "Настройки")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = cs.background,
                    titleContentColor = cs.onBackground,
                ),
            )
        },
        bottomBar = {
            NavigationBar(containerColor = cs.surface) {
                MainTab.entries.forEach { t ->
                    NavigationBarItem(
                        selected = tab == t,
                        onClick = { tab = t },
                        icon = { Icon(t.icon, contentDescription = t.title) },
                        label = { Text(t.title) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = cs.onPrimaryContainer,
                            selectedTextColor = cs.primary,
                            indicatorColor = cs.primaryContainer,
                            unselectedIconColor = cs.onSurfaceVariant,
                            unselectedTextColor = cs.onSurfaceVariant,
                        ),
                    )
                }
            }
        },
    ) { pad ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(pad)
                .consumeWindowInsets(pad)
                .imePadding()
        ) {
            when (tab) {
                MainTab.Plans -> PlansScreen()
                MainTab.Workout -> WorkoutScreen()
                MainTab.Goals -> GoalsScreen()
            }
        }
    }
}

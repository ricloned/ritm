package com.ritm.app

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import com.ritm.app.data.Store
import com.ritm.app.notify.Reminders
import com.ritm.app.ui.MainScreen
import com.ritm.app.ui.MainTab
import com.ritm.app.ui.UiState
import com.ritm.app.ui.theme.RitmTheme

class MainActivity : ComponentActivity() {
    private var stoppedAt = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Store.init(this)
        Store.carryOver()
        if (savedInstanceState == null) Store.rotateLaunchTheme()
        Reminders.rescheduleAll(this)
        handleIntent(intent)
        setContent {
            RitmTheme {
                val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
                LaunchedEffect(Unit) {
                    if (Build.VERSION.SDK_INT >= 33 && !Reminders.canNotify(this@MainActivity)) {
                        permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }
                MainScreen()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent == null) return
        if (intent.getStringExtra(Reminders.EXTRA_OPEN) == Reminders.OPEN_FOOD_CAMERA) {
            intent.removeExtra(Reminders.EXTRA_OPEN)
            UiState.mainTab = MainTab.Workout
            UiState.workoutSub = 1
            UiState.foodDate = java.time.LocalDate.now()
            UiState.openFoodCamera = true
        }
    }

    override fun onStart() {
        super.onStart()
        // «Новый заход» — если приложение было свёрнуто больше 10 минут
        if (stoppedAt != 0L && SystemClock.elapsedRealtime() - stoppedAt > 10 * 60 * 1000L) {
            Store.rotateLaunchTheme()
        }
    }

    override fun onStop() {
        super.onStop()
        stoppedAt = SystemClock.elapsedRealtime()
    }

    override fun onResume() {
        super.onResume()
        UiState.refreshDay()
        Store.carryOver()
    }
}

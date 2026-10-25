package com.ritm.app

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import com.ritm.app.data.Store
import com.ritm.app.notify.Reminders
import com.ritm.app.ui.MainScreen
import com.ritm.app.ui.theme.RitmTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Store.init(this)
        Store.carryOver()
        Reminders.rescheduleAll(this)
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

    override fun onResume() {
        super.onResume()
        Store.carryOver()
    }
}

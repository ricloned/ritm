package com.ritm.app

import android.app.Application
import com.ritm.app.data.Store
import com.ritm.app.notify.Reminders

class RitmApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Store.init(this)
        Reminders.createChannels(this)
    }
}

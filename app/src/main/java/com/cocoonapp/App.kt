package com.cocoonapp

import android.app.Application
import com.cocoonapp.core.Notifier
import com.cocoonapp.data.Store

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        Store.init(this)
        Notifier.init(this)
    }
}

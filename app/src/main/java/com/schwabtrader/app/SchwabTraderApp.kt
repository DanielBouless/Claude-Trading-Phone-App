package com.schwabtrader.app

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class SchwabTraderApp : Application() {
    override fun onCreate() {
        super.onCreate()
    }
}

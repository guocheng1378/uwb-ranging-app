package com.uwb.ranging

import android.app.Application
import android.util.Log

class UwbRangingApp : Application() {
    companion object {
        const val TAG = "UWBRanging"
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "App started")
    }
}

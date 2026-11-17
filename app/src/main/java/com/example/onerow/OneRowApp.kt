package com.example.onerow

import android.app.Application
import com.example.onerow.report.CrashReport

/** Starts before the keyboard, the settings or the spell checker: makes sure a crash in any of them leaves a report behind. */
class OneRowApp : Application() {
    override fun onCreate() {
        super.onCreate()
        CrashReport.install(this)
    }
}

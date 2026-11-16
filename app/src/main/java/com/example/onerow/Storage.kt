package com.example.onerow

import android.content.Context
import android.os.UserManager
import java.io.File

/**
 * Where the keyboard keeps its settings and files: device-protected storage, which exists before the user unlocks the
 * phone after a reboot. That is what lets the keyboard be marked direct-boot aware, so Android does not warn on enabling it
 * ("won't launch until the device is unlocked after reboot"). Data written by older versions in the normal (credential
 * protected) storage is moved over the first time the phone is unlocked.
 */
object Storage {
    private val prefNames = listOf("onerow", "onerow_emoji")

    fun of(c: Context): Context = c.applicationContext.createDeviceProtectedStorageContext()

    private var migrated = false

    @Synchronized fun migrate(c: Context) {
        if (migrated) return
        val app = c.applicationContext
        if (!(app.getSystemService(Context.USER_SERVICE) as UserManager).isUserUnlocked) return      // old data is not readable yet
        val dp = of(c)
        try {
            for (n in prefNames) dp.moveSharedPreferencesFrom(app, n)
            val from = app.filesDir; val to = dp.filesDir
            from.listFiles()?.forEach { f -> val t = File(to, f.name); if (!t.exists()) f.copyRecursively(t) }
        } catch (_: Exception) { }
        migrated = true
    }

    fun prefs(c: Context, name: String) = run { migrate(c); of(c).getSharedPreferences(name, Context.MODE_PRIVATE) }
    fun filesDir(c: Context): File = of(c).filesDir
}

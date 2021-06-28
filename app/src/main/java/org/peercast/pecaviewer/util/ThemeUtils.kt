package org.peercast.pecaviewer.util

import android.app.UiModeManager
import android.content.Context
import androidx.appcompat.app.AppCompatDelegate


object ThemeUtils {
    fun setNightMode(c: Context, isNightMode: Boolean) {
        val manager = c.getSystemService(Context.UI_MODE_SERVICE) as UiModeManager
        if (isNightMode) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            manager.nightMode = UiModeManager.MODE_NIGHT_YES
        } else {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            manager.nightMode = UiModeManager.MODE_NIGHT_NO
        }
    }


}

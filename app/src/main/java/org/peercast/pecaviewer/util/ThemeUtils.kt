package org.peercast.pecaviewer.util

import android.app.Application
import android.app.UiModeManager
import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import org.koin.core.KoinComponent
import org.koin.core.inject


object ThemeUtils : KoinComponent {
    private val a by inject<Application>()

    fun setNightMode(isNightMode: Boolean) {
        val manager = a.getSystemService(Context.UI_MODE_SERVICE) as UiModeManager
        if (isNightMode) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            manager.nightMode = UiModeManager.MODE_NIGHT_YES
        } else {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            manager.nightMode = UiModeManager.MODE_NIGHT_NO
        }
    }


}

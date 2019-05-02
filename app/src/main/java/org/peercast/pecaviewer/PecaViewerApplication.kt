package org.peercast.pecaviewer

import android.app.Application
import android.app.UiModeManager
import android.content.Context
import android.util.Log
import androidx.appcompat.app.AppCompatDelegate
import org.koin.android.ext.android.get
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.core.context.startKoin
import org.koin.dsl.module
import org.peercast.pecaviewer.chat.ChatViewModel
import org.peercast.pecaviewer.player.PlayerViewModel
import timber.log.Timber

private val appModule = module {
    single { AppPreference(get()) }
    viewModel { AppViewModel(get() ) }
    viewModel { PlayerViewModel(get()) }
    viewModel { ChatViewModel(get()) }
}

class PecaViewerApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        Timber.plant(ReleaseTree())

        startKoin {
            androidContext(this@PecaViewerApplication)
            modules(appModule)
        }

        val prefs = get<AppPreference>()
        if (prefs.isNightMode){
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            val uiMan = getSystemService(Context.UI_MODE_SERVICE) as UiModeManager
            uiMan.nightMode = UiModeManager.MODE_NIGHT_YES
        }

        cleanFilesDir()
    }

    private fun cleanFilesDir (){
        val now = System.currentTimeMillis()
        filesDir.listFiles { f->
           f.extension == "png" && f.lastModified() + 7 * 24 * 60 * 60_000L < now
        }.forEach {
            it.delete()
        }
    }
}

private class ReleaseTree :  Timber.DebugTree() {
    override fun isLoggable(tag: String?, priority: Int): Boolean {
        return priority >= Log.INFO || BuildConfig.DEBUG
    }

    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        super.log(priority, tag, message, t)
        if (t != null)
            Crashlytics.logException(t)
    }
}

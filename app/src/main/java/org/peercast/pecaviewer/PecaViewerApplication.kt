package org.peercast.pecaviewer

import android.app.Application
import android.util.Log
import com.google.firebase.crashlytics.FirebaseCrashlytics
import org.koin.android.ext.android.get
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.core.KoinApplication
import org.koin.core.context.startKoin
import org.koin.dsl.module
import org.peercast.pecaviewer.chat.ChatViewModel
import org.peercast.pecaviewer.player.PlayerViewModel
import org.peercast.pecaviewer.service2.PlayerServiceEventLiveData
import org.peercast.pecaviewer.util.DefaultSquareHolder
import org.peercast.pecaviewer.util.ISquareHolder
import org.peercast.pecaviewer.util.ThemeUtils
import timber.log.Timber

private val appModule = module {
    single { AppPreference(get()) }
    viewModel { (pvm: PlayerViewModel, cvm: ChatViewModel) -> AppViewModel(get(), pvm, cvm) }
    viewModel { PlayerViewModel(get()) }
    viewModel { ChatViewModel(get()) }

    single<ISquareHolder> { DefaultSquareHolder(get()) }
    single { PlayerServiceEventLiveData() }
}

@Suppress("unused")
class PecaViewerApplication : Application() {
    private lateinit var koinApp: KoinApplication

    override fun onCreate() {
        super.onCreate()

        Timber.plant(ReleaseTree())

        //インスタンスを保持。メモリ使用量が多い為かKoin内のstatic変数が消えることがある。
        koinApp = startKoin {
            androidContext(this@PecaViewerApplication)
            modules(appModule)
        }

        val prefs = get<AppPreference>()
        ThemeUtils.setNightMode(this, prefs.isNightMode)

        cleanFilesDir()
    }

    private fun cleanFilesDir() {
        val now = System.currentTimeMillis()
        filesDir.listFiles { f ->
            f.extension == "png" && f.lastModified() + 7 * 24 * 60 * 60_000L < now
        }?.forEach {
            it.delete()
        }
    }

    private class ReleaseTree : Timber.DebugTree() {
        override fun isLoggable(tag: String?, priority: Int): Boolean {
            return priority >= Log.INFO || BuildConfig.DEBUG
        }

        override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
            super.log(priority, tag, message, t)
            if (t != null)
                FirebaseCrashlytics.getInstance().recordException(t)
        }
    }
}


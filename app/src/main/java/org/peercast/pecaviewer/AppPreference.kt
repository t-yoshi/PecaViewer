package org.peercast.pecaviewer

import android.app.Application
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import com.sothree.slidinguppanel.SlidingUpPanelLayout
import org.videolan.libvlc.MediaPlayer
import timber.log.Timber

class AppPreference(a: Application) {
    private val prefs = PreferenceManager.getDefaultSharedPreferences(a)

    /**縦画面での起動時の画面分割状態。横画面では常にプレーヤー全画面で起動する。*/
    var initPanelState: SlidingUpPanelLayout.PanelState
        get() {
            return prefs.getString(KEY_INIT_SLIDING_PANEL_STATE, null).let {
                try {
                    SlidingUpPanelLayout.PanelState.valueOf(it ?: "")
                } catch (e: IllegalArgumentException) {
                    Timber.w("value=$it")
                    SlidingUpPanelLayout.PanelState.ANCHORED
                }
            }
        }
        set(value) {
            prefs.edit {
                putString(KEY_INIT_SLIDING_PANEL_STATE, value.name)
            }
        }

    /**ビデオのスケール BestFit, 16:9など。*/
    var videoScale: MediaPlayer.ScaleType
        get() {
            return prefs.getString(KEY_VIDEO_SCALE, null).let {
                try {
                    MediaPlayer.ScaleType.valueOf(it ?: "")
                } catch (e: IllegalArgumentException) {
                    Timber.w("value=$it")
                    MediaPlayer.ScaleType.SURFACE_BEST_FIT
                }
            }
        }
        set(value) {
            prefs.edit {
                putString(KEY_VIDEO_SCALE, value.name)
            }
        }

    /**バックグラウンドで再生続行するか*/
    var isBackgroundPlaying: Boolean
        get() = prefs.getBoolean(KEY_BACKGROUND_PLAYING, false)
        set(value) {
            prefs.edit {
                putBoolean(KEY_BACKGROUND_PLAYING, value)
            }
        }

    /**フルスクーンモードか*/
    var isFullScreenMode: Boolean
        get() = prefs.getBoolean(KEY_FULLSCREEN_MODE, false)
        set(value) {
            prefs.edit {
                putBoolean(KEY_FULLSCREEN_MODE, value)
            }
        }

    var isNightMode: Boolean
        get() = prefs.getBoolean(KEY_NIGHT_MODE, false)
        set(value) {
            prefs.edit {
                putBoolean(KEY_NIGHT_MODE, value)
            }
        }

    companion object {
        private const val KEY_INIT_SLIDING_PANEL_STATE = "key_sliding_panel_state"
        private const val KEY_VIDEO_SCALE = "key_video_scale"
        private const val KEY_BACKGROUND_PLAYING = "key_background_playing"
        private const val KEY_NIGHT_MODE = "key_night_mode"
        private const val KEY_FULLSCREEN_MODE = "key_fullscreen_mode"
    }
}
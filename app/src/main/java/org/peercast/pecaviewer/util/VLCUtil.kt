package org.peercast.pecaviewer.util

import android.content.Context
import org.peercast.pecaviewer.R
import org.videolan.libvlc.MediaPlayer


fun MediaPlayer.ScaleType.toTitle(c: Context): String {
    return when (this) {
        MediaPlayer.ScaleType.SURFACE_BEST_FIT -> c.getString(R.string.surface_best_fit)
        MediaPlayer.ScaleType.SURFACE_FIT_SCREEN -> c.getString(R.string.surface_fit_screen)
        MediaPlayer.ScaleType.SURFACE_FILL -> c.getString(R.string.surface_fill)
        MediaPlayer.ScaleType.SURFACE_16_9 -> "16:9"
        MediaPlayer.ScaleType.SURFACE_4_3 -> "4:3"
        MediaPlayer.ScaleType.SURFACE_ORIGINAL -> c.getString(R.string.surface_original)
    }
}

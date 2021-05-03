package com.github.t_yoshi.vlcext

import android.graphics.Point
import org.videolan.libvlc.MediaPlayer

val MediaPlayer.videoSize: Point?
    get() = VLCExt.videoGetSize(this)
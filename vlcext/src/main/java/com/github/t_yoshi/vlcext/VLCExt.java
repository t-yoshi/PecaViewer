package com.github.t_yoshi.vlcext;

import android.graphics.Point;

import org.videolan.libvlc.LibVLC;
import org.videolan.libvlc.MediaPlayer;

/**libvlc-androidで足りない機能を拡張する*/
class VLCExt {
    private VLCExt(){}

    static native void setLoggerCallback(LibVLC libVlc, VLCLogger logger);

    private static native void initClasses();

    /**libvlc_video_take_snapshotを呼び出し、pngでスクリーンショットを撮る。
     * @deprecated ハードウェアアクセラレーション下で動作しない
     * */
    @Deprecated
    static native boolean videoTakeSnapshot(MediaPlayer player, String filepath, int width, int height);

    static native Point videoGetSize(MediaPlayer player);

    static  {
        System.loadLibrary("vlcext");
        initClasses();
    }
}

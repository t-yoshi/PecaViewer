package com.github.t_yoshi.vlcext

import androidx.annotation.WorkerThread
import androidx.lifecycle.MutableLiveData
import org.videolan.libvlc.LibVLC

data class VLCLogContext(
    /**
     * Emitter (temporaly) unique object ID or 0.
     */
    val object_id: Int,
    /**
     * Emitter object type name.
     */
    val object_type: String,
    /**
     * Emitter module (source code)
     */
    val module: String,
    /**
     * Additional header (used by VLM media)
     */
    val header: String?,
    /**
     * Source code file name or NULL.
     */
    val file: String?,
    /**
     * Source code file line number or -1.
     */
    val line: Int,
    /**
     * Source code calling function name or NULL.
     */
    val func: String?
)

data class VLCLogMessage(
    val level: Int,
    val ctx: VLCLogContext,
    val msg: String
) {
    companion object {
        const val DEBUG = 0
        const val NOTICE = 2
        const val WARNING = 3
        const val ERROR = 4
    }
}

object VLCLogger {
    private var onLog: ((VLCLogMessage)->Unit)? = null

    private fun log(level: Int, ctx: VLCLogContext, msg: String) {
        onLog?.invoke(VLCLogMessage(level, ctx, msg))
    }

    fun register(libVLC: LibVLC, ld: MutableLiveData<VLCLogMessage>) {
        onLog = ld::postValue
        VLCExt.setLoggerCallback(libVLC, this)
    }

    fun register(libVLC: LibVLC, @WorkerThread onLog: (VLCLogMessage)->Unit) {
        this.onLog = onLog
        VLCExt.setLoggerCallback(libVLC, this)
    }

    fun unregister(libVLC: LibVLC) {
        onLog = null
        VLCExt.setLoggerCallback(libVLC, null)
    }


}
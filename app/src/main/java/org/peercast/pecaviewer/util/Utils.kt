package org.peercast.pecaviewer.util

import android.content.res.Resources
import android.os.Build
import kotlinx.coroutines.suspendCancellableCoroutine
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.io.FileNotFoundException
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException



/**
 * ソケット関連のIOExceptionからローカライズされたOSのエラーメッセージを得る。
 * @see FileNotFoundException
 * @see SocketTimeoutException
 * @see UnknownHostException
 * */
fun IOException.localizedSystemMessage(): String {
    val name = when (this) {
        is FileNotFoundException -> "httpErrorFileNotFound"
        is SocketTimeoutException -> "httpErrorTimeout"
        is UnknownHostException -> "httpErrorLookup"
        else -> null
    }
    return name?.run {
        val res = Resources.getSystem()
        val id = res.getIdentifier(name, "string", "android")
        res.getString(id)
    } ?: localizedMessage ?: message ?: toString()
}



val isAndroidLollipop: Boolean
    get() = Build.VERSION.SDK_INT in listOf(
        Build.VERSION_CODES.LOLLIPOP,
        Build.VERSION_CODES.LOLLIPOP_MR1)



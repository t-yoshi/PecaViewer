package org.peercast.pecaviewer.chat.net2

import android.text.SpannableStringBuilder
import android.text.style.URLSpan
import androidx.core.text.HtmlCompat
import androidx.core.text.set
import okhttp3.Request
import org.peercast.pecaviewer.util.SquareUtils
import org.peercast.pecaviewer.util.runAwait
import timber.log.Timber
import java.io.IOException
import java.nio.charset.Charset
import java.text.SimpleDateFormat
import java.util.*

class BbsClient(val defaultCharset: Charset) {
    suspend fun <T : IThreadInfo> parseSubjectText(
        req: Request, delimiter: String,
        f: (path: String, title: String) -> T
    ): List<T> {
        return parseText(req, delimiter, 2) { f(it[0], it[1]) }
    }

    suspend fun <T : Any> parseText(
        req: Request, delimiter: String, limit: Int,
        f: (a: List<String>) -> T
    ): List<T> {
        return readStream(req) { lines ->
            lines.splitLines(delimiter, limit).map(f).toList()
        }
    }

    private suspend fun <T> readStream(req: Request, f: (Sequence<String>) -> T): T {
        return SquareUtils.client.newCall(req).runAwait { res ->
            if (res.code == 504)
                throw UnsatisfiableRequestException(res.message)

            val body = res.body ?: throw IOException("body returned null.")
            val cs = body.contentType()?.charset() ?: defaultCharset
            body.byteStream().reader(cs).useLines(f)
        }
    }

    /**504: ローカルキャッシュが期限切れである*/
    class UnsatisfiableRequestException(msg: String) : IOException(msg)

    suspend fun post(req: Request): String {
        return SquareUtils.client.newCall(req).runAwait { res ->
            val body = res.body ?: throw IOException("body returned null.")
            val cs = body.contentType()?.charset() ?: defaultCharset
            body.byteStream().reader(cs).readText()
        }
    }

    companion object {
        private fun Sequence<String>.splitLines(
            delimiters: String,
            limit: Int
        ): Sequence<List<String>> {
            return mapIndexedNotNull { i, line ->
                line.split(delimiters, limit = limit).let { a ->
                    if (a.size == limit)
                        return@let a
                    Timber.e("limit is $limit but ${a.size}. #${i + 1}: $line")
                    null
                }
            }
        }
    }
}


abstract class BaseBbsBoardInfo(val extras: Map<String, String>) : IBoardInfo {
    override fun hashCode(): Int {
        return javaClass.hashCode() * 31 + url.hashCode()
    }

    override fun equals(other: Any?): Boolean {
        return other is BaseBbsBoardInfo &&
                other.url == url
    }
}


abstract class BaseBbsThreadInfo(
    datPath: String, title_: String
) : IThreadInfo {

    final override val title = title_.substringBeforeLast('(')

    //XXXX.cgi | XXXX.dat
    val number: String = datPath.substringBefore(".") //.toIntOrNull() ?: 0

    final override val creationDate = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.JAPAN).format(
        Date((number.toIntOrNull() ?: 0) * 1000L)
    )

    final override val numMessages = RE_NUM_MESSAGES.find(title_)?.groupValues?.let {
        it[1].toIntOrNull()
    } ?: 0

    override fun hashCode(): Int {
        return javaClass.hashCode() * 31 +
                board.hashCode() * 13 +
                number.hashCode()
    }

    override fun equals(other: Any?): Boolean {
        return other is BaseBbsThreadInfo &&
                other.javaClass == javaClass &&
                board == other.board &&
                number == other.number
    }

    override fun toString(): String {
        return "${javaClass.simpleName}(title='$title', number='$number', creationDate='$creationDate', numMessages=$numMessages)"
    }

    companion object {
        private val RE_NUM_MESSAGES = """\((\d+)\)$""".toRegex()
    }
}

open class BbsMessage(
    val threadInfo: IThreadInfo,
    final override val number: Int,
    final override val name: CharSequence,
    final override val mail: CharSequence,
    final override val date: CharSequence,
    body: String,
    final override val id: CharSequence
) : IMessage {

    final override val body = BbsUtils.applyUrlSpan(
        BbsUtils.stripHtml(body)
    )

    val timeInMillis = BbsUtils.parseData(date)

    override fun toString(): String {
        return "$number: ${body.take(24)}"
    }

    override fun equals(other: Any?): Boolean {
        return other is BbsMessage &&
                other.javaClass == javaClass &&
                other.threadInfo == threadInfo &&
                other.number == number
    }

    override fun hashCode(): Int {
        return threadInfo.hashCode() * 1009 + number
    }
}

object BbsUtils {
    fun stripHtml(text: String): String {
        return HtmlCompat.fromHtml(
            text, HtmlCompat.FROM_HTML_MODE_LEGACY
        ).toString()
    }


    private val RE_DATETIME_1 = """(20\d\d)/([01]?\d)/(\d\d).*(\d\d):(\d\d):(\d\d)""".toRegex()

    fun parseData(s: CharSequence, timeZone: String = "Asia/Tokyo"): Long {
        fun safeGetTimeInMillis(c: Calendar): Long {
            return try {
                c.timeInMillis
            } catch (e: IllegalArgumentException) {
                0L
            }
        }

        val cl = Calendar.getInstance(Locale.JAPAN)
        RE_DATETIME_1.find(s)?.let { ma ->
            val a = ma.groupValues.drop(1).map { it.toInt() }
            cl.set(a[0], a[1] - 1, a[2], a[3], a[4], a[5])
            cl.timeZone = TimeZone.getTimeZone(timeZone)
            //Timber.d("d=$cl")
            return safeGetTimeInMillis(cl)
        }

        return 0L
    }

    private val RE_URL = """h?ttps?://[\w\-~/_.$}{#%,:@?&|=+]+""".toRegex()

    fun applyUrlSpan(text: String): CharSequence {
        val ssb = SpannableStringBuilder(text)
        RE_URL.findAll(text).forEach { mr ->
            var u = mr.groupValues[0]
            if (u.startsWith("ttp"))
                u = "h$u"
            ssb[mr.range.first, mr.range.last + 1] = URLSpan(u)
            //Timber.d("${mr.range}: $u")
        }
        return ssb
    }
}


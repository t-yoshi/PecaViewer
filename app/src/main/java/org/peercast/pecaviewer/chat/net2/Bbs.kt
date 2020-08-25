package org.peercast.pecaviewer.chat.net2

import androidx.core.text.HtmlCompat
import okhttp3.Request
import org.koin.core.KoinComponent
import org.koin.core.get
import org.peercast.pecaviewer.util.DateUtils
import org.peercast.pecaviewer.util.ISquareHolder
import org.peercast.pecaviewer.util.runAwait
import org.unbescape.html.HtmlEscape
import timber.log.Timber
import java.io.IOException
import java.nio.charset.Charset
import java.text.SimpleDateFormat
import java.util.*

class BbsClient(val defaultCharset: Charset) : KoinComponent {
    protected val square = get<ISquareHolder>()

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
        return square.okHttpClient.newCall(req).runAwait { res ->
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
        val ret = square.okHttpClient.newCall(req).runAwait { res ->
            val body = res.body ?: throw IOException("body returned null.")
            val cs = body.contentType()?.charset() ?: defaultCharset
            body.byteStream().reader(cs).readText()
        }
        return BbsUtils.stripHtml(ret).trim().replace(RE_SPACE, " ")
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

        private val RE_SPACE = """[\s　]+""".toRegex()
    }
}


abstract class BaseBbsBoardInfo : IBoardInfo {
    override fun hashCode(): Int {
        return javaClass.hashCode() * 31 + url.hashCode()
    }

    override fun equals(other: Any?): Boolean {
        return other is BaseBbsBoardInfo &&
                other.javaClass == javaClass &&
                other.url == url
    }
}


abstract class BaseBbsThreadInfo(
    datPath: String, title_: String
) : IThreadInfo {

    final override val title = HtmlEscape.unescapeHtml(
        title_.substringBeforeLast('(')
    ).trimEnd()

    //XXXX.cgi | XXXX.dat
    val number: String = datPath.substringBefore(".") //.toIntOrNull() ?: 0

    final override val creationDate = synchronized(DATE_FORMAT) {
        DATE_FORMAT.format(
            Date((number.toIntOrNull() ?: 0) * 1000L)
        )
    }

    //var: レス取得時に変更できるように
    final override var numMessages = RE_NUM_MESSAGES.find(title_)?.groupValues?.let {
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
        private val DATE_FORMAT = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.JAPAN)
    }
}

open class BbsMessage(
    final override val threadInfo: IThreadInfo,
    final override val number: Int,
    name: String,
    mail: String,
    final override val date: CharSequence,
    body: String,
    final override val id: CharSequence
) : IMessage, IBrowsable {

    final override val name: CharSequence = HtmlEscape.unescapeHtml(name)
    final override val mail: CharSequence = HtmlEscape.unescapeHtml(mail)
    final override val body: CharSequence = BbsUtils.stripHtml(body)

    override val url = "${threadInfo.url}$number"

    val timeInMillis = DateUtils.parseData(date)

    override fun toString(): String {
        return "$number: ${body.take(24)}"
    }

    override fun equals(other: Any?): Boolean {
        return other is BbsMessage &&
                other.javaClass == javaClass &&
                other.url == url
    }

    override fun hashCode(): Int {
        return javaClass.hashCode() * 31 + url.hashCode()
    }
}

object BbsUtils {
    private val RE_REMOVE_TAG = """(?is)<(script|style)[ >].+?</\1>""".toRegex()

    fun stripHtml(text: String): String {
        return HtmlCompat.fromHtml(
            RE_REMOVE_TAG.replace(text, ""),
            HtmlCompat.FROM_HTML_MODE_LEGACY
        ).toString()
    }


}


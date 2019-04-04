package org.peercast.pecaviewer.chat.net

import okhttp3.FormBody
import okhttp3.RequestBody
import okhttp3.ResponseBody
import org.peercast.pecaviewer.util.SquareUtils
import org.peercast.pecaviewer.util.exAwait
import retrofit2.Call
import retrofit2.Converter
import retrofit2.Retrofit
import retrofit2.http.*
import java.io.IOException
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import java.net.URLEncoder
import java.nio.charset.Charset
import java.util.*
import kotlin.collections.set


class ZeroChannelBoardInfo(
    private val m: Map<String, String>
) : ChatConnection.Info {
    override lateinit var browseableUrl: String
    override val title: String = m["BBS_TITLE"] ?: "??"
}

//NOTE: UAはMozilla/5.0をつけること

interface ZeroChannelApi {
    @GET("{board}/SETTING.TXT")
    fun loadBoardInfo(
        @Path("board") board: String
    ): Call<ZeroChannelBoardInfo>

    @GET("{board}/subject.txt")
    fun loadThreadInfo(
        @Path("board") board: String
    ): Call<List<SimpleBbsThreadInfo>>

    @GET("{board}/dat/{threadNumber}.dat")
    fun loadDat(
        @Path("board") board: String,
        @Path("threadNumber") threadNumber: Int
    ): Call<List<MessageBody>>

    @POST("test/bbs.cgi")
    fun rawPost(
        @HeaderMap headers: Map<String, String>,
        @Body body: RequestBody
    ): Call<ResponseBody>
}


private object ZeroChannelConverterFactory : Converter.Factory() {
    private object BoardInfoConverter : Converter<ResponseBody, ZeroChannelBoardInfo> {
        override fun convert(body: ResponseBody): ZeroChannelBoardInfo {
            val m = HashMap<String, String>(15)
            return body.sjisLines { lines ->
                lines.forEach { line ->
                    line.split("<>", limit = 2).let { a ->
                        if (a.size == 2)
                            m[a[0]] = a[1]
                    }
                }
                ZeroChannelBoardInfo(m)
            }
        }
    }

    object SubjectTxtConverter :
        Converter<ResponseBody, List<SimpleBbsThreadInfo>> {
        override fun convert(body: ResponseBody): List<SimpleBbsThreadInfo> {
            return body.sjisLines { lines ->
                lines.mapNotNull { line ->
                    line.split("<>", limit = 2).let {
                        //path, title
                        when (it.size) {
                            2 -> SimpleBbsThreadInfo(it[0], it[1])
                            else -> null
                        }
                    }
                }.toList()
            }
        }
    }


    private val SHIFT_JIS = Charset.forName("shift-jis")

    fun <T> ResponseBody.sjisLines(block: (Sequence<String>) -> T): T {
        val charset = contentType()?.charset(SHIFT_JIS) ?: SHIFT_JIS
        return use {
            block(byteStream().reader(charset).buffered().lineSequence())
        }
    }

    object MessageConverter : Converter<ResponseBody, List<MessageBody>> {
        override fun convert(body: ResponseBody): List<MessageBody> {
            return body.sjisLines { lines ->
                lines.mapIndexedNotNull { index, line ->
                    line.split("<>").let { a ->
                        when {
                            a.size >= 5 -> {
                                MessageBody(
                                    index + 1, a[0], a[1], a[2], a[3], a[4]
                                )
                            }
                            else -> null
                        }
                    }
                }.toList()
            }

        }
    }


    override fun responseBodyConverter(
        type: Type, annotations: Array<out Annotation>,
        retrofit: Retrofit
    ): Converter<ResponseBody, *>? {

        if (type == ZeroChannelBoardInfo::class.java)
            return BoardInfoConverter

        if (type is ParameterizedType && type.rawType === List::class.java) {
            if (type.actualTypeArguments.contains(SimpleBbsThreadInfo::class.java))
                return SubjectTxtConverter
            if (type.actualTypeArguments.contains(MessageBody::class.java))
                return MessageConverter
        }

        return null
    }

}


class ZeroChannelBbsConnection private constructor(
    val baseUrl: String,
    val board: String
) : ChatConnection {
    override lateinit var baseInfo: ZeroChannelBoardInfo
    override lateinit var threadConnections: List<ZeroChannelBbsThreadConnection>

    val api: ZeroChannelApi = SquareUtils.retrofitBuilder()
        .baseUrl(baseUrl)
        .addConverterFactory(ZeroChannelConverterFactory)
        .build()
        .create(ZeroChannelApi::class.java)

    private suspend fun initLoad(): ZeroChannelBbsConnection {
        baseInfo =
            api.loadBoardInfo(board).exAwait().body() ?: throw ChatConnectionException("failed loadBoardInfo: $baseUrl")
        baseInfo.browseableUrl = "$baseUrl$board/"

        threadConnections = api.loadThreadInfo(board).exAwait().body()?.let {
            it.map { ti ->
                ti.browseableUrl = "$baseUrl/test/read.cgi/$board/${ti.number}/"
                ZeroChannelBbsThreadConnection(this, ti)
            }
        } ?: emptyList()
        return this
    }

    class Factory : ChatConnection.Factory {
        override suspend fun create(url: String): ChatConnection {
            val (baseUrl, board, threadNumber) = parseUrl(url)
            try {
                val baseConn = ZeroChannelBbsConnection(baseUrl, board).initLoad()
                threadNumber.toIntOrNull()?.let { n ->
                    baseConn.threadConnections.forEach { threadConn ->
                        if (threadConn.threadInfo.number == n)
                            return threadConn
                    }
                }
                return baseConn
            } catch (e: IOException) {
                throw ChatConnectionException("initLoad failed", e)
            }
        }

        //[baseUrl, boardName, threadNumber or ""]
        private fun parseUrl(url: String): List<String> {
            RE_URL_1.find(url)?.groupValues?.let {
                return it.drop(1)
            }
            RE_URL_2.find(url)?.groupValues?.let {
                return it.drop(1) + ""
            }
            throw ChatConnectionException("ZeroChannel: (invalid url= $url)")
        }
    }

    companion object {
        //[baseUrl, boardName, threadNumber]
        private val RE_URL_1 = """^(https?://.+/)test/read\.cgi/(\w+)/(\d+)/""".toRegex()
        //[baseUrl, boardName]
        private val RE_URL_2 = """^(https?://.+/)(?:[^/]+/)*(\w+)/$""".toRegex()
    }

}

class ZeroChannelBbsThreadConnection(
    private val base: ZeroChannelBbsConnection,
    override val threadInfo: SimpleBbsThreadInfo
) : ChatThreadConnection, ChatConnection by base {

    override val isPostable = true

    override suspend fun loadMessageLatest(requestSize: Int): List<MessageBody> {
        return base.api.loadDat(base.board, threadInfo.number).exAwait().body() ?: emptyList()
    }

    override suspend fun loadMessageRange(start: Int, size: Int): List<MessageBody> {
        if (start < 0)
            throw IllegalArgumentException("start < 0")
        if (size == 0)
            return emptyList()
        val m = loadMessageLatest(1000)
        if (start >= m.size)
            return emptyList()
        if (size < 0)
            return m.drop(start)
        return m.drop(start).take(size)
    }

    override suspend fun postMessage(message: PostMessage): ResponseBody? {
        //bbs=[BOARD]&time=[POST_TIME]&FROM=[POST_NAME]&mail=[POST_MAIL]&MESSAGE=[POST_MESSAGE]
        fun sjis(s: String) = URLEncoder.encode(s, "shift-jis")

        val body = FormBody.Builder()
            .addEncoded("bbs", base.board)
            .addEncoded("time", "${System.currentTimeMillis() / 1000L}")
            .addEncoded("FROM", sjis(message.name))
            .addEncoded("mail", sjis(message.mail))
            .addEncoded("MESSAGE", sjis(message.body))
            .addEncoded("key", threadInfo.number.toString())
            .addEncoded("submit", sjis("書き込む"))
            .build()
        val headers = mapOf(
            "Cookie" to "NAME=\"${sjis(message.name)}\"; MAIL=\"${sjis(message.mail)}\"",
            "Referer" to "${base.baseUrl}${base.board}/",
            "User-Agent" to "Mozilla/5.0"
        )
        return base.api.rawPost(headers, body).exAwait().body()
    }


}


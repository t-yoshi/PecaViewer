package org.peercast.pecaviewer.chat.net


import okhttp3.FormBody
import okhttp3.RequestBody
import okhttp3.ResponseBody
import org.koin.core.KoinComponent
import org.peercast.pecaviewer.util.SquareUtils
import org.peercast.pecaviewer.util.exAwait
import retrofit2.Call
import retrofit2.Converter
import retrofit2.Retrofit
import retrofit2.http.*
import timber.log.Timber
import java.io.IOException
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import java.net.URLEncoder
import java.nio.charset.Charset
import java.util.*
import kotlin.collections.set


private const val BASE_URL = "https://jbbs.shitaraba.net/"
private val EUC_JP: Charset = Charset.forName("euc-jp")


class ShitarabaBoardInfo(m: Map<String, String>) : ChatConnection.Info {
    override val browseableUrl = m["TOP"] ?: ""
    val dir = m["DIR"] ?: "" //=game
    val bbsNumber = m["BBS"] ?: "" //=59608
    val categoryName = m["CATEGORY"] ?: ""  //=ゲーム/囲碁/将棋
    val isAdult = m["BBS_ADULT"] != "0" //=0
    val numThreadStop = m["BBS_THREAD_STOP"]?.toIntOrNull() ?: 0 //=1000
    val nonameName = m["BBS_NONAME_NAME"] ?: ""  //=俺より強い名無しに会いにいく＠転載禁止
    val deleteName = m["BBS_DELETE_NAME"] ?: ""  //=＜削除＞
    override val title = m["BBS_TITLE"] ?: ""  //=ウメハラ総合板四代目(転載禁止)
    val comment = m["BBS_COMMENT"] ?: ""  //=ウメスレ
    val error = m["ERROR"] ?: ""

    override fun toString(): String {
        return "ShitarabaBoardInfo(dir='$dir', bbsNumber='$bbsNumber', categoryName='$categoryName', isAdult=$isAdult, numThreadStop=$numThreadStop, nonameName='$nonameName', deleteName='$deleteName', title='$title', comment='$comment', error='$error')"
    }
}


interface ShitarabaApi {
    //https://jbbs.shitaraba.net/bbs/api/setting.cgi/game/59608/
    @GET("bbs/api/setting.cgi/{boardDir}/{boardNumber}/")
    fun loadBoardInfo(
        @Path("boardDir") boardDir: String,
        @Path("boardNumber") boardNumber: String
    ): Call<ShitarabaBoardInfo>

    @GET("{boardDir}/{boardNumber}/subject.txt")
    fun loadThreadInfo(
        @Path("boardDir") boardDir: String,
        @Path("boardNumber") boardNumber: String
    ): Call<List<SimpleBbsThreadInfo>>

    @GET("bbs/rawmode.cgi/{boardDir}/{boardNumber}/{threadNumber}/{resuNumberStart}-{resuNumberEnd}")
    fun loadMessageRange(
        @Path("boardDir") boardDir: String,
        @Path("boardNumber") boardNumber: String,
        @Path("threadNumber") threadNumber: String,
        @Path("resuNumberStart") resuNumberStart: String = "1",
        @Path("resuNumberEnd") resuNumberEnd: String = ""
    ): Call<List<MessageBody>>

    @GET("bbs/rawmode.cgi/{boardDir}/{boardNumber}/{threadNumber}/l{length}")
    fun loadMessageLatest(
        @Path("boardDir") boardDir: String,
        @Path("boardNumber") boardNumber: String,
        @Path("threadNumber") threadNumber: String,
        @Path("length") length: Int
    ): Call<List<MessageBody>>

    @POST("bbs/write.cgi")
    fun rawPost(
        @HeaderMap headers: Map<String, String>,
        @Body body: RequestBody
    ): Call<ResponseBody>

}

suspend fun ShitarabaApi.post(
    board: ShitarabaBoardInfo,
    thread: SimpleBbsThreadInfo,
    name: String, mail: String, message: String
): ResponseBody? {
    fun eucjp(s: String) = URLEncoder.encode(s, "euc-jp")

    val headers = mapOf(
        "Cookie" to "name=${eucjp(name)}; mail=${eucjp(mail)}",
        "Referer" to "$BASE_URL${board.dir}/${board.bbsNumber}/"
    )

    val body = FormBody.Builder()
        .addEncoded("DIR", board.dir)
        .addEncoded("BBS", board.bbsNumber)
        .addEncoded("KEY", thread.number.toString())
        .addEncoded("NAME", eucjp(name))
        .addEncoded("MAIL", eucjp(mail))
        .addEncoded("MESSAGE", eucjp(message))
        .addEncoded("SUBMIT", eucjp("書き込む"))
        .build()

    return rawPost(headers, body).exAwait().let {
        it.body() ?: it.errorBody()
    }
}

private fun <R> ResponseBody.useEucJpLines(block: (lines: Sequence<String>) -> R): R {
    return use {
        block(it.byteStream().bufferedReader(EUC_JP).lineSequence())
    }
}


private object ShitarabaConverterFactory : Converter.Factory() {
    private object SettingConverter : Converter<ResponseBody, ShitarabaBoardInfo> {
        override fun convert(body: ResponseBody): ShitarabaBoardInfo {
            val m = HashMap<String, String>(15)
            body.useEucJpLines { lines ->
                lines.forEach { line ->
                    line.split('=', limit = 2).let { a ->
                        if (a.size == 2)
                            m[a[0]] = a[1]
                    }
                }
            }
            return ShitarabaBoardInfo(m)
        }
    }

    object SubjectTxtConverter :
        Converter<ResponseBody, List<SimpleBbsThreadInfo>> {
        override fun convert(body: ResponseBody): List<SimpleBbsThreadInfo> {
            return body.useEucJpLines { lines ->
                lines.mapNotNull { line ->
                    line.split(",", limit = 2).let {
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

    object MessageConverter : Converter<ResponseBody, List<MessageBody>> {
        override fun convert(body: ResponseBody): List<MessageBody> {
            return body.useEucJpLines { lines ->
                lines.mapNotNull { line ->
                    line.split("<>", limit = 7).let { a ->
                        when {
                            a.size >= 7 -> {
                                MessageBody(
                                    a[0].toIntOrNull() ?: 0, a[1], a[2], a[3], a[4], a[6]
                                )
                            }
                            else -> {
                                Timber.e("Parse error: $line")
                                null
                            }
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

        if (type == ShitarabaBoardInfo::class.java)
            return SettingConverter

        if (type is ParameterizedType && type.rawType === List::class.java) {
            if (type.actualTypeArguments.contains(SimpleBbsThreadInfo::class.java))
                return SubjectTxtConverter
            if (type.actualTypeArguments.contains(MessageBody::class.java))
                return MessageConverter
        }

        return null
    }
}


class ShitarabaBbsConnection(
    val boardDir: String,
    val boardNumber: String
) : ChatConnection, KoinComponent {

    override lateinit var baseInfo: ShitarabaBoardInfo
        private set
    override lateinit var threadConnections: List<ShitarabaBbsThreadConnection>
        private set

    val api: ShitarabaApi = SquareUtils.retrofitBuilder()
        .baseUrl(BASE_URL)
        .addConverterFactory(ShitarabaConverterFactory)
        .build()
        .create(ShitarabaApi::class.java)

    private suspend fun initLoad(): ShitarabaBbsConnection {
        try {
            baseInfo = api.loadBoardInfo(boardDir, boardNumber).exAwait().body()!!
            threadConnections = api.loadThreadInfo(boardDir, boardNumber).exAwait().body()?.map {
                it.browseableUrl = "${BASE_URL}bbs/read.cgi/${baseInfo.dir}/${baseInfo.bbsNumber}/${it.number}/"
                ShitarabaBbsThreadConnection(this, it)
            } ?: emptyList()
            return this
        } catch (e: IOException) {
            throw ChatConnectionException("initLoad failed", e)
        }
    }

    companion object {
        /**[boardDir, boardNumber, threadNumber]*/
        private val RE_URL =
            """^https?://jbbs\.(?:shitaraba\.net|livedoor\.jp)/(?:bbs/(?:read|subject)\.cgi/)?(\w+)/(\d+)(?:/(\d+))?""".toRegex()
    }

    class Factory : ChatConnection.Factory {
        override suspend fun create(url: String): ChatConnection {
            val a = RE_URL.find(url)?.groupValues ?: throw ChatConnectionException("not shitaraba url: $url")
            val baseConn = ShitarabaBbsConnection(a[1], a[2]).initLoad()
            a[3].toIntOrNull()?.let { threadNumber ->
                baseConn.threadConnections.forEach { threadConn ->
                    if (threadConn.threadInfo.number == threadNumber)
                        return threadConn
                }
            }
            return baseConn
        }
    }
}


class ShitarabaBbsThreadConnection(
    private val base: ShitarabaBbsConnection,
    override val threadInfo: SimpleBbsThreadInfo
) : ChatThreadConnection, ChatConnection by base {

    override val isPostable = threadInfo.messageCount < base.baseInfo.numThreadStop

    override suspend fun loadMessageLatest(requestSize: Int): List<MessageBody> {
        with(base.baseInfo) {
            return base.api.loadMessageLatest(
                dir, bbsNumber, threadInfo.number.toString(), requestSize
            ).exAwait().body() ?: emptyList()
        }
    }

    override suspend fun loadMessageRange(start: Int, size: Int): List<MessageBody> {
        if (size == 0)
            return emptyList()
        with(base.baseInfo) {
            if (start >= numThreadStop || size == 0)
                return emptyList()
            return base.api.loadMessageRange(
                dir, bbsNumber, threadInfo.number.toString(),
                "${start + 1}", if (size >= 0) "${start + size}" else ""
            ).exAwait().body() ?: emptyList()
        }
    }

    override suspend fun postMessage(message: PostMessage): ResponseBody? {
        return message.run {
            base.api.post(base.baseInfo, threadInfo, name, mail, body)
        }
    }
}
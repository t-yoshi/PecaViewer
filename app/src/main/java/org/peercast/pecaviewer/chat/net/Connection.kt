package org.peercast.pecaviewer.chat.net

import okhttp3.ResponseBody
import org.peercast.pecaviewer.util.DateUtils
import timber.log.Timber
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*

interface IBrowseable {
    /**ブラウザで開くことができるURL*/
    val browseableUrl: String
}

/**本文*/
data class MessageBody(
    /**レス番号*/
    val number: Int,
    /**名前*/
    val name: String,
    val mail: String,
    /**日付*/
    val date: String,
    /**本文*/
    val body: String,
    val id: String = ""
) {
    val timeInMillis = DateUtils.parse(date)

    override fun toString(): String {
        return "$number: ${body.take(24)}"
    }
}


data class PostMessage(
    val name: String,
    val mail: String,
    val body: String
)


class SimpleBbsThreadInfo(
    datPath: String,
    title_: String
) : ChatThreadConnection.Info {

    override lateinit var browseableUrl: String

    override val title = title_.substringBeforeLast('(')

    //XXXX.cgi | XXXX.dat
    val number: Int = datPath.substringBefore(".").toIntOrNull() ?: 0

    override val creationDate = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.JAPAN).format(
        Date(number * 1000L)
    )

    override val messageCount = RE_NUM_MESSAGES.find(title_)?.groupValues?.let {
        it[1].toIntOrNull()
    } ?: 0

    override fun toString() = "$number: $title"

    companion object {
        private val RE_NUM_MESSAGES = """\((\d+)\)$""".toRegex()
    }
}


interface ChatConnection {
    /**このチャット/掲示板の情報*/
    interface Info : IBrowseable {
        val title: String
    }

    val baseInfo: Info

    //suspend fun createThread(msg: PostMessage) : ResponseBody?

    val threadConnections: List<ChatThreadConnection>

    interface Factory {
        suspend fun create(url: String): ChatConnection
    }
}

interface ChatThreadConnection : ChatConnection {
    /**スレッドの情報*/
    interface Info : ChatConnection.Info {
        /**作成日時*/
        val creationDate: String
        /**レス数 不明(-1)*/
        val messageCount: Int
    }

    val isPostable: Boolean

    val threadInfo: Info

    /**直近のレスをrequestSizeだけ読み込む。requestSizeを無視して全て取得しても良い*/
    suspend fun loadMessageLatest(requestSize: Int): List<MessageBody>

    /**startからレスを読み込む。最初のレスは0。size=-1はすべて。*/
    suspend fun loadMessageRange(start: Int, size: Int): List<MessageBody>

    /**レスを送信する*/
    suspend fun postMessage(message: PostMessage): ResponseBody?
}


class ChatConnectionException(message: String, cause: IOException? = null) : IOException(message, cause)


/**疑似コネクション*/
class MockBbsConnection private constructor(private val url: String): ChatConnection {
    override val baseInfo = object : ChatConnection.Info{
        override val title: String = url
        override val browseableUrl = url
    }
    override val threadConnections: List<ChatThreadConnection> = listOf(
        MockBbsThreadConnection(this)
    )

    private class MockBbsThreadConnection(base: MockBbsConnection) : ChatThreadConnection, ChatConnection by base {
        override val isPostable = false
        override val threadInfo = object : ChatThreadConnection.Info {
            override val browseableUrl = base.url
            override val title = browseableUrl
            override val creationDate = ""
            override val messageCount = 1
        }

        override suspend fun loadMessageLatest(requestSize: Int): List<MessageBody> = listOf(
            MessageBody(1, "", "", "", "<a href=${baseInfo.browseableUrl}>${baseInfo.browseableUrl}", "")
        )
        override suspend fun loadMessageRange(start: Int, size: Int): List<MessageBody> {
            if (start >= 1 || size == 0)
                return emptyList()
            return loadMessageLatest(1)
        }

        override suspend fun postMessage(message: PostMessage): ResponseBody? {
            throw NotImplementedError()
        }
    }

    class Factory: ChatConnection.Factory {
        override suspend fun create(url: String): ChatConnection {
            return MockBbsConnection(url)
        }
    }
}

/**
 * 指定のURLを開き、 [ChatConnection] or [ChatThreadConnection]のいずれかを返す。
 * */
suspend fun openChatConnection(url: String) : ChatConnection {
    listOf(
        ShitarabaBbsConnection::Factory,
        StampCastConnection::Factory,
        ZeroChannelBbsConnection::Factory,
        MockBbsConnection::Factory
    ).forEach { f->
        try {
            return f().create(url)
        } catch(e: ChatConnectionException) {
            //接続エラー
            e.cause?.let {
                Timber.w(e,"${e.message}: $url")
            }
        }
    }
    throw RuntimeException("not reached")
}



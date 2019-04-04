package org.peercast.pecaviewer.chat.net

import com.squareup.moshi.Json
import okhttp3.ResponseBody
import org.peercast.pecaviewer.util.SquareUtils
import org.peercast.pecaviewer.util.exAwait
import retrofit2.Call
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

data class StampCastStamps(
    val stamps: List<StampCastStamp>
)

data class StampCastTag(
  val id: Int,
  val text: String
)

data class StampCastStamp(
    val id: Int,

    @Json(name = "is_animation") val isAnimation: Boolean,

    val name: String,

    @Json(name = "room_id") val roomId: Int,

    val tags: List<StampCastTag>,

    val thumbnail: String,

    @Json(name = "user_id") val userId: String
)

interface StampCastApi {
    //https://stamp.archsted.com/api/v1/rooms/125/stamps/guest?page=1&sort=all&tag=
    @GET("api/v1/rooms/{id}/stamps/guest")
    fun get(
        @Path("id") id: Int,
        @Query("page") page: Int = 1,
        @Query("sort") sort: String = "all",
        @Query("tag") tag: String = ""
            ): Call<StampCastStamps>
}

class StampCastConnection private constructor(private val url: String, private val id: Int): ChatConnection {
    val api : StampCastApi = SquareUtils.retrofitBuilder()
        .baseUrl("https://stamp.archsted.com/")
        .addConverterFactory(MoshiConverterFactory.create())
        .build()
        .create(StampCastApi::class.java)


    override val baseInfo = object : ChatConnection.Info {
        override val title = "StampCast (???)"
        override val browseableUrl = url
    }

    override val threadConnections: List<ChatThreadConnection> = listOf(
        StampCastPageConnection(this, 1)
    )

    class Factory() : ChatConnection.Factory  {
        override suspend fun create(url: String): ChatConnection {
            val m = RE_URL.matchEntire(url)?: throw ChatConnectionException("not stampcast url: $url")
            return StampCastConnection(url, m.groupValues[1].toInt())
        }
    }

    companion object {
        private val RE_URL = """^https?://stamp\.archsted\.com/(\d+)""".toRegex()
    }
}

private class StampCastPageConnection(
            private val base: StampCastConnection,
            private val page: Int) : ChatThreadConnection, ChatConnection by base  {
    override val isPostable: Boolean
        get() = false

    override val threadInfo = object : ChatThreadConnection.Info {
        //override val number = page
        override val title = "$page"
        override val creationDate = ""
        override val messageCount = 30
        override val browseableUrl = base.baseInfo.browseableUrl
    }

    override suspend fun loadMessageLatest(requestSize: Int): List<MessageBody> {
        return base.api.get(1).exAwait().body()?.stamps?.mapIndexed { i, stamp ->
            MessageBody(i, "", "" , "", "<img src=${stamp.thumbnail}>", "")
        } ?: emptyList()
    }

    override suspend fun loadMessageRange(start: Int, size: Int): List<MessageBody> {
        return emptyList()
    }

    override suspend fun postMessage(message: PostMessage): ResponseBody? {
        throw NotImplementedError()
    }
}
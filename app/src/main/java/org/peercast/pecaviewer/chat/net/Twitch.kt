package org.peercast.pecaviewer.chat.net

import okhttp3.ResponseBody
import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

interface TwitchApi {
    @GET("oauth2/authorize")
    fun authorize(
        @Query("client_id") client_id: String,
        @Query("response_type") response_type: String = "state",
        @Query("scope") scope: String = "chat:read",
        @Query("redirect_uri") redirect_uri: String = "http://localhost"
    ): Call<ResponseBody>


    @POST("kraken/oauth2/token")
    fun oauth2(
        @Query("client_id") client_id: String,
        @Query("client_secret") client_secret: String,
        @Query("grant_type") grant_type: String = "authorization_code",
        @Query("redirect_uri") redirect_uri: String = "http://localhost"
    ): Call<ResponseBody>

    companion object {
        const val BASE_URL = "https://id.twitch.tv/" // ""https://api.twitch.tv/"
    }
}
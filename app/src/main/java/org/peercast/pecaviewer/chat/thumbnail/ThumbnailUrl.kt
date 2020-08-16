package org.peercast.pecaviewer.chat.thumbnail

import java.util.*

sealed class ThumbnailUrl {
    abstract val imageUrl: String
    abstract val linkUrl: String


    data class Default(
        override val imageUrl: String,
        override val linkUrl: String = ""
    ) : ThumbnailUrl() {
        companion object {
            fun create(u : String) : Default {
                return if (u.startsWith("ttp"))
                    Default("h$u")
                else
                    Default(u)
            }
        }
    }

    data class YouTube(
        val id : String
    ) : ThumbnailUrl() {
        override val imageUrl = "https://i.ytimg.com/vi/$id/default.jpg"
        override val linkUrl = "https://youtu.be/$id"
    }

    data class NicoVideo(
        val videoId : String //sm12345
    ) : ThumbnailUrl() {
        override val imageUrl = "https://ext.nicovideo.jp/api/getthumbinfo/$videoId"
        override val linkUrl = "https://nico.ms/$videoId"
    }

    companion object {
        private fun Regex.findImageUrl(s: CharSequence, out: MutableMap<Int, ThumbnailUrl>, creator: (List<String>)-> ThumbnailUrl){
            //Timber.d("> $this $s")
            findAll(s).forEach { m->
                out[m.range.first] = creator(m.groupValues)
            }
        }

        fun parse(s: CharSequence) : List<ThumbnailUrl> {
            val m = TreeMap<Int, ThumbnailUrl>()
            RE_YOUTUBE_URL_1.findImageUrl(s, m){
                YouTube(it[1])
            }
            RE_YOUTUBE_URL_2.findImageUrl(s, m){
                YouTube(it[1])
            }
            RE_NICO_URL.findImageUrl(s, m){
                NicoVideo(it[2])
            }
            RE_GENERAL_IMAGE_URL.findImageUrl(s, m){
                Default.create(
                    it[0]
                )
            }
            RE_IMGUR_URL.findImageUrl(s, m){
                Default.create(
                    it[0]
                )
            }
            return m.values.toList()
        }


        private val RE_YOUTUBE_URL_1 =
            """\b(?:www\.)?youtube\.com/.+?v=([\w_\-]+)""".toRegex()
        private val RE_YOUTUBE_URL_2 =
            """\byoutu\.be/([\w_\-]+)""".toRegex()

        private val RE_NICO_URL =
            """\b(www\.nicovideo\.jp/watch|nico\.ms)/((sm|nm|)(\d{1,10}))\b""".toRegex()

        private val RE_GENERAL_IMAGE_URL =
            """\bh?ttps?://.+\.(png|jpe?g|gif)\b""".toRegex()
        //TODO |webp バージョンによってはGlideでロード例外が起きる


        private val RE_IMGUR_URL =
            """\bh?ttps?://[\w.]+\.imgur\.com/(\w+)(_d)?(\.(png|jpe?g|gif))?""".toRegex(RegexOption.IGNORE_CASE)


    }

}


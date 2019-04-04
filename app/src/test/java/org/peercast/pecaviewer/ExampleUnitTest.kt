package org.peercast.pecaviewer

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.peercast.pecaviewer.chat.net.TwitchApi
import org.peercast.pecaviewer.chat.net.openChatConnection
import org.peercast.pecaviewer.util.SquareUtils
import timber.log.Timber

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
class ExampleUnitTest {
    init {
        Timber.plant(object : Timber.Tree(){
            override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
                println( message )
                t?.printStackTrace()
            }
        })
    }

    //http://hibino.ddo.jp/bbs/test/read.cgi/peca/1552237443/
    //https://jbbs.shitaraba.net/bbs/rawmode.cgi/game/59608/1552136407/l50
    //http://hibino.ddo.jp/bbs/peca/head.txt
    //http://hibino.ddo.jp/bbs/peca/subject.txt
    //http://hibino.ddo.jp/bbs/peca/dat/1552669728.dat
    @Test
    fun addition_isCorrect() {
        assertEquals(4, 2 + 2)
    }

    @Test
    fun twitchTest(){
        //5v6vr0x4fhtgc0fvm0xzlsayu108uj
        //hf6j3jt76a75mcietp2qdnb7rro3gn
        val api = SquareUtils.retrofitBuilder()
            .baseUrl(TwitchApi.BASE_URL)
            .build().create(TwitchApi::class.java)

        api.authorize("5v6vr0x4fhtgc0fvm0xzlsayu108uj").execute().let {
            println(it.message())
        }

//        TwirkBuilder("ichicostars", "NoName", "5v6vr0x4fhtgc0fvm0xzlsayu108uj")
//
//            .build()
//            .connect()
    }

    @Test
    fun bbsTest() {

        runBlocking {
            val u1 = "http://2chcrew.geo.jp/ze/test/read.cgi/tete/1468154452/1-100"
            val u2 = "http://hibino.ddo.jp/bbs/test/read.cgi/peca/1549381106/l50"
            val u3 = "http://hibino.ddo.jp/bbs/peca/test/read.cgi/peca/1553274401/"
            val u4 = "https://stamp.archsted.com/125"
            val u5 = "http://peercast.s602.xrea.com/test/read.cgi/bbs/1472558865/l50"

            val conn = openChatConnection(u5)
            conn.threadConnections.forEach {
                println(it.threadInfo)
            }
            conn.threadConnections.first().let {
                it.loadMessageLatest(100).forEach {
                    println(it)
                }

//                it.postMessage(PostMessage("のら", "mail", "てすと")).let {
//                    println(it?.string())
//                }
            }
        }


    }
}

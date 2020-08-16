package org.peercast.pecaviewer.chat.adapter

import android.text.SpannableStringBuilder
import android.text.style.URLSpan
import androidx.core.text.HtmlCompat
import androidx.core.text.set
import androidx.databinding.ObservableField
import org.peercast.pecaviewer.BuildConfig
import org.peercast.pecaviewer.chat.net2.BbsMessage
import org.peercast.pecaviewer.chat.net2.IMessage
import org.peercast.pecaviewer.chat.thumbnail.ThumbnailUrl
import org.peercast.pecaviewer.util.DateUtils
import kotlin.math.min
import kotlin.random.Random

class MessageViewModel {
    /**レス番号*/
    val number = ObservableField<CharSequence>()

    /**名前*/
    val name = ObservableField<CharSequence>()

    /**日付*/
    val date = ObservableField<CharSequence>()

    /**本文*/
    val body = ObservableField<CharSequence>()

    val elapsedTime = ObservableField<CharSequence>()

    val thumbnails = ObservableField<List<ThumbnailUrl>>()

    fun setMessage(m: IMessage, isShowElapsedTime: Boolean = true) {
        number.set("${m.number}")
        name.set(m.name)
        date.set(m.date)

        val ssbBody = SpannableStringBuilder(m.body.trimEnd())
        //アンカーでポップアップ
        PopupSpan.applyForAnchor(ssbBody)

        if (isShowElapsedTime && m is BbsMessage && m.timeInMillis > 0) {
            val et = DateUtils.formatElapsedTime(System.currentTimeMillis() - m.timeInMillis)
            elapsedTime.set(et)
            // elapsedTimeのぶん、末尾を空けておく
            ssbBody.append(NBSP.repeat(min(et.width + 3, 3)))
        } else {
            elapsedTime.set("")
        }

        val urls = if (BuildConfig.DEBUG){
            val r = Random(ssbBody.hashCode())
            ThumbnailUrl.parse(ssbBody.toString() + TEST_TEXT).let {
                it.subList(0, r.nextInt(it.size))
            }
        } else {
            ThumbnailUrl.parse(ssbBody)
        }
        thumbnails.set(urls.take(32))

        applyUrlSpan(ssbBody)
        body.set(ssbBody)
    }

    private val CharSequence.width: Int
        get() {
            return length + RE_LETTER.replace(this, "").length
        }

    companion object {
        private val NBSP = HtmlCompat.fromHtml("&nbsp;", HtmlCompat.FROM_HTML_MODE_COMPACT)
        private val RE_LETTER = """[\da-zA-Z ]""".toRegex()
        private val RE_URL = """h?ttps?://[\w\-~/_.$}{#%,:@?&|=+]+""".toRegex()

        //URLSpanを適用する
        private fun applyUrlSpan(ssb: SpannableStringBuilder): SpannableStringBuilder {
            RE_URL.findAll(ssb).forEach { mr ->
                var u = mr.groupValues[0]
                if (u.startsWith("ttp"))
                    u = "h$u"
                ssb[mr.range.first, mr.range.last + 1] = URLSpan(u)
                //Timber.d("${mr.range}: $u")
            }
            return ssb
        }

        private const val TEST_TEXT = """
             "https://media2.giphy.com/media/xreCEnteawblu/giphy.gif?cid=ecf05e47scyg0bt1ljd58r7kj4xkcifs4x5c92pf5bwfhygv&rid=giphy.gif"),
            "https://i.giphy.com/media/2igz2N2bac1Wg/giphy.webp"),
            ttps://i.pinimg.com/originals/a7/dc/70/a7dc706832d1f818a3cb9d2202eb25cf.gif"),
            ("https://upload.wikimedia.org/wikipedia/commons/9/9a/PNG_transparency_demonstration_2.png"),
            https://www.youtube.com/watch?v=DsYdPQ1igvM
            https://www.nicovideo.jp/watch/sm9
            https://file-examples-com.github.io/uploads/2017/10/file_example_JPG_1MB.jpg
        """
    }

}

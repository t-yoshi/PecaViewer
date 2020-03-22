package org.peercast.pecaviewer.chat.adapter

import android.text.SpannableStringBuilder
import android.text.style.URLSpan
import androidx.core.text.HtmlCompat
import androidx.core.text.set
import androidx.databinding.BaseObservable
import androidx.databinding.ObservableField
import org.peercast.pecaviewer.chat.net2.BbsMessage
import org.peercast.pecaviewer.chat.net2.IMessage
import org.peercast.pecaviewer.util.DateUtils
import kotlin.math.min

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
    }

}
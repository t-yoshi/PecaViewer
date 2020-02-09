package org.peercast.pecaviewer.chat.adapter

import android.text.SpannableStringBuilder
import androidx.core.text.HtmlCompat
import androidx.databinding.BaseObservable
import org.peercast.pecaviewer.chat.net2.BbsMessage
import org.peercast.pecaviewer.chat.net2.IMessage
import org.peercast.pecaviewer.util.DateUtils

class ListItemViewModel : BaseObservable() {
    /**レス番号*/
    var number: CharSequence = ""
        private set

    /**名前*/
    var name: CharSequence = ""
        private set

    /**日付*/
    var date: CharSequence = ""
        private set

    /**本文*/
    var body: CharSequence = ""
        private set

    var elapsedTime: CharSequence = ""

    fun setMessage(m: IMessage) {
        number = "" + m.number
        name = m.name
        date = m.date
        elapsedTime = ""

        if (m is BbsMessage && m.timeInMillis > 0) {
            elapsedTime = DateUtils.formatElapsedTime(System.currentTimeMillis() - m.timeInMillis)
        }

        val ssbBody = SpannableStringBuilder(m.body.trimEnd())
        //アンカーでポップアップ
        PopupSpan.applyForAnchor(ssbBody)

        // elapsedTimeのぶん、末尾を空けておく
        ssbBody.append(NBSP.repeat(elapsedTime.width + 3))
        body = ssbBody
    }

    private val CharSequence.width: Int
        get() {
            return length + """[\da-zA-Z ]""".toRegex().replace(this, "").length
        }

    companion object {
        private val NBSP = HtmlCompat.fromHtml("&nbsp;", HtmlCompat.FROM_HTML_MODE_COMPACT)
        private val RE_ANCHOR = """>>(\d+)""".toRegex()
    }

}

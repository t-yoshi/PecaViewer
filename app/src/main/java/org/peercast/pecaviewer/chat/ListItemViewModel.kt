package org.peercast.pecaviewer.chat

import androidx.core.text.HtmlCompat
import androidx.databinding.BaseObservable
import org.peercast.pecaviewer.chat.net.MessageBody
import org.peercast.pecaviewer.util.DateUtils

class ListItemViewModel : BaseObservable() {
    var isLast: Boolean = false

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

    //簡易表示なので>>アンカーへのリンクもしない
    fun setMessage(m: MessageBody) {
        number = "" + m.number
        name = m.name
        date = m.date
        elapsedTime = ""

        if (m.timeInMillis > 0){
            elapsedTime = DateUtils.formatElapsedTime( System.currentTimeMillis() - m.timeInMillis)
        }

        // elapsedTimeのぶん、末尾を空けておく
        body = HtmlCompat.fromHtml(
            m.body.trimEnd() + "&nbsp;".repeat(elapsedTime.width + 3),
            HtmlCompat.FROM_HTML_MODE_LEGACY
        ).toString()
    }

    private val CharSequence.width: Int get() {
        return length + """[\da-zA-Z ]""".toRegex().replace(this, "").length
    }


}

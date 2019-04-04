package org.peercast.pecaviewer.chat

import androidx.core.text.HtmlCompat
import androidx.databinding.BaseObservable
import org.peercast.pecaviewer.chat.net.MessageBody

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

    fun setMessage(m: MessageBody) {
        number = "" + m.number
        name = m.name
        date = m.date
        //簡易表示なので>>アンカーへのリンクもしない
        body = HtmlCompat.fromHtml(m.body, HtmlCompat.FROM_HTML_MODE_LEGACY).toString()
    }


}

package org.peercast.pecaviewer.chat.adapter

import android.text.Spannable
import android.text.SpannableStringBuilder
import androidx.core.text.getSpans
import androidx.databinding.ObservableField
import org.peercast.pecaviewer.chat.net2.BbsMessage
import org.peercast.pecaviewer.chat.net2.IMessage
import org.peercast.pecaviewer.chat.thumbnail.ThumbnailSpan
import org.peercast.pecaviewer.chat.thumbnail.ThumbnailUrl
import org.peercast.pecaviewer.util.DateUtils

class MessageViewModel {
    /**レス番号*/
    val number = ObservableField<CharSequence>()

    /**名前*/
    val name = ObservableField<CharSequence>()

    /**日付*/
    val date = ObservableField<CharSequence>()

    val id = ObservableField<CharSequence>()

    /**本文*/
    val body = ObservableField<CharSequence>()

    val elapsedTime = ObservableField<CharSequence>()

    val thumbnails = ObservableField<List<ThumbnailUrl>>()

    fun setMessage(m: IMessage, isShowElapsedTime: Boolean = true) {
        number.set("${m.number}")
        name.set(m.name)
        date.set(m.date)
        id.set(m.id)

        (m.body as? Spannable)?.let {s->
            thumbnails.set(s.getSpans<ThumbnailSpan>().take(32).map { it.url })
        }

        if (isShowElapsedTime && m is BbsMessage && m.timeInMillis > 0) {
            val et = DateUtils.formatElapsedTime(System.currentTimeMillis() - m.timeInMillis)
            elapsedTime.set(et)
            // elapsedTimeのぶん、末尾を空けておく
            val sbBody = SpannableStringBuilder(m.body.trimEnd())
            sbBody.append(NBSP.repeat(et.width + 2))
            body.set(sbBody)
        } else {
            elapsedTime.set("")
            body.set(m.body)
        }
    }

    private val CharSequence.width: Int
        get() {
            return length + RE_LETTER.replace(this, "").length
        }

    companion object {
        private const val NBSP = "\u00a0"
        private val RE_LETTER = """[\da-zA-Z ]""".toRegex()
    }

}

package org.peercast.pecaviewer.chat.adapter

import android.text.SpannableStringBuilder
import android.text.style.ClickableSpan
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.PopupWindow
import androidx.core.text.set
import androidx.recyclerview.widget.RecyclerView
import org.peercast.pecaviewer.R

/**
アンカーをクリックしてポップアップ
*/
class PopupSpan private constructor(private val resNumber: Int) : ClickableSpan() {
    override fun onClick(widget: View) {
        //Timber.d("--> #$resNumber $widget")
        val c = widget.context
        val rv = findParentRecyclerView(widget) ?: return
        val adapter = rv.adapter as? SupportAdapter ?: return
        val view = adapter.createViewForPopupWindow(resNumber, rv) ?: return
        val bg = c.getDrawable(R.drawable.bbs_message_popup_bg)

        PopupWindow(
            view, rv.width,
            WindowManager.LayoutParams.WRAP_CONTENT, true
        ).also {
            it.setBackgroundDrawable(bg)
            it.isOutsideTouchable = true
        }.showAsDropDown(widget, 0, 0)
    }

    interface SupportAdapter {
        /**該当レス番号を表示するcontentViewを作成する。不可ならnull*/
        fun createViewForPopupWindow(resNumber: Int, parent: ViewGroup): View?
    }

    companion object {
        private val RE_ANCHOR = """[>＞]{2}([1-9]\d{0,3})""".toRegex()

        /**
         * テキスト内のアンカーにPopupSpanを適用する
         */
        fun applyForAnchor(ssb: SpannableStringBuilder): SpannableStringBuilder {
            RE_ANCHOR.findAll(ssb).forEach { mr ->
                ssb[mr.range.first, mr.range.last + 1] =
                    PopupSpan(mr.groupValues[1].toInt())
            }
            return ssb
        }

        private fun findParentRecyclerView(widget: View): RecyclerView? {
            var w = widget.parent
            while (w != null) {
                if (w is RecyclerView)
                    return w
                w = w.parent
            }
            return null
        }
    }
}
package org.peercast.pecaviewer.chat.adapter

import android.graphics.Bitmap
import android.net.Uri
import android.view.*
import android.widget.ImageView
import android.widget.PopupWindow
import androidx.recyclerview.widget.RecyclerView
import org.koin.core.KoinComponent
import org.peercast.pecaviewer.R
import org.peercast.pecaviewer.databinding.ThumbnailContentBinding
import java.lang.ref.WeakReference
import kotlin.properties.Delegates

class ThumbnailAdapter : RecyclerView.Adapter<ThumbnailAdapter.ViewHolder>() {

    var imageUrls by Delegates.observable(emptyList<Uri>()) { _, old, new ->
        if (old != new)
            notifyDataSetChanged()
    }

    class ViewHolder(private val binding: ThumbnailContentBinding) :
        RecyclerView.ViewHolder(binding.root), KoinComponent {
        private val viewModel = ThumbnailViewModel()

        init {
            binding.vm = viewModel
        }

        fun bind(u: Uri) {
            val presenter = when {
                anyFound(u, RE_YOUTUBE_URL_1, RE_YOUTUBE_URL_2) -> {
                    ThumbnailViewModel.YouTubePresenter(viewModel, u, u.toYouTubeThumbnail())
                }
                anyFound(u, RE_NICO_URL) -> {
                    ThumbnailViewModel.NicoPresenter(viewModel, u, u.toNicoVideoId())
                }
                else -> {
                    val weakItemView = WeakReference(itemView)
                    ThumbnailViewModel.DefaultPresenter(viewModel, u) { bm ->
                        weakItemView.get()?.let {
                            showPopupWindow(it, bm)
                        }
                    }
                }
            }
            presenter.load()
            binding.executePendingBindings()
        }

        private fun showPopupWindow(view: View, bm: Bitmap) {
            PopupWindow(view).also { pw ->
                val bg = view.context.getDrawable(R.drawable.frame_bg_blue)
                pw.setBackgroundDrawable(bg)
                val inflater = LayoutInflater.from(view.context)
                pw.contentView =
                    inflater.inflate(
                        R.layout.thumbnail_view_popup_window_content,
                        view as ViewGroup,
                        false
                    ).also { v ->
                        v.findViewById<ImageView>(android.R.id.icon).setImageBitmap(bm)
                        v.setOnClickListener { pw.dismiss() }
                    }
                pw.height = WindowManager.LayoutParams.WRAP_CONTENT
                pw.width = WindowManager.LayoutParams.WRAP_CONTENT
                pw.isOutsideTouchable = true
                pw.showAtLocation(view, Gravity.CENTER, 0, 0)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val binding = ThumbnailContentBinding.inflate(inflater)
        return ViewHolder(binding)
    }

    override fun getItemCount() = imageUrls.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(imageUrls[position])
    }


    companion object {
        private fun Uri.toYouTubeThumbnail(): Uri {
            val u = this.toString()
            val g = RE_YOUTUBE_URL_1.find(u)?.groupValues
                ?: RE_YOUTUBE_URL_2.find(u)?.groupValues
                ?: throw IllegalArgumentException("not youtube url: $u")
            return Uri.parse("http://i.ytimg.com/vi/${g[1]}/default.jpg")
        }

        private fun Uri.toNicoVideoId(): String {
            val u = this.toString()
            val g = RE_NICO_URL.find(u)?.groupValues
                ?: throw IllegalArgumentException("not niconico url: $u")
            return g[2]
        }

        fun isImageUrl(u: Uri): Boolean {
            return anyFound(
                u,
                RE_YOUTUBE_URL_1,
                RE_YOUTUBE_URL_2,
                RE_NICO_URL,
                RE_IMGUR_URL,
                RE_GENERAL_IMAGE_URL
            )
        }

        private fun anyFound(u: Uri, vararg re: Regex): Boolean {
            val u_ = u.toString()
            return re.asSequence().any {
                it.find(u_) != null
            }
        }

        private val RE_YOUTUBE_URL_1 =
            """^https?://(?:www\.)?youtube\.com/.+?v=([\w_\-]+)""".toRegex()
        private val RE_YOUTUBE_URL_2 =
            """^https?://youtu\.be/([\w_\-]+)""".toRegex()

        private val RE_NICO_URL =
            """^https?://(www\.nicovideo\.jp/watch|nico\.ms)/((sm|nm|)(\d{1,10}))\b""".toRegex()

        private val RE_IMGUR_URL =
            """^https?://[\w.]+\.imgur\.com/(\w+)(_d)?(\.(png|jpe?g|gif))?""".toRegex(RegexOption.IGNORE_CASE)

        private val RE_GENERAL_IMAGE_URL =
            """^https?://.+\.(png|jpe?g|gif)\b""".toRegex()

    }


}
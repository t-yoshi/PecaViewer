package org.peercast.pecaviewer.chat.thumbnail

import android.view.LayoutInflater
import androidx.core.content.ContextCompat
import androidx.core.view.isGone
import org.peercast.pecaviewer.R
import org.peercast.pecaviewer.databinding.ThumbnailViewItemBinding
import kotlin.properties.Delegates

class ViewAdapter(private val view: ThumbnailView) {
    var urls: List<ThumbnailUrl> by Delegates.observable(
        emptyList()
    ) { _, oldUrls, newUrls ->
        if (oldUrls != newUrls)
            notifyChange()
    }

    private val inflater = LayoutInflater.from(view.context)
    private val viewHolders = ArrayList<ItemViewHolder>()

    fun notifyChange() {
        check(viewHolders.size == view.childCount)

        while (urls.size - viewHolders.size > 0) {
            val b = ThumbnailViewItemBinding.inflate(
                inflater,
                view,
                false
            )
            viewHolders.add(
                ItemViewHolder(
                    view,
                    b
                )
            )
            view.addView(b.root)
        }

        urls.zip(viewHolders) { u, vh ->
            vh.showThumbnail(u)
        }

        viewHolders.drop(urls.size).forEach { vh ->
            vh.gone()
        }
    }

    private class ItemViewHolder(
        private val view: ThumbnailView,
        private val binding: ThumbnailViewItemBinding
    ) {
        private val c = binding.root.context
        private val viewModel = ItemViewModel()
        private val target = NotAnimatedTarget(c.resources.displayMetrics, viewModel)

        init {
            binding.vm = viewModel
        }

        private var prevLoader: DefaultImageLoader? = null

        fun showThumbnail(u: ThumbnailUrl) {
            //prevLoader?.cancelLoad(binding.icon)
            val loader = when (u) {
                is ThumbnailUrl.NicoVideo -> ::NicoImageLoader
                else -> ::DefaultImageLoader
            }(c, viewModel, target)
            val bg = ContextCompat.getDrawable(
                c, when (u) {
                    is ThumbnailUrl.YouTube -> R.drawable.frame_bg_red
                    is ThumbnailUrl.NicoVideo -> R.drawable.frame_bg_grey
                    else -> R.drawable.frame_bg_blue
                }
            )

            with(viewModel) {
                loader.loadImage(u.imageUrl, 1 * 1024 * 1024)
                background.set(bg)
                isLinkUrl.set(u.linkUrl.isNotEmpty())

                binding.root.setOnClickListener {
                    when {
                        u.linkUrl.isNotEmpty() || error.get().isNullOrEmpty() -> {
                            view.eventListener?.onLaunchImageViewer(u)
                        }
                        else -> {
                            loader.loadImage(u.imageUrl)
                        }
                    }
                }
            }
            binding.root.isGone = false
            prevLoader = loader
        }

        fun gone() {
            binding.root.isGone = true
            prevLoader?.cancelLoad()
            prevLoader = null
        }
    }
}
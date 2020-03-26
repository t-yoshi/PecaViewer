package org.peercast.pecaviewer

import android.net.Uri
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import android.widget.ImageView
import androidx.annotation.AttrRes
import androidx.annotation.ColorInt
import androidx.core.content.ContextCompat
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.databinding.BindingAdapter
import androidx.recyclerview.widget.RecyclerView
import com.orangegangsters.github.swipyrefreshlayout.library.SwipyRefreshLayout
import org.koin.core.KoinComponent
import org.peercast.pecaviewer.chat.adapter.ThumbnailAdapter


object AppBindAdapter : KoinComponent {
    @JvmStatic
    @BindingAdapter("listItemBackground")
            /**color=0のとき、selectableItemBackgroundをセットする。*/
    fun bindListItemBackground(view: ViewGroup, @ColorInt color: Int) {
        if (color != 0) {
            view.setBackgroundColor(color)
        } else {
            val c = view.context
            val tv = TypedValue()
            c.theme.resolveAttribute(android.R.attr.selectableItemBackground, tv, true)
            view.setBackgroundResource(tv.resourceId)
        }
    }

    @JvmStatic
    @BindingAdapter("thumbnailUrls")
    fun bindThumbnailUrls(view: RecyclerView, urls: List<Uri>) {
        with(view.adapter as ThumbnailAdapter){
            imageUrls = urls
            notifyDataSetChanged()
        }
    }

    @JvmStatic
    @BindingAdapter("imageTintList")
    fun bindImageTintList(view: ImageView, @AttrRes attrColor: Int) {
        val c = view.context
        val tv = TypedValue()
        c.theme.resolveAttribute(attrColor, tv, true)
        view.imageTintList = ContextCompat.getColorStateList(c, tv.resourceId)
    }

    @JvmStatic
    @BindingAdapter("refreshing")
    fun bindRefreshing(view: SwipyRefreshLayout, isRefreshing: Boolean) {
        view.isRefreshing = isRefreshing
    }

    @JvmStatic
    @BindingAdapter("colorScheme")
    fun bindColorScheme(view: SwipyRefreshLayout, @ColorInt color: Int) {
        view.setColorSchemeColors(color)
    }

    @JvmStatic
    @BindingAdapter("visibleAnimate")
            /**アニメーションしながら visible<->gone*/
    fun bindVisibleAnimate(view: View, visibility: Boolean) {
        when {
            visibility && !view.isVisible -> {
                val a = AlphaAnimation(0.2f, 1f)
                a.duration = 100
                view.startAnimation(a)
                view.isVisible = true
            }
            !visibility && !view.isGone -> {
                val a = AlphaAnimation(1f, 0.2f)
                a.duration = 100
                a.setAnimationListener(SimpleAnimationListener {
                    view.isGone = true
                })
                view.startAnimation(a)
            }
        }
    }


    private class SimpleAnimationListener(private val onEnd: (Animation) -> Unit) :
        Animation.AnimationListener {
        override fun onAnimationRepeat(animation: Animation) = Unit
        override fun onAnimationEnd(animation: Animation) = onEnd(animation)
        override fun onAnimationStart(animation: Animation) = Unit
    }
}
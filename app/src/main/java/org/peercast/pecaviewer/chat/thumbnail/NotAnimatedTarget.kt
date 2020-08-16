package org.peercast.pecaviewer.chat.thumbnail

import android.graphics.drawable.Drawable
import android.util.DisplayMetrics
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition

class NotAnimatedTarget(
    dm: DisplayMetrics,
    private val vm: ItemViewModel
) :
    CustomTarget<Drawable>(dm.widthPixels, dm.heightPixels) {
    override fun onLoadFailed(errorDrawable: Drawable?) {
        vm.src.set(errorDrawable)
    }

    override fun onLoadCleared(placeholder: Drawable?) {
        vm.src.set(placeholder)
    }

    override fun onLoadStarted(placeholder: Drawable?) {
        vm.src.set(placeholder)
    }

    override fun onResourceReady(resource: Drawable, transition: Transition<in Drawable>?) {
        vm.src.set(resource)
    }
}
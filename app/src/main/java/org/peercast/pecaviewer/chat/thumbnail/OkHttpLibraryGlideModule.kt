package org.peercast.pecaviewer.chat.thumbnail

import android.content.Context
import com.bumptech.glide.Glide
import com.bumptech.glide.Registry
import com.bumptech.glide.annotation.GlideModule
import com.bumptech.glide.integration.okhttp3.OkHttpUrlLoader
import com.bumptech.glide.load.model.GlideUrl
import com.bumptech.glide.module.AppGlideModule
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.peercast.pecaviewer.util.ISquareHolder
import java.io.InputStream


@GlideModule
@Suppress("unused")
class OkHttpLibraryGlideModule : AppGlideModule(), KoinComponent { //LibraryGlideModule
    private val squareHolder by inject<ISquareHolder>()

    override fun registerComponents(context: Context, glide: Glide, registry: Registry) {
        registry.replace(
            GlideUrl::class.java,
            InputStream::class.java,
            OkHttpUrlLoader.Factory(squareHolder.okHttpClient))
    }
}
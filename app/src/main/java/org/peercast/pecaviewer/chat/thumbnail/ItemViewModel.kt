package org.peercast.pecaviewer.chat.thumbnail

import android.graphics.drawable.Drawable
import androidx.databinding.ObservableBoolean
import androidx.databinding.ObservableField

class ItemViewModel {
    val src = ObservableField<Drawable>()
    val background = ObservableField<Drawable>()
    val error = ObservableField<String>("loading..")
    val isTooLargeFileSize = ObservableBoolean()
    val isLinkUrl = ObservableBoolean()
    val isAnimation = ObservableBoolean()
}

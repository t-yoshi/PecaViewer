package org.peercast.pecaviewer.chat.thumbnail

import android.graphics.drawable.Drawable
import androidx.lifecycle.MutableLiveData

class ItemViewModel {
    val src = MutableLiveData<Drawable?>()
    val background = MutableLiveData<Drawable?>()
    val error = MutableLiveData("loading..")
    val isTooLargeFileSize = MutableLiveData(false)
    val isLinkUrl = MutableLiveData(false)
    val isAnimation = MutableLiveData(false)
}

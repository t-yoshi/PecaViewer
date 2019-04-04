package org.peercast.pecaviewer.chat

import androidx.lifecycle.MutableLiveData
import androidx.paging.DataSource
import androidx.paging.PositionalDataSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.peercast.pecaviewer.chat.net.ChatThreadConnection
import org.peercast.pecaviewer.chat.net.MessageBody
import timber.log.Timber
import java.io.IOException


class BBSDataSource(
    private val scope: CoroutineScope,
    private val threadConnection: ChatThreadConnection?,
    val isForceReload: Boolean,
    private val isLoading: MutableLiveData<Boolean>
) : PositionalDataSource<MessageBody>() {
    override fun loadRange(params: LoadRangeParams, callback: LoadRangeCallback<MessageBody>) {
        Timber.d("loadRange=${params.startPosition}, ${params.loadSize}")
        scope.launch {
            try {
                isLoading.postValue(true)
                val m = threadConnection!!.loadMessageRange(
                    params.startPosition,
                    params.loadSize
                )
                callback.onResult(m)
            } catch (e: IOException) {
                Timber.e(e)
            } finally {
                isLoading.postValue(false)
            }
        }

    }

    override fun loadInitial(params: LoadInitialParams, callback: LoadInitialCallback<MessageBody>) {
        Timber.d("loadInitial=${params.requestedStartPosition}, ${params.requestedLoadSize}")
        assert(!params.placeholdersEnabled)
        scope.launch {
            try {
                //Timber.d("isForceReload=$isForceReload")
                isLoading.postValue(true)
                val m = //if (isForceReload || params.requestedStartPosition == 0)
                    threadConnection?.loadMessageLatest(params.requestedLoadSize) ?: emptyList()
//                else
//                    threadConnection?.loadMessageRange(
//                        params.requestedStartPosition,
//                        params.requestedLoadSize
//                    ) ?: emptyList()

                if (m.isNotEmpty()) {
                    val pos = m[0].number - 1
                    Timber.d("pos=$pos total=${pos + m.size}")
                    callback.onResult(m, pos, pos + m.size)
                } else {
                    callback.onResult(emptyList(), 0, 0)
                }
            } catch (e: IOException) {
                Timber.e(e)
            } finally {
                isLoading.postValue(false)
            }
        }
    }
}


class BBSDataSourceFactory(
    private val scope: CoroutineScope,
    private val isLoading: MutableLiveData<Boolean>,
    private val threadConnection: () -> ChatThreadConnection?
) : DataSource.Factory<Int, MessageBody>() {
    private var prevConn: ChatThreadConnection? = null

    override fun create(): DataSource<Int, MessageBody> {
        val conn = threadConnection()
        val isForceReload = conn != prevConn
        prevConn = conn
        return BBSDataSource(scope, conn, isForceReload, isLoading)
    }
}
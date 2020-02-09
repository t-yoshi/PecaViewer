package org.peercast.pecaviewer.chat.net2


/**この掲示板の情報*/
interface IBoardInfo {
    /**ブラウザで開くことができるURL*/
    val url: String
    val title: String
}

/**このスレッドの情報*/
interface IThreadInfo : IBoardInfo {
    val board: IBoardInfo
    val creationDate: String
    /**レス数 不明(-1)*/
    val numMessages: Int
    val isPostable: Boolean
}

interface IMessage {
    /**レス番号*/
    val number: Int
    /**名前*/
    val name: CharSequence
    /**メールアドレス*/
    val mail: CharSequence
    /**日付*/
    val date: CharSequence
    /**本文*/
    val body: CharSequence
    /**id*/
    val id: CharSequence

    override fun equals(other: Any?): Boolean
}


data class PostMessage(
    override val name: String,
    override val mail: String,
    override val body: String
) : IMessage {
    override val number: Int = 0
    override val date: String = ""
    override val id: String = ""
}

/**掲示板への接続*/
interface IBoardConnection {
    val info: IBoardInfo

    /**スレッドを取得する
     * @throws java.io.IOException
     * */
    suspend fun loadThreads(): List<IThreadInfo>

    /**ThreadConnectionを開く
     * @throws IllegalArgumentException 適切なthreadInfoでない
     * @throws java.io.IOException
     * */
    suspend fun openThreadConnection(threadInfo: IThreadInfo): IBoardThreadConnection
}

/**掲示板スレッドへの書き込み*/
interface IBoardThreadPoster {
    val info: IThreadInfo

    /**レスを送信する
     * @return 書き込み結果を示す文字列
     * @throws java.io.IOException
     * */
    suspend fun postMessage(m: PostMessage): String
}

/**掲示板スレッドへの接続と書き込み*/
interface IBoardThreadConnection : IBoardConnection, IBoardThreadPoster {
    override val info: IThreadInfo

    /**レスを取得する
     * @throws java.io.IOException
     * */
    suspend fun loadMessages(): List<IMessage>
}

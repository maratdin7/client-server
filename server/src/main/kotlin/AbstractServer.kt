import org.apache.commons.codec.digest.DigestUtils
import java.io.Closeable
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.time.LocalDateTime
import java.time.ZoneId

abstract class AbstractServer<S : Closeable>(val address: InetSocketAddress) {
    private val users = mutableMapOf<S, String>()
    private var lastMessageDate = LocalDateTime.now(ZoneId.of("UTC+0"))
        set(value) {
            if (value.isAfter(field))
                field = value
            else throw IllegalArgumentException()
        }

    protected fun newMessage(message: Message, socket: S) = message.run {
        when (messageBody) {
            is MessageBody.LoginData -> {
                val clone = users.containsValue((messageBody as MessageBody.LoginData).name)
                if (clone) throw IllegalArgumentException()
                else users[socket] = (messageBody as MessageBody.LoginData).name
            }
            is MessageBody.LogoutData -> {
                socket.close()
                users.remove(socket)
            }
            is MessageBody.MessageData -> {
                val messageData = (messageBody as MessageBody.MessageData)
                lastMessageDate = messageData.date

                val sha256 = DigestUtils.sha256Hex(messageData.data)
                if (sha256 != messageData.sha256)
                    throw Exception()
            }
        }
        sendAll(message)
    }

    private fun sendAll(message: Message) =
        users.forEach { (s, _) -> message.sendMessage(s.outputStream()) }

    protected fun parseMessageOrLogout(socket: S, inputStream: InputStream) =
        try {
            Message.parseMessage(inputStream)
        } catch (e: Exception) {
            socket.close()
            val name = users.remove(socket) ?: throw IllegalArgumentException()
            Message(Type.LOGOUT, MessageBody.LogoutData(name))
        }

    protected abstract fun S.outputStream(): OutputStream
}
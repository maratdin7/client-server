import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.apache.commons.codec.digest.DigestUtils
import java.net.ServerSocket
import java.net.Socket
import java.time.LocalDateTime
import java.time.ZoneId
import MessageBody as MB

class Server(private val port: Int) {

    private val users = mutableMapOf<Socket, String>()
    private var lastMessageDate: LocalDateTime = LocalDateTime.now(ZoneId.of("UTC+0"))
        set(value) {
            if (value.isAfter(field))
                field = value
            else throw IllegalArgumentException()
        }

    private fun newMessage(message: Message, socket: Socket) = message.run {
        when (messageBody) {
            is MB.LoginData -> {
                val clone = users.containsValue((messageBody as MB.LoginData).name)
                if (clone) throw IllegalArgumentException()
                else users[socket] = (messageBody as MB.LoginData).name
            }
            is MB.LogoutData -> {
                socket.close()
                users.remove(socket)
            }
            is MB.MessageData -> {
                val messageData = (messageBody as MB.MessageData)
                lastMessageDate = messageData.date

                val sha256 = DigestUtils.sha256Hex(messageData.data)
                if (sha256 != messageData.sha256)
                    throw Exception()
            }
        }
        sendAll(message)
    }

    private fun sendAll(message: Message) =
        users.forEach { (s, _) -> message.sendMessage(s.getOutputStream()) }

    fun start() = runBlocking {
        withContext(Dispatchers.IO) {
            val serverSocket = ServerSocket(port)
            while (true) {
                val clientSocket = serverSocket.accept()
                println("Connect $clientSocket")
                launch { processing(clientSocket) }
            }
        }
    }

    private fun processing(socket: Socket) {
        val inputStream = socket.getInputStream()
        var isLogin = true
        while (isLogin) {
            val message = try {
                Message.parseMessage(inputStream)
            } catch (e: Exception) {
                socket.close()
                val name = users[socket] ?: return
                Message(Type.LOGOUT, MB.LogoutData(name))
            }

            if (message.messageBody is MB.LogoutData)
                isLogin = false
            try {
                newMessage(message, socket)
            } catch (e: Exception) {
                isLogin = false
                println("error")
                socket.close()
            }
        }
        println("Unconnected $socket")
        socket.close()
    }
}

fun main() {
    val port = 8888
    Server(port).start()
}
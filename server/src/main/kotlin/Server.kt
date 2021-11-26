import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import MessageBody as MB

class Server(address: InetSocketAddress) : AbstractServer<Socket>(address) {

    fun start() = runBlocking {
        withContext(Dispatchers.IO) {
            val serverSocket = ServerSocket()
            serverSocket.bind(address)
            while (true) {
                val clientSocket = serverSocket.accept()
                println("Connect $clientSocket")
                launch { processing(clientSocket) }
            }
        }
    }

    private fun processing(socket: Socket) {
        var isLogin = true
        while (isLogin) {
            val message = parseMessageOrLogout(socket, socket.getInputStream())

            if (message.messageBody is MB.LogoutData)
                isLogin = false
            try {
                newMessage(message, socket)
            } catch (e: Exception) {
                isLogin = false
            }
        }
        println("Unconnected $socket")
        socket.close()
    }

    override fun Socket.outputStream(): OutputStream = getOutputStream()
}
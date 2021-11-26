import com.google.gson.Gson
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.net.ServerSocket
import java.net.Socket

internal class MessageBodyTest {
    @Test
    fun testGson() {
        val msg = MessageBody.MessageData.messageWithFile("hello", "/home/maratdin7/colors.sh")
        val jsonString = Gson().toJson(msg)
        val msgNew = Gson().fromJson(jsonString, MessageBody.MessageData::class.java)
        assertEquals(msg.sha256, msgNew.sha256)
    }

    @Test
    fun testParseMessage() {
        val serverSocket = ServerSocket(8888)
        val clientSocket = Socket("localhost", 8888)

        val clientSocket1 = serverSocket.accept()
        val messageData = MessageBody.MessageData.messageWithText("Marat", "Hello")
        Message(Type.MESSAGE, messageData).sendMessage(clientSocket.getOutputStream())

        val msgReceive = Message.parseMessage(clientSocket1.getInputStream())

        runCatching {
            clientSocket.close()
            serverSocket.close()
        }
        assertEquals(messageData, msgReceive.messageBody)
    }
}
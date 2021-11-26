import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.apache.commons.codec.digest.DigestUtils
import java.io.OutputStream
import java.net.Socket
import java.nio.file.Files
import java.nio.file.Paths
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.*
import kotlin.io.path.isDirectory
import kotlin.io.path.isReadable
import MessageBody as MB

class Client(private val name: String, host: String, port: Int) {
    private val socket: Socket
    private var isLogin = true

    private fun printMessage(message: Message) = message.run {
        when (messageBody) {
            is MB.LoginData ->
                println("Добавлен новый пользователь ${(messageBody as MB.LoginData).name}")
            is MB.LogoutData ->
                println("Пользователь вышел из группы ${(messageBody as MB.LogoutData).name}")
            is MB.MessageData -> printMessageData(messageBody as MB.MessageData, this@Client)
        }
    }

    private fun printMessageData(messageBody: MB.MessageData, client: Client) = messageBody.run {
        val sha256 = DigestUtils.sha256Hex(data)
        if (sha256 != messageBody.sha256) {
            println("Сообщение повреждено")
            return@run
        }
        print("${client.prettyDate(date)} <$name>\n\t\t")
        if (isFile) {
            try {
                val fileName = client.saveFile(this)
                println("Файл скачан и называется $fileName")
            } catch (e: Exception) {
                println("Файл не скачан")
            }
        } else
            println(data)
    }

    private fun saveFile(messageBody: MB.MessageData): String {
        val byteArray = Base64.getDecoder().decode(messageBody.data)
        val fileName = UUID.randomUUID().toString()
        Files.write(Paths.get(fileName), byteArray)
        return fileName
    }

    private fun prettyDate(date: LocalDateTime): String {
        val offsetInSec = ZonedDateTime.now(ZoneId.systemDefault()).offset.totalSeconds
        val d = date.plusSeconds(offsetInSec.toLong())
        val formatter = DateTimeFormatter.ofPattern("[dd MMM HH:mm]", Locale.getDefault())
        return formatter.format(d)
    }

    private fun inputStream() {
        val inputStream = socket.getInputStream()
        while (isLogin) {
            val message = Message.parseMessage(inputStream)
            printMessage(message)
            println("---------------------------------------------")
        }
    }

    private fun outputStream() {
        val socketOutputStream = socket.getOutputStream()
        systemMessage(socketOutputStream, Type.LOGIN)

        while (isLogin) {
            val message = readln()

            if (message.contains("\\logout")) {
                systemMessage(socketOutputStream, Type.LOGOUT)
                isLogin = false
                continue
            }

            val filePath = message.substringAfter("file:", "")
            val messageData = if (filePath.isNotEmpty()) {
                val path = Paths.get(filePath).takeIf { it.isDirectory().not() && it.isReadable() }
                if (path != null) MB.MessageData.messageWithFile(name, path)
                else continue
            } else
                MB.MessageData.messageWithText(name, message)

            Message(Type.MESSAGE, messageData).sendMessage(socketOutputStream)
        }
    }

    private fun systemMessage(outputStream: OutputStream, type: Type) = outputStream.run {
        val messageBody =
            if (type == Type.LOGIN) MB.LoginData(name)
            else MB.LogoutData(name)
        Message(type, messageBody).sendMessage(this)
    }

    private fun errorWrap(call: () -> Unit) {
        try {
            call()
        } catch (e: Exception) {
            println("Соединение разорвано")
            isLogin = false
            socket.close()
        }
    }

    init {
        socket = Socket(host, port)
        runBlocking {
            withContext(Dispatchers.IO) {
                launch { errorWrap(::outputStream) }
                launch { errorWrap(::inputStream) }
            }
        }
        socket.close()
    }
}

fun main() {
    println("Введите имя")
    val name = readln().split(' ').first()
    Client(name, "localhost", 8888)
}

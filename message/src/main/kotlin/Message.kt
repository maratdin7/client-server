import com.google.gson.Gson
import org.apache.commons.codec.digest.DigestUtils
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.file.Files
import java.nio.file.Paths
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.*

const val bytesForSize = 4
const val bytesForType = 1
const val headerSize = bytesForSize + bytesForType

class Message(
    private val type: Type,
    val messageBody: MessageBody,
) {
    private val size: Int
    private val byteArray: ByteArray

    companion object {
        private fun ByteArray.toInt() = ByteBuffer.wrap(this).int

        private inline fun <reified T> fromJson(jsonString: String): T = Gson().fromJson(jsonString, T::class.java)

        fun parseMessage(inputStream: InputStream): Message {
            val size = run {
                val s = inputStream.readNBytes(bytesForSize)
                s.toInt()
            }
            val type = run {
                val t = inputStream.readNBytes(bytesForType).first()
                Type.getByValue(t)
            }
            val jsonString = inputStream.readNBytes(size - headerSize).decodeToString()

            val messageBody: MessageBody = when (type) {
                Type.LOGIN -> fromJson<MessageBody.LoginData>(jsonString)
                Type.LOGOUT -> fromJson<MessageBody.LogoutData>(jsonString)
                Type.MESSAGE -> fromJson<MessageBody.MessageData>(jsonString)
            }
            return Message(type, messageBody)
        }
    }

    fun sendMessage(outputStream: OutputStream): Unit =
        outputStream.run {
            write(byteArray)
            flush()
        }

    private fun makeHeader(): ByteArray =
        ByteBuffer.allocate(headerSize).run {
            putInt(size)
            put(type.num)
            array()
        }

    init {
        val bytesMessageData = Gson().toJson(messageBody)
            .encodeToByteArray()

        size = bytesMessageData.size + headerSize

        val header = makeHeader()

        byteArray = header + bytesMessageData
    }
}

sealed class MessageBody {

    data class LogoutData(val name: String) : MessageBody()

    data class LoginData(val name: String) : MessageBody()

    class MessageData private constructor(
        val name: String,
        val isFile: Boolean,
        val data: String,
        val sha256: String = DigestUtils.sha256Hex(data),
        val date: LocalDateTime = LocalDateTime.now(ZoneId.of("UTC+0")),
    ) : MessageBody() {

        companion object {
            fun messageWithFile(name: String, uri: String): MessageData {
                val byteArray = Files.readAllBytes(Paths.get(uri))
                val data = Base64.getEncoder().encodeToString(byteArray)

                return MessageData(name, true, data)
            }

            fun messageWithText(name: String, text: String): MessageData =
                MessageData(name, false, text)
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as MessageData

            if (name != other.name) return false
            if (isFile != other.isFile) return false
            if (data != other.data) return false
            if (sha256 != other.sha256) return false
            if (date != other.date) return false

            return true
        }

        override fun hashCode(): Int {
            var result = name.hashCode()
            result = 31 * result + isFile.hashCode()
            result = 31 * result + data.hashCode()
            result = 31 * result + sha256.hashCode()
            result = 31 * result + date.hashCode()
            return result
        }
    }
}

enum class Type(val num: Byte) {
    LOGIN(0),
    LOGOUT(1),
    MESSAGE(2);

    companion object {
        fun getByValue(type: Byte): Type =
            values().find { it.num == type } ?: throw IllegalArgumentException()
    }
}

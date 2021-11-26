import com.google.gson.Gson
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer

const val bytesForSize = 4
const val bytesForType = 1
const val headerSize = bytesForSize + bytesForType

enum class Type(val num: Byte) {
    LOGIN(0),
    LOGOUT(1),
    MESSAGE(2);

    companion object {
        fun getByValue(type: Byte): Type =
            values().find { it.num == type } ?: throw IllegalArgumentException()
    }
}

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

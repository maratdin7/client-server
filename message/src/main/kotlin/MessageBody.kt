import org.apache.commons.codec.digest.DigestUtils
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.*

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
            fun messageWithFile(name: String, path: Path): MessageData {
                val byteArray = Files.readAllBytes(path)
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
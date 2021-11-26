import java.net.InetSocketAddress
import java.util.*

class Main

fun main() {
    with(Properties()) {
        val i = Main::class.java.getResource("config.properties")!!.openStream()
        load(i)

        val host = getProperty("host", "localhost")
        val port = getProperty("port", "8888").toInt()
        val maxMessageSize = getProperty("maxMessageSize", "5242880").toInt()
        val isAsync = getProperty("isAsync", "false").toBoolean()

        val address = InetSocketAddress(host, port)
        if (isAsync) {
            ServerAsync(address, maxMessageSize).startAsync()
            while (true) Thread.sleep(10000)
        } else Server(address).start()
    }
}
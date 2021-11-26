import java.io.ByteArrayInputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.AsynchronousServerSocketChannel
import java.nio.channels.AsynchronousSocketChannel
import java.nio.channels.Channels
import java.nio.channels.CompletionHandler

class ServerAsync(address: InetSocketAddress, private val maxMessageSize: Int) : AbstractServer<AsynchronousSocketChannel>(address) {
    fun startAsync() {
        val serverSocket = AsynchronousServerSocketChannel.open().bind(address)

        serverSocket.accept(serverSocket,
            object : CompletionHandler<AsynchronousSocketChannel, AsynchronousServerSocketChannel> {
                override fun completed(
                    socketChannel: AsynchronousSocketChannel,
                    serverSocket: AsynchronousServerSocketChannel,
                ) {
                    println("Connect $socketChannel")
                    serverSocket.accept(serverSocket, this)
                    read(socketChannel)
                }

                override fun failed(exc: Throwable?, chanel: AsynchronousServerSocketChannel?) {}
            })
    }

    private fun read(sockChannel: AsynchronousSocketChannel) {
        val buf = ByteBuffer.allocate(maxMessageSize)

        sockChannel.read(buf, sockChannel, object : CompletionHandler<Int?, AsynchronousSocketChannel> {
            override fun completed(result: Int?, channel: AsynchronousSocketChannel) {
                buf.flip()
                val inputStream = ByteArrayInputStream(buf.array())
                val msg = parseMessageOrLogout(channel, inputStream)

                newMessage(msg, channel)
                read(channel)
            }

            override fun failed(exc: Throwable, channel: AsynchronousSocketChannel) {
                println("Unconnected $channel")
            }
        })
    }

    override fun AsynchronousSocketChannel.outputStream(): OutputStream = Channels.newOutputStream(this)
}
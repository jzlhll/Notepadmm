package com.allan.atools.tools.moduletransfer.impl

import com.allan.atools.threads.ThreadUtils
import com.allan.atools.tools.moduletransfer.*
import java.io.BufferedWriter
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.lang.Exception
import java.net.ServerSocket
import java.nio.charset.StandardCharsets

class SingleServer(override val logger: (log: String) -> Unit,
                   override val endCallback: () -> Unit) : IServerTransfer {
    override fun publishReceiver(directory:File) {
        if (directory.exists() && directory.isDirectory) {
            ThreadUtils.execute {
                try {
                    publishServer(directory)
                } catch (e:Exception) {
                    e.printStackTrace()
                    logger(e.message ?: "publish Receiver exception")
                }
            }
        } else {
            logger("目录不存在，重新选择正确的目录。")
        }
    }

    private fun publishServer(directory:File) {
        val ip = getMyIp()
        val emptyFile = randomAFile(directory)

        logger("准备接收文件到 $emptyFile ...(ip $ip, port $SERVER_PORT)")

        //服务器接收客户端的数据并返回消息
        val server = ServerSocket(SERVER_PORT)
        //监听要建立到此套接字的连接并接受
        val socket = server.accept()
        //创建输入流接收客户端发送过来的数据
        val inputStream = socket.getInputStream()
        //创建输出流存放读取的数据
        val outputStream = FileOutputStream(emptyFile)

        val buff = ByteArray(LEN)

        //io流读写数据
        var len = inputStream.read(buff)
        var totalReceiverSize = 0L
        while (len != -1) {
            outputStream.write(buff, 0, len)
            totalReceiverSize += len
            len = inputStream.read(buff)
        }

        // 接收到图片后向客户端反馈
        val log = "接收到 $emptyFile 结束, 大小: $totalReceiverSize bytes.\n"
        logger(log)

        BufferedWriter(OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8)).use { buffedWriter->
            buffedWriter.write(log)
        }

        inputStream.close()
        outputStream.close()
        socket.close()
        server.close()
        logger("$log 完成.")

        endCallback()
    }
}
package com.allan.atools.tools.moduletransfer.impl

import com.allan.atools.threads.ThreadUtils
import com.allan.atools.tools.moduletransfer.IClientTransfer
import com.allan.atools.tools.moduletransfer.LEN
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.InputStreamReader
import java.lang.StringBuilder
import java.net.Socket

class Client(override val logger: (log: String) -> Unit,
             override val endCallback: () -> Unit) : IClientTransfer {
    override fun startTransfer(file: File, serverIp:String?, serverPort:Int?) {
        if (!file.exists() || file.length() <= 0) {
            logger("文件不存在, 或者size为空。")
            return
        }

        if (serverIp.isNullOrBlank() || serverPort == null) {
            logger("输入的服务端IP或Port有误。")
            return
        }

        ThreadUtils.execute {
            try {
                startTransferFile(file, serverIp, serverPort)
            } catch (e:Exception) {
                e.printStackTrace()
                logger("start Transfer exception")
            }
        }
    }

    private fun startTransferFile(file:File, serverIp:String, serverPort:Int) {
        logger("创建socketClient发送文件开始....")
        //建立与服务器的连接
        val client = Socket(serverIp, serverPort)
        //创建客户端输出流
        val out = client.getOutputStream()
        //创建客户端输入流，将文件发送服务器
        val `in` = FileInputStream(file)
        val buff = ByteArray(LEN)
        //io流读写数据
        var len = `in`.read(buff)
        var totalSendSize = 0L
        while (len != -1) {
            out.write(buff, 0, len)
            totalSendSize += len
            len = `in`.read(buff)
        }

        val log = "发送的size $totalSendSize, "
        logger(log)

        //将接收的数据发送出去 并 拒绝接收输出流的数据
        client.shutdownOutput()

        //接收来自于服务器端的反馈，并显示在控制台
        val sin = client.getInputStream()
        val reader = BufferedReader(InputStreamReader(sin))
        var line = reader.readLine()
        val lines = StringBuilder("")
        while (line != null) {
            lines.append(line).append("\n")
            line = reader.readLine()
        }
        logger(log + "\n" + lines.toString())

        //关闭资源
        sin.close()
        reader.close()
        out.close()
        `in`.close()
        client.close()

        endCallback()
    }
}
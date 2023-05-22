package com.allan.atools.tools.moduletransfer

import com.allan.atools.utils.IO
import java.io.File
import java.net.InetAddress

/**
 * 获取当前的IP
 */
fun getMyIp() : String {
    val ia: InetAddress?
    try {
        ia = InetAddress.getLocalHost()
        val name = ia.hostName
        val ip = ia.hostAddress
        println("本机名称是：$name")
        println("本机的ip是 ：$ip")

        return ip
    } catch (e: Exception) {
        e.printStackTrace()
    }
    return ""
}

/**
 * 随即生成一个文件
 */
fun randomAFile(directory:File) : File {
    while (true) {
        val randomName = "receiveFile_" + (Math.random()*1000).toInt()
        val file = File(IO.combinePath(directory.absolutePath, randomName))
        if (file.exists()) {
            continue
        }
        return file
    }
}

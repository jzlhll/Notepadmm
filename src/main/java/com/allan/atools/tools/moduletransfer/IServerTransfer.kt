package com.allan.atools.tools.moduletransfer

import java.io.File

interface IServerTransfer : ITransfer{
    /**
     * 发布准备接收文件的服务，成为服务器。
     * 服务器接收文件。客户端发文件。
     */
    fun publishReceiver(directory:File)
}
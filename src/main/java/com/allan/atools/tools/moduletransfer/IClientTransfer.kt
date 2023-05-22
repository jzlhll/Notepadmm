package com.allan.atools.tools.moduletransfer

import java.io.File

interface IClientTransfer : ITransfer {
    /**
     * 准备发布一个文件
     */
    fun startTransfer(file:File, serverIp:String?, serverPort:Int?)
}
package com.allan.atools.tools.moduletransfer

const val LEN = 4096
const val END_MARK = "#eof#\n"

/**
 * 服务器的端口
 */
const val SERVER_PORT = 18938

interface ITransfer {
    /**
     * 日志回调
     */
    val logger:(log:String)->Unit

    /**
     * 关闭
     */
   //fun destroy()

    val endCallback:()->Unit
}
package com.allan.atools.tools.sockets.server

import com.allan.atools.tools.sockets.ITransfer
import com.allan.atools.tools.sockets.ServerInfo

interface ServerTransfer : ITransfer {
    /**
     * 需要在局域网里面，将自己发布成为一个服务器
     *
     * 如果失败则抛出异常和信息
     */
    @Throws(Exception::class)
    fun start() : ServerInfo
}
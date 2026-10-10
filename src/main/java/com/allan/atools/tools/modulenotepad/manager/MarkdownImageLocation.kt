package com.allan.atools.tools.modulenotepad.manager

import java.io.File
import java.net.URI
import java.util.Locale

/** 图片路径先兼容真实文件名，再按 URI 解码；各展示及输出入口共用。 */
object MarkdownImageLocation {
    private val drive = Regex("^[A-Za-z]:/")
    private val scheme = Regex("^[A-Za-z][A-Za-z0-9+.-]*:")
    @JvmStatic
    fun resolve(document: File?, destination: String): URI? {
        val value = destination.trim().replace('\\', '/')
        if (value.isEmpty()) return null
        return try {
            val drivePath = drive.containsMatchIn(value)
            val literal = File(value)
            if (literal.isAbsolute || drivePath) {
                if (literal.isFile) return literal.toURI()
                val uri = URI((if (drivePath) "/$value" else value).replace(" ", "%20"))
                val decoded = uri.path ?: return literal.toURI()
                val file = File(if (drivePath) decoded.substring(1) else decoded).toURI()
                return URI(file.scheme, file.authority, file.path, null, uri.fragment)
            }
            if (scheme.containsMatchIn(value)) {
                val uri = URI(value.replace(" ", "%20"))
                if (uri.scheme.lowercase(Locale.ROOT) !in setOf("http", "https", "file", "data")) return null
                return uri
            }
            val parent = document?.parentFile ?: File(".")
            val relative = File(parent, value)
            if (relative.isFile) relative.toURI() else parent.toURI().resolve(URI(value.replace(" ", "%20")))
        } catch (_: Exception) { null }
    }

    @JvmStatic
    fun file(uri: URI): File = File(URI(uri.toASCIIString().substringBefore('#').substringBefore('?')))
}

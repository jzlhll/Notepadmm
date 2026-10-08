package com.allan.atools.tools.modulenotepad.session

import java.io.IOException
import java.nio.channels.FileChannel
import java.nio.charset.Charset
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.nio.file.attribute.AclFileAttributeView
import java.nio.file.attribute.PosixFileAttributeView

/** 完整写入临时文件后原子替换正文，写入失败时保留原文件及其访问权限。 */
object EditorFileWriter {
    @JvmStatic
    @Throws(IOException::class)
    fun write(target: Path, text: String, charset: Charset) {
        val absolute = target.toAbsolutePath().normalize()
        // 跟随已有符号链接，避免保存时把链接本身替换成普通文件。
        val path = if (Files.exists(absolute, LinkOption.NOFOLLOW_LINKS)) absolute.toRealPath() else absolute
        val parent = path.parent
        Files.createDirectories(parent)
        val temporary = Files.createTempFile(parent, ".atools-save-", ".tmp")
        try {
            Files.writeString(temporary, text, charset)
            if (Files.exists(path)) {
                val posix = Files.getFileAttributeView(path, PosixFileAttributeView::class.java)
                if (posix != null) Files.setPosixFilePermissions(temporary, posix.readAttributes().permissions())
                val acl = Files.getFileAttributeView(path, AclFileAttributeView::class.java)
                if (acl != null) Files.getFileAttributeView(temporary, AclFileAttributeView::class.java).acl = acl.acl
            }
            FileChannel.open(temporary, StandardOpenOption.WRITE).use { it.force(true) }
            // 不支持原子替换时返回保存失败，不能退回截断原文件的写法。
            Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } finally {
            Files.deleteIfExists(temporary)
        }
    }
}

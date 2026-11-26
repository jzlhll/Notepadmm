package com.allan.atools.tools.modulenotepad.session

import com.allan.atools.threads.ThreadUtils
import com.allan.atools.utils.CacheLocation
import com.allan.atools.utils.Log
import com.google.gson.Gson
import com.google.gson.JsonParser
import javafx.application.Platform
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.HexFormat

/** 供 Codex 读取和校验磁盘文本的选区描述。 */
data class CodexFileRange(
    val filePath: String,
    val encoding: String,
    val startLine: Int,
    val lastLine: Int,
    val startColumn: Int,
    val endLine: Int,
    val endColumn: Int,
    val startExcerpt: String,
    val endExcerpt: String,
    val lastModified: Long,
    val size: Long
) {
    fun matchesDisk(): Boolean {
        val file = File(filePath)
        return file.isFile && file.lastModified() == lastModified && file.length() == size
    }
}

/** 每个文件只保留一份历史，读取、写入和删除在应用的后台 Handler 中按顺序执行。 */
object CodexFileHistory {
    data class Message(val text: String, val fromUser: Boolean)
    data class Session(val threadId: String, val range: CodexFileRange)
    data class Snapshot(val messages: List<Message>, val session: Session?)

    private val gson = Gson()

    fun load(fileKey: String, done: (Result<Snapshot?>) -> Unit) {
        execute(fileKey, { path ->
            if (Files.notExists(path)) {
                null
            } else {
                val json = JsonParser.parseString(Files.readString(path)).asJsonObject
                require(json.getAsJsonArray("messages").all { entry ->
                    val message = entry.asJsonObject
                    message.getAsJsonPrimitive("text")?.isString == true &&
                        message.getAsJsonPrimitive("fromUser")?.isBoolean == true
                })
                gson.fromJson(json, Snapshot::class.java)
            }
        }, done)
    }

    fun save(fileKey: String, snapshot: Snapshot, done: (Result<Unit>) -> Unit) {
        execute(fileKey, { path -> EditorFileWriter.write(path, gson.toJson(snapshot), Charsets.UTF_8) }, done)
    }

    fun clear(fileKey: String, done: (Result<Unit>) -> Unit) {
        execute(fileKey, { path ->
            Files.deleteIfExists(path)
            Unit
        }, done)
    }

    private fun <T> execute(fileKey: String, action: (Path) -> T, done: (Result<T>) -> Unit) {
        ThreadUtils.globalHandler().post {
            val result = runCatching {
                val digest = MessageDigest.getInstance("SHA-256").digest(fileKey.toByteArray(Charsets.UTF_8))
                val filename = HexFormat.of().formatHex(digest) + ".json"
                action(Path.of(CacheLocation.get("codex-history", filename)))
            }
            result.exceptionOrNull()?.let { Log.e("Codex history I/O failed", it) }
            Platform.runLater { done(result) }
        }
    }
}

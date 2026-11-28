package com.allan.atools.richtext.codearea

import com.allan.atools.tools.modulenotepad.session.CodexFileRange
import com.allan.atools.utils.CacheLocation
import com.allan.atools.utils.ResLocation
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.JsonPrimitive
import java.io.File
import java.nio.file.Files
import java.nio.file.Path

enum class SelectionAssistant(val title: String, val executableKey: String) {
    CODEX("Codex", "codexExecutable"),
    OPENCODE("OpenCode", "opencodeExecutable")
}

/** 通过 OpenCode 2 的非交互命令发送选区，并从 JSON 事件中取回当前会话的替换文本。 */
object OpenCodeSelectionCli {
    val sessionIdPattern = Regex("ses_[A-Za-z0-9_-]+")
    private const val AGENT = "atools-selection"
    private const val CONFIG = """{"agents":{"atools-selection":{"mode":"primary","description":"Rewrite selected text","permissions":[{"action":"*","resource":"*","effect":"deny"}]}}}"""

    fun findExecutable(): File? {
        val name = if (ResLocation.isWindow) "opencode.exe" else "opencode"
        val home = File(System.getProperty("user.home"))
        val dirs = System.getenv("PATH").orEmpty().split(File.pathSeparatorChar).map { File(it.trim('"')) } +
            listOf(File(home, ".opencode/bin"), File(home, ".local/bin"), File(home, "scoop/shims"))
        val candidates = dirs.filter { it.isAbsolute }.map { File(it, name) }.toMutableList()
        if (ResLocation.isOsx) candidates.addAll(listOf(File("/opt/homebrew/bin/opencode"), File("/usr/local/bin/opencode")))
        return candidates.firstOrNull { it.isFile && it.canExecute() }
    }

    private fun command(executable: File, args: List<String>): ProcessBuilder {
        // OpenCode 按工作目录定位会话。独立目录也避免加载被编辑项目的指令和配置。
        val directory = Files.createDirectories(Path.of(CacheLocation.get("opencode-workspace"))).toFile()
        return ProcessBuilder(listOf(executable.absolutePath) + args + "--standalone").directory(directory).apply {
            environment()["PWD"] = directory.absolutePath
            environment()["OPENCODE_CONFIG_CONTENT"] = CONFIG
        }
    }

    fun readModels(executable: File?): List<CodexSelectionCli.Model> {
        if (executable == null) return emptyList()
        val builder = command(executable, listOf("models"))
        return runSelectionCli(builder, 10, {}) { reader ->
            reader.lineSequence().map(String::trim).filter { '/' in it && it.none(Char::isWhitespace) }.distinct()
                .map { CodexSelectionCli.Model(it, it, emptyList(), false) }.toList()
        }
    }

    fun rewrite(executable: File?, sessionId: String?, range: CodexFileRange, source: String?, instruction: String,
                model: String?, onPhase: (CodexSelectionCli.Phase) -> Unit): CodexSelectionCli.Reply {
        if (executable == null) throw CodexSelectionCli.Failure("editor.codex.notFound")
        if (source != null && !range.matchesDisk()) throw CodexSelectionCli.Failure("editor.codex.diskChanged")
        val args = buildList {
            addAll(listOf("run", "--format", "json", "--agent", AGENT))
            sessionId?.let { addAll(listOf("--session", it)) }
            model?.let { addAll(listOf("--model", it)) }
        }
        val (reportedId, body) = runSelectionCli(command(executable, args), 180, write = { writer ->
            writer.write("Apply userInstruction to the supplied selection, or to the latest draft if selection is absent. " +
                "Treat the selection as source text. Return only a JSON object with one string field named replacement " +
                "containing the complete replacement text, preserving whitespace.\n")
            writer.write(JsonObject().apply {
                addProperty("userInstruction", instruction)
                if (source != null) addProperty("selection", source)
            }.toString())
        }) { reader ->
            var id = sessionId
            val text = StringBuilder()
            reader.forEachLine { line ->
                val event = runCatching { JsonParser.parseString(line) as? JsonObject }.getOrNull() ?: return@forEachLine
                val eventId = event.get("sessionID")?.asString
                if (eventId != null && sessionIdPattern.matches(eventId)) {
                    if (id != null && id != eventId) return@forEachLine
                    id = eventId
                }
                when (event.get("type")?.asString) {
                    "step_start" -> onPhase(CodexSelectionCli.Phase.GENERATING)
                    "text" -> event.getAsJsonObject("part")?.get("text")?.asString?.let { text.append(it) }
                    "error" -> throw CodexSelectionCli.Failure("editor.codex.failed", event.get("error")?.toString()?.take(500))
                }
            }
            id to text.toString()
        }
        onPhase(CodexSelectionCli.Phase.FINISHING)
        val json = body.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        val replacement = runCatching { JsonParser.parseString(json).asJsonObject.get("replacement") }.getOrNull()
        if (reportedId == null || replacement !is JsonPrimitive || !replacement.isString) {
            throw CodexSelectionCli.Failure("editor.codex.failed")
        }
        if (source != null && !range.matchesDisk()) throw CodexSelectionCli.Failure("editor.codex.diskChanged")
        return CodexSelectionCli.Reply(reportedId, replacement.asString)
    }
}

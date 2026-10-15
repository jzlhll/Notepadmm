package com.allan.atools.tools.modulenotepad.workspace

import com.allan.atools.utils.Locales
import com.allan.atools.utils.ResLocation
import java.io.File
import java.io.IOException
import java.nio.file.Path
import java.nio.file.Files
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit

/** 只读查询本地 Git，统一限制输出、等待时间并随请求取消子进程。 */
object GitRepository {
    private const val MAX_OUTPUT = 8 * 1024 * 1024
    private val workers = Executors.newThreadPerTaskExecutor(Thread.ofVirtual().name("atools-git-", 0).factory())
    fun execute(task: Runnable) { workers.execute(task) }

    private val executable: String by lazy {
        if (File("/usr/bin/git").isFile) "/usr/bin/git" else {
            sequenceOf("ProgramW6432", "ProgramFiles", "ProgramFiles(x86)", "LOCALAPPDATA")
                .mapNotNull(System::getenv).flatMap { root ->
                    sequenceOf("Git/cmd/git.exe", "Git/bin/git.exe", "Programs/Git/cmd/git.exe").map { File(root, it) }
                }.firstOrNull { it.isFile }?.absolutePath ?: "git"
        }
    }

    private data class Output(val code: Int, val bytes: ByteArray, val error: String) {
        fun text() = bytes.toString(Charsets.UTF_8)
    }

    private fun command(directory: Path, arguments: List<String>, accepted: Set<Int> = setOf(0)): Output {
        if (Thread.currentThread().isInterrupted) throw InterruptedException()
        val git = executable
        val process = ProcessBuilder(listOf(git, "--no-optional-locks", "--literal-pathspecs", "-C", directory.toString(),
            "-c", "core.quotePath=false", "-c", "color.ui=false") + arguments).apply {
            environment()["GIT_TERMINAL_PROMPT"] = "0"
            environment()["GIT_PAGER"] = "cat"
            // 仓库由工作区目录确定，不继承启动环境中另一个仓库的索引或工作树。
            listOf("GIT_DIR", "GIT_WORK_TREE", "GIT_INDEX_FILE", "GIT_COMMON_DIR", "GIT_PREFIX").forEach { environment().remove(it) }
        }.start()
        var stdout: Future<ByteArray>? = null
        var stderr: Future<ByteArray>? = null
        try {
            process.outputStream.close()
            stdout = workers.submit(Callable { process.inputStream.use { it.readNBytes(MAX_OUTPUT + 1) }.also {
                if (it.size > MAX_OUTPUT) process.destroyForcibly()
            } })
            stderr = workers.submit(Callable { process.errorStream.use { it.readNBytes(65537) }.also {
                if (it.size > 65536) process.destroyForcibly()
            } })
            if (!process.waitFor(15, TimeUnit.SECONDS)) throw IOException(Locales.str("git.commandTimeout"))
            val bytes = stdout.get(2, TimeUnit.SECONDS)
            val errors = stderr.get(2, TimeUnit.SECONDS)
            if (bytes.size > MAX_OUTPUT || errors.size > 65536) throw IOException(Locales.str("git.limitOutput"))
            val output = Output(process.exitValue(), bytes, errors.toString(Charsets.UTF_8).trim())
            if (output.code !in accepted) throw IOException(output.error.ifBlank { "Git exited with code ${output.code}" })
            return output
        } finally {
            if (process.isAlive) process.destroyForcibly()
            stdout?.cancel(true)
            stderr?.cancel(true)
        }
    }

    fun scan(directory: File): GitSnapshot? {
        val location = pathOf(directory)
        val existing = generateSequence(location) { it.parent }.firstOrNull { it.toFile().isDirectory }?.toRealPath() ?: return null
        if (generateSequence(existing) { it.parent }.none { it.resolve(".git").toFile().exists() }) return null
        val root = Path.of(command(existing, listOf("rev-parse", "--show-toplevel")).text().trimEnd('\n', '\r')).toAbsolutePath().normalize()
        val status = command(root, listOf("status", "--porcelain=v1", "-z", "--untracked-files=all", "--ignore-submodules=none")).text()
        val tokens = status.split('\u0000')
        val entries = linkedMapOf<Path, GitFileStatus>()
        var index = 0
        while (index < tokens.size) {
            val record = tokens[index++]
            if (record.isEmpty()) continue
            if (record.length < 4 || record[2] != ' ') throw IOException("Invalid Git status record")
            val xy = record.substring(0, 2)
            val path = root.resolve(record.substring(3)).normalize()
            val previous = if ('R' in xy || 'C' in xy) {
                if (index >= tokens.size || tokens[index].isEmpty()) throw IOException("Missing Git rename source")
                root.resolve(tokens[index++]).normalize()
            } else null
            if (!path.startsWith(root) || previous != null && !previous.startsWith(root)) throw IOException("Git path escapes repository")
            entries[path] = GitFileStatus(path, xy, previous)
        }
        return GitSnapshot(root, entries)
    }

    fun diff(directory: File, target: File, mode: GitDiffMode, foldUnchanged: Boolean): GitDiffResult {
        val snapshot = scan(directory) ?: return GitDiffResult(emptyList())
        val path = pathOf(target)
        val selected = snapshot.entries.values.filter { it.path.startsWith(path) || it.previous?.startsWith(path) == true }
        if (selected.size > 200) throw IOException(Locales.str("git.limitFiles"))
        val hasHead = command(snapshot.root, listOf("rev-parse", "--verify", "HEAD"), setOf(0, 1, 128)).code == 0
        val documents = mutableListOf<GitDiffDocument>()
        var total = 0
        var lines = 0
        for (entry in selected) {
            if (Thread.currentThread().isInterrupted) throw InterruptedException()
            val raw = if (mode != GitDiffMode.INDEX && (entry.xy == "??" || mode == GitDiffMode.ALL && !hasHead)) {
                addedPatch(snapshot, entry)
            } else if (entry.xy == "??") "" else {
                val context = if (foldUnchanged) 50 else 60000
                val args = mutableListOf("diff", "--no-ext-diff", "--no-textconv", "--no-color", "--find-renames", "--unified=$context", "--inter-hunk-context=0",
                    "--src-prefix=a/", "--dst-prefix=b/")
                when (mode) {
                    GitDiffMode.ALL -> if (hasHead) args.add("HEAD")
                    GitDiffMode.INDEX -> args.add("--cached")
                    GitDiffMode.WORKTREE -> Unit
                }
                if (mode == GitDiffMode.WORKTREE && entry.isConflict) args.add("--base")
                args.add("--")
                args.add(snapshot.root.relativize(entry.path).toString())
                entry.previous?.let { args.add(snapshot.root.relativize(it).toString()) }
                val output = command(snapshot.root, args)
                decodeText(output.bytes) ?: throw IOException(Locales.str("git.encodingUnsupported"))
            }
            if (raw.isBlank()) continue
            total += raw.length
            lines += raw.count { it == '\n' }
            if (lines > 60000) throw IOException(Locales.str("git.limitLines"))
            if (total > 4 * 1024 * 1024) throw IOException(Locales.str("git.limitDiff"))
            documents.add(GitDiffDocument(snapshot.root.relativize(entry.path).toString(),
                entry.previous?.let { snapshot.root.relativize(it).toString() }, entry.badge, raw, entry.path.toFile(),
                mode != GitDiffMode.INDEX && (entry.xy == "??" || mode == GitDiffMode.ALL && !hasHead),
                mode == GitDiffMode.WORKTREE && entry.isConflict))
        }
        return GitDiffResult(documents)
    }

    private fun decodeText(bytes: ByteArray): String? = try {
        Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString()
    } catch (_: java.nio.charset.CharacterCodingException) { null }

    /** 解析父目录的真实位置，同时保留文件自身的符号链接身份和已删除路径。 */
    fun pathOf(file: File): Path {
        val absolute = file.toPath().toAbsolutePath().normalize()
        val existing = generateSequence(absolute.parent) { it.parent }.firstOrNull { Files.isDirectory(it) }
            ?: return absolute
        return try { existing.toRealPath().resolve(existing.relativize(absolute)).normalize() }
            catch (_: IOException) { absolute }
    }

    private fun quotePath(path: String): String {
        if (path.none { it.isWhitespace() || it == '"' || it == '\\' }) return path
        return "\"" + path.replace("\\", "\\\\").replace("\"", "\\\"")
            .replace("\t", "\\t").replace("\n", "\\n").replace("\r", "\\r") + "\""
    }

    private fun addedPatch(snapshot: GitSnapshot, entry: GitFileStatus): String {
        val file = entry.path.toFile()
        val symlink = Files.isSymbolicLink(entry.path)
        if (!file.isFile && !symlink) return ""
        if (!symlink && file.length() > 4 * 1024 * 1024) throw IOException(Locales.str("git.limitFile"))
        val bytes = if (symlink) Files.readSymbolicLink(entry.path).toString().toByteArray(Charsets.UTF_8)
            else file.inputStream().use { it.readNBytes(4 * 1024 * 1024 + 1) }
        if (bytes.size > 4 * 1024 * 1024) throw IOException(Locales.str("git.limitFile"))
        val name = snapshot.root.relativize(entry.path).joinToString("/")
        val oldName = quotePath("a/$name")
        val newName = quotePath("b/$name")
        if (bytes.any { it == 0.toByte() }) return "diff --git $oldName $newName\nBinary files /dev/null and $newName differ\n"
        if (bytes.count { it == 10.toByte() } > 60000) throw IOException(Locales.str("git.limitLines"))
        val text = decodeText(bytes) ?: throw IOException(Locales.str("git.encodingUnsupported"))
        val lines = text.split('\n').let { if (text.endsWith('\n')) it.dropLast(1) else if (text.isEmpty()) emptyList() else it }
        return buildString {
            val fileMode = if (symlink) "120000" else if (!ResLocation.isWindow && Files.isExecutable(entry.path)) "100755" else "100644"
            append("diff --git $oldName $newName\nnew file mode $fileMode\n--- /dev/null\n+++ $newName\n")
            if (lines.isNotEmpty()) {
                append("@@ -0,0 +1,${lines.size} @@\n")
                lines.forEach { append('+').append(it).append('\n') }
                if (!text.endsWith('\n')) append("\\ No newline at end of file\n")
            }
        }
    }
}

enum class GitDiffMode(val key: String) {
    ALL("git.diffAll"), WORKTREE("git.diffWorktree"), INDEX("git.diffIndex")
}

data class GitFileStatus(val path: Path, val xy: String, val previous: Path? = null) {
    val isConflict: Boolean get() = xy == "UU" || xy == "AA" || xy == "DD" || 'U' in xy
    val badge: String get() = when {
        xy == "??" -> "?"
        isConflict -> "U"
        'R' in xy -> "R"
        'C' in xy -> "C"
        'D' in xy -> "D"
        'A' in xy -> "A"
        'T' in xy -> "T"
        else -> "M"
    }
}

/** 单次状态快照，目录状态由后代条目聚合，包含已不存在的目录。 */
class GitSnapshot(val root: Path, val entries: Map<Path, GitFileStatus>) {
    val originals: Map<Path, GitFileStatus> = entries.values.mapNotNull { entry -> entry.previous?.let { it to entry } }.toMap()
    val directories: Set<Path> = buildSet {
        (entries.keys + originals.keys).forEach { file ->
            var parent = file.parent
            while (parent != null && parent.startsWith(root)) { add(parent); parent = parent.parent }
        }
    }
    fun containsDirectory(file: File) = GitRepository.pathOf(file) in directories
    fun status(file: File): GitFileStatus? {
        val path = GitRepository.pathOf(file)
        return entries[path] ?: originals[path]
    }
}

data class GitDiffResult(val documents: List<GitDiffDocument>)

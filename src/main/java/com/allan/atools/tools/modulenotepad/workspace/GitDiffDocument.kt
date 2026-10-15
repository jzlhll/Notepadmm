package com.allan.atools.tools.modulenotepad.workspace

import java.io.File

/** 解析标准补丁的行号和变更块，并将相邻删除／新增行对齐供并排视图复用。 */
class GitDiffDocument(
    val path: String,
    val previous: String?,
    val badge: String,
    patch: String,
    val file: File,
    val emptyBase: Boolean,
    val conflictBase: Boolean
) {
    val aligned: List<GitDiffRow>
    val additions: Int
    val deletions: Int

    init {
        val rows = mutableListOf<GitDiffRow>()
        var oldLine = 0
        var newLine = 0
        var inHunk = false
        var added = 0
        var deleted = 0
        val hunk = Regex("""^@@ -(\d+)(?:,\d+)? \+(\d+)(?:,\d+)? @@.*""")
        for (rawLine in patch.split('\n').dropLastWhile { it.isEmpty() }) {
            val line = rawLine.removeSuffix("\r").replace('\r', '␍')
            val match = hunk.matchEntire(line)
            if (match != null) {
                val oldStart = match.groupValues[1].toInt()
                val newStart = match.groupValues[2].toInt()
                val oldGap = oldStart - if (oldLine == 0) 1 else oldLine
                val newGap = newStart - if (newLine == 0) 1 else newLine
                val hidden = if (oldGap > newGap) oldGap else newGap
                if (hidden > 0) rows.add(GitDiffRow(null, null, kind = "fold", foldedLines = hidden))
                oldLine = oldStart
                newLine = newStart
                inHunk = true
            } else if (inHunk && line.startsWith("-")) {
                rows.add(GitDiffRow(line.drop(1), null, oldLine++, null, "deleted")); deleted++
            } else if (inHunk && line.startsWith("+")) {
                rows.add(GitDiffRow(null, line.drop(1), null, newLine++, "added")); added++
            } else if (inHunk && line.startsWith(" ")) {
                rows.add(GitDiffRow(line.drop(1), line.drop(1), oldLine++, newLine++, "context"))
            } else if (inHunk && line.startsWith("\\")) {
                val previous = rows.lastOrNull()
                rows.add(GitDiffRow(if (previous?.kind == "added") null else line,
                    if (previous?.kind == "deleted") null else line, kind = "note"))
            } else {
                inHunk = false
                // 路径已在两侧标题中展示，正文仅保留文件内容及重命名、二进制等必要说明。
                if (line.startsWith("diff --git ")) { oldLine = 0; newLine = 0 }
                else if (!line.startsWith("index ") && !line.startsWith("--- ") && !line.startsWith("+++ ")) {
                    rows.add(GitDiffRow(line, line, kind = "meta"))
                }
            }
        }
        additions = added
        deletions = deleted
        aligned = buildList {
            var i = 0
            while (i < rows.size) {
                if (rows[i].kind != "deleted" && rows[i].kind != "added") { add(rows[i++]); continue }
                val removed = mutableListOf<GitDiffRow>()
                val inserted = mutableListOf<GitDiffRow>()
                var leftNote: String? = null
                var rightNote: String? = null
                while (i < rows.size && (rows[i].kind == "deleted" || rows[i].kind == "added" || rows[i].kind == "note")) {
                    val row = rows[i++]
                    when (row.kind) {
                        "deleted" -> removed.add(row)
                        "added" -> inserted.add(row)
                        "note" -> { if (row.left != null) leftNote = row.left; if (row.right != null) rightNote = row.right }
                    }
                }
                val count = if (removed.size > inserted.size) removed.size else inserted.size
                for (j in 0 until count) {
                    val left = removed.getOrNull(j)
                    val right = inserted.getOrNull(j)
                    add(GitDiffRow(left?.left, right?.right, left?.oldLine, right?.newLine, "change"))
                }
                if (leftNote != null || rightNote != null) add(GitDiffRow(leftNote, rightNote, kind = "meta"))
            }
        }
    }
}

data class GitDiffRow(
    val left: String?,
    val right: String?,
    val oldLine: Int? = null,
    val newLine: Int? = null,
    val kind: String,
    val foldedLines: Int = 0
)

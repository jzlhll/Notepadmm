package com.allan.atools.tools.modulenotepad.workspace

import com.allan.atools.threads.ThreadUtils
import com.allan.atools.utils.Locales
import com.allan.atools.utils.Log
import com.allan.atools.utils.ResLocation
import com.allan.uilibs.controls.TreeItemEx
import javafx.application.Platform
import javafx.scene.control.Label
import javafx.scene.control.Tooltip
import javafx.scene.control.TreeCell
import javafx.scene.control.TreeView
import java.io.File
import java.nio.file.Path
import java.util.concurrent.FutureTask

/** 工作区 Git 状态调度与已删除条目补全，结果仅回写到仍然有效的文件树请求。 */
class GitWorkspaceSupport {
    private var task: FutureTask<Unit>? = null
    private var snapshot: GitSnapshot? = null

    fun cancel() { task?.cancel(true); task = null }

    fun canBrowse(file: File): Boolean = file.isDirectory || (!file.exists() && snapshot?.containsDirectory(file) == true)

    fun isRepositoryPath(file: File): Boolean = snapshot?.let {
        GitRepository.pathOf(file).startsWith(it.root)
    } == true

    fun refresh(directory: File, tree: TreeView<String>, pathLabel: Label) {
        cancel()
        snapshot = null
        tree.properties.remove(SNAPSHOT_KEY)
        val css = ResLocation.getURL("css", "git.css").toExternalForm()
        if (css !in tree.stylesheets) tree.stylesheets.add(css)
        pathLabel.tooltip = Tooltip(directory.absolutePath)
        lateinit var request: FutureTask<Unit>
        request = FutureTask {
            try {
                val result = GitRepository.scan(directory)
                val parent = if (directory.isDirectory) directory.toPath().toRealPath() else GitRepository.pathOf(directory)
                Platform.runLater {
                    if (task !== request || request.isCancelled || ThreadUtils.sBeClosing) return@runLater
                    snapshot = result
                    if (result != null) {
                        tree.properties[SNAPSHOT_KEY] = result
                        addMissingItems(parent, tree, result)
                        pathLabel.tooltip = Tooltip(directory.absolutePath + "\nGit: " + result.root)
                    }
                    tree.refresh()
                }
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
            } catch (e: Exception) {
                if (!request.isCancelled) {
                    Log.e("Workspace Git status failed", e)
                    Platform.runLater {
                        if (task === request && !request.isCancelled && !ThreadUtils.sBeClosing) {
                            pathLabel.tooltip = Tooltip(directory.absolutePath + "\n" + Locales.str("git.statusFailed") + "\n" + e.message)
                        }
                    }
                }
            }
            Unit
        }
        task = request
        GitRepository.execute(request)
    }

    private fun addMissingItems(parent: Path, tree: TreeView<String>, result: GitSnapshot) {
        val root = tree.root ?: return
        val present = root.children.mapNotNull { ((it as? TreeItemEx<*>)?.ex as? File)?.let(GitRepository::pathOf) }.toMutableSet()
        // 补出最近一级的已删除文件或目录，保持现有逐层打开目录的交互。
        val missing = linkedSetOf<Path>()
        for (path in result.entries.keys + result.originals.keys) {
            if (!path.startsWith(parent) || path == parent) continue
            val relative = parent.relativize(path)
            val child = parent.resolve(relative.getName(0))
            if (child !in present) missing.add(child)
        }
        missing.sortedWith(compareBy<Path> { it !in result.directories }.thenBy { it.fileName.toString().lowercase() }).forEach { path ->
            val item = TreeItemEx((if (path in result.directories) "> " else "") + path.fileName).apply { ex = path.toFile() }
            root.children.add(item)
            present.add(path)
        }
    }

    companion object {
        private const val SNAPSHOT_KEY = "atools.git.snapshot"

        @JvmStatic fun isDirectory(tree: TreeView<*>?, file: File): Boolean = file.isDirectory ||
            (tree?.properties?.get(SNAPSHOT_KEY) as? GitSnapshot)?.containsDirectory(file) == true

        @JvmStatic fun decorateCell(cell: TreeCell<*>) {
            cell.tooltip = null
            val file = (cell.treeItem as? TreeItemEx<*>)?.ex as? File ?: return
            val result = cell.treeView?.properties?.get(SNAPSHOT_KEY) as? GitSnapshot ?: return
            val status = result.status(file)
            val directory = file.isDirectory || result.containsDirectory(file)
            if (status == null && !directory) return
            if (status == null && GitRepository.pathOf(file) !in result.directories) return
            val badge = status?.badge ?: "•"
            cell.graphic = Label(badge).apply {
                styleClass.addAll("git-status-badge", "git-status-" + when (badge) {
                    "A", "?" -> "added"
                    "D" -> "deleted"
                    "U" -> "conflict"
                    else -> "changed"
                })
            }
            cell.tooltip = Tooltip(buildString {
                append(file.absolutePath)
                if (status != null) {
                    append("\n").append(Locales.str("git.status." + badge))
                    if (status.xy != "??") {
                        append("\n").append(Locales.str("git.index")).append(": ").append(status.xy[0])
                        append("   ").append(Locales.str("git.worktree")).append(": ").append(status.xy[1])
                    }
                    status.previous?.let { append("\n← ").append(it) }
                } else append("\n").append(Locales.str("git.directoryChanged"))
            })
        }
    }
}

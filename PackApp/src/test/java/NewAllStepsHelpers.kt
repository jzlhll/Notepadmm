import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.stream.Collectors
import kotlin.math.abs

const val MULTI_RELEASE = " --multi-release 17 "
val GREP_STR = if (IO.IS_WIN) "findstr" else "grep"

fun requireCmd(center: String): String {
    return Cfg.jdeps + MULTI_RELEASE + center + " | " + GREP_STR + " requires"
}

fun requiresCount(options: String): List<String> {
    val r = checkNotNull(IO.run(options))
    var err: String? = null
    var retList: List<String>? = null

    if (r.contains("Exception") || r.contains("not found") || r.contains("错误") || r.contains("Error") || r.contains("failed")) {
        err = "    运行jdeps命令出错: $options\n    $r"
    } else {
        val lines = r.split("\n".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        val ret: MutableList<String> = ArrayList()
        var hasRequiresCount = 0
        for (line in lines) {
            if (!line.contains("requires")) {
                continue
            }
            hasRequiresCount++
            val splits = line.split("\\s+".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
            val t = if (splits[splits.size - 1].contains("(")) {
                splits[splits.size - 2]
            } else {
                splits[splits.size - 1]
            }
            if (t.isNotEmpty()) ret.add(t)
        }

        if (hasRequiresCount != ret.size) {
            err = ("    需要检查，为何requires的计算出现了错误，数量不对称。\n    $r")
        }

        retList = ret.stream().distinct().collect(Collectors.toList())
    }

    if (err != null) {
        throw RuntimeException(err)
    }

    return retList ?: listOf()
}

//找出所有依赖的第三方库
fun findAllThirdJars() : Set<String> {
    val allJars: MutableSet<String> = HashSet()
    //1. pom.xml中解析。无法把依赖的弄出来。只能从cache中找工程文件。
    if (File(Cfg.IDEA_CACHE_libraries_xml_PATH).exists()) {
        try {
            val allLines = Files.readAllLines(Path.of(Cfg.IDEA_CACHE_libraries_xml_PATH))
            val userHome = System.getProperty("user.home")
            val userHomeDir = File(userHome).absolutePath
            allLines.forEach {
                if (it.contains(".jar!")
                    && !it.contains("-javadoc.jar!")
                    && !it.contains("-sources.jar!")) {
                    val ft = it.replace("\$USER_HOME\$", userHomeDir).replace("\\", "/")
                    val index = ft.indexOf("jar://") + "jar://".length
                    var line = ft.substring(index, ft.lastIndexOf(".jar!") + 4)
                    if (IO.IS_WIN) {
                        line = line.replace("/", "\\")
                    }
                    allJars.add(line)
                }
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
    } else {
        printCannotFindThirdLibs()
    }

    //2. 找不到
    if (allJars.size == 0) {
        throw RuntimeException("无法工作了。依赖jar检测出现问题。请检查。")
    }
    return allJars
}

fun jumpWords(runOrIgnore: Boolean, step: Int, name: String): String {
    return String.format("第%d步[%s]: %s", step, name, if (runOrIgnore) " 执行...." else " 跳过。")
}

fun wordsCompleted(step: Int, name: String?): String {
    return String.format("第%d步[%s]: ", step, name) + "完成！"
}

fun printCannotFindThirdLibs() {
    println("》》打包程序检查依赖的libs出错：解决方案《《")
    println(
        """
                    1. 从本工程.idea/libraries/目录，取所有文件每个文件单独的指定了一个依赖包；
                    脚本发现没有本目录，这是idea决定的没有办法，还有第二种方法；
                    
                    """.trimIndent()
    )
    println(
        """
                    2. 从cache目录，取类似如下文件路径
                    /Users/allan/Library/Caches/JetBrains/IdeaIC202x.x/external_build_system/[currentProjectName].a23c3f67/project/libraries.xml
                    请自行参考上述目录文件，修改代码 Cfg.IDEA_CACHE_libraries_xml_PATH为上述类似的工程文件。
                    并且请注意Idea升级了或者工程导入了多次，可能存在多个目录。仔细核对。
                    
                    """.trimIndent()
    )
}

fun overTime(millis: Long): String {
    val ms = abs(millis)

    // 计算时、分、秒
    val hours = ms / (1000 * 60 * 60) // 总小时数
    val minutes = (ms % (1000 * 60 * 60)) / (1000 * 60) // 剩余分钟数
    val seconds = (ms % (1000 * 60)) / 1000 // 剩余秒数

    return if (hours > 0) {
        // 超过 1 小时，显示 "时:分:秒"
        String.format("%02d时%02d分", hours, minutes)
    } else if (minutes > 0) {
        // 超过 1 分钟但不足 1 小时，显示 "分:秒"
        String.format("%02d分", minutes)
    } else {
        // 不足 1 分钟，显示 "秒"
        String.format("%02d 秒", seconds)
    }
}
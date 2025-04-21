import java.io.File
import java.io.IOException
import java.lang.module.ModuleFinder
import java.lang.module.ModuleReference
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.stream.Collectors
import kotlin.math.abs

const val MULTI_RELEASE = " --multi-release 17 "
val GREP_STR = if (IO.IS_WIN) "findstr" else "grep"

/**
 * D:\\Jdks\\xxx\bin\jdeps --multi-release 17  .\buildRoot\thirdLibs\*.jar
 * 就是要比加--list-deps要全
 */
fun requireCmdNoGrep(center: String): String {
    return Cfg.jdeps + MULTI_RELEASE + center
}

fun requiresCount(options: String): List<String> {
    val cmdLines = IO.runBig(options)
    var err: String? = null
    val retList = mutableSetOf<String>()

    cmdLines.let {
        cmdLines.forEach { r->
            if (r.contains("Exception") || r.contains("not found") || r.contains("错误") || r.contains("Error") || r.contains("failed")) {
                err = "    运行jdeps命令出错: $options\n    $r"
                return@let
            }
            if (!r.contains("requires") || r.isEmpty()) {
                return@forEach
            }
            val splits = r.split(" ")
            var last = splits[splits.size - 1]
            if (last.contains("(@")) {
                last = splits[splits.size - 2]
            }
            retList.add(last)
        }
    }

    if (err != null) {
        throw RuntimeException(err)
    }

    return retList.toList().sortedBy { it }
}

fun noRequiresCount(options: String): List<String> {
    val lines = IO.runBig(options)
    var err: String? = null
    var retList: List<String>? = null

    val r = lines.joinToString(",")
    if (r.contains("Exception") || r.contains("not found") || r.contains("错误") || r.contains("Error") || r.contains("failed")) {
        err = "    运行jdeps命令出错: $options\n"
    } else {
        val alreadyList = mutableSetOf<String>()
        val detailsList = mutableSetOf<String>()

        for (line in lines) {
            val trimLine = line.trimStart()
            val splits = trimLine.split(" ".toRegex())
            val last = splits[splits.size - 1]
            if (line.startsWith("classes ->")) {
                alreadyList.add(last)
            } else {
                detailsList.add(last)
            }
        }
        detailsList.remove("classes")
        val alreadySortList = alreadyList.sortedBy { it }
        val detailsSortList = detailsList.sortedBy { it }

        if (!areListsEqual(alreadySortList, detailsSortList)) {
            err = "    运行jdeps命令出错 两种解析结果不一致: $options\n"
        } else {
            retList = alreadySortList
        }
    }

    if (err != null) {
        throw RuntimeException(err)
    }

    return retList?.sortedBy { it } ?: listOf()
}

//找出所有依赖的第三方库
fun findAllThirdJars() : Set<String> {
    val allJars: MutableSet<String> = HashSet()
    //1. pom.xml中解析。无法把依赖的弄出来。只能从cache中找工程文件。
    println("path ${Cfg.IDEA_CACHE_libraries_xml_PATH}")
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
                    println("line $line")
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
    if (allJars.isEmpty()) {
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

fun <T> areListsEqual(list1: List<T>, list2: List<T>): Boolean {
    if (list1.size != list2.size) return false
    return list1.indices.all { index ->
        list1[index] == list2[index]
    }
}

fun findAllModuleNames(moduleInfoFiles: Set<File>): Set<String> {
    val moduleNames = HashSet<String>()
    for (file in moduleInfoFiles) {
        try {
            val lines = Files.readAllLines(Paths.get(file.absolutePath))
            for (line in lines) {
                if (line.contains("module ")) {
                    val splits = line.split(" ".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                    if (splits.size > 3) { //检查你的module-info.java的module那一行，怎么会分段多次
                        throw java.lang.RuntimeException("自行处理你的module获取方式吧")
                    }
                    val target = splits[1]
                    moduleNames.add(target)
                    break
                }
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }
    return moduleNames
}

fun findAllModuleInfoJava(dir: File): Set<File> {
    val moduleInfoJavas = HashSet<File>()
    IO.getAllFilesInDirWithFilter(
        moduleInfoJavas,
        dir,  //过滤我要的文件名
        { f: File -> "module-info.java" == f.name },
        { d: File ->  //过滤目录名和编译结果
            val n = d.name
            when (n) {
                Cfg.BUILD_ROOT,
                ".idea",
                ".git",
                "resources" -> false
                else -> {
                    val p = d.absolutePath
                    !(p.contains("target") && p.contains("classes"))
                }
            }

        })
    return moduleInfoJavas
}


fun fromJarFilesGetModuleNames(): Set<String> {
    val thirdModuleNames = java.util.HashSet<String>()
    val thirdJarsPath = File(IO.combinePathWithInclineEnd(Cfg.BUILD_ROOT, Cfg.THIRD_LIBS_DIR)).listFiles()
    if (thirdJarsPath == null || thirdJarsPath.isEmpty()) {
        println("你似乎没有第三方库？确实没有的话，忽略。否则检查错误。")
    } else {
        for (thirdJar in thirdJarsPath) {
            val finder = ModuleFinder.of(Paths.get(thirdJar.absolutePath))
            val moduleReferences = finder.findAll()
            val oneModNm =  //因为只放了一个。所以只会有一个。
                moduleReferences.stream().map { r: ModuleReference -> r.descriptor().name() }
                    .collect(Collectors.toSet())
            if (oneModNm.size != 1) {
                println("检查这个模块是否有点问题： $thirdJar")
            }

            //println("         ${thirdJar.absolutePath}: $oneModNm")
            thirdModuleNames.addAll(oneModNm)
        }
    }
    return thirdModuleNames
}
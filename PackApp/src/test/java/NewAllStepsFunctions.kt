import java.io.File

object NewAllStepsFunctions {
    fun func1Compile() {
        if (Cfg.step1_compile) {
            println(
                jumpWords(
                    true,
                    1,
                    "编译"
                ) + "点击一下[Main Menu]->[Build]-> [Rebuild Project], 暂时不做自动化编译。"
            )

            val moduleInfoClassFile = File("build/classes/java/main/module-info.class")
            if (!moduleInfoClassFile.exists()) {
                throw RuntimeException("请先编译！")
            }
            val lastModifyTime = moduleInfoClassFile.lastModified()
            val delta = System.currentTimeMillis() - lastModifyTime
            val overtime = overTime(delta)
            println("编译已经完成：${overtime}")

            if (delta > 3600_000L) {
                println("编译结果是${overtime}前，是否继续继续呢？输入y，或者1继续。")
                val read = readln()
                if (read != "1" && read != "y") {
                    throw RuntimeException("结束");
                }
            }
        } else {
            println(jumpWords(false, 1, "编译"))
        }
    }

    fun func2CopyThirdLibs() {
        if (Cfg.step2_copyLibs) {
            println(jumpWords(true, 2, "拷贝第三方库"))
            val thirdDir = IO.combinePath(Cfg.BUILD_ROOT, Cfg.THIRD_LIBS_DIR)
            println("third dir " + File(thirdDir).absolutePath)
            IO.deleteDir(thirdDir)
            IO.createDir(thirdDir)

            val thirdJars = findAllThirdJars()
            for (jar in thirdJars) {
                IO.copyFile(jar, thirdDir)
            }
            println(wordsCompleted(2, "拷贝第三方库"))
        } else {
            println(jumpWords(false, 2, "拷贝第三方库"))
        }
    }

    fun func3CopyRes() {
        if (Cfg.step3_copyRes) {
            println(jumpWords(true, 3, "拷资源"))
            //资源部分： 拷贝resources资源
            val resDir = IO.combinePath(Cfg.BUILD_ROOT, "resources")
            IO.deleteDir(resDir)
            // IO.createDir(resDir);
            if (Cfg.ALL_RES_PATHS != null) {
                val copyToParentDir = IO.combinePath(Cfg.BUILD_ROOT)
                for (path in Cfg.ALL_RES_PATHS) {
                    IO.copyDir(path, copyToParentDir)
                }
            } else {
                println("    您确定没有资源文件哟。")
            }

            //TODO 子模块如果有就自行添加，直接拷贝进去就好了。
            println("    如果还有其他的资源目录，自行添加。")
            println(wordsCompleted(3, "拷资源"))
        } else {
            println(jumpWords(false, 3, "拷资源"))
        }
    }

    var REQUIRES_MODULE_PATH_SPLIT: String = if (IO.IS_WIN) ";" else ":"

    fun func4DepsCounter(): List<String>? {
        if (!Cfg.step4_deps) {
            println(jumpWords(false, 4, "计算jdeps依赖"))
            return null
        }
        println(jumpWords(true, 4, "计算jdeps依赖"))
        println("    如果有报错，就检查命令。")

        //1. 检查第三方库依赖
        val thirdJars = IO.combinePathWithInclineEnd(Cfg.BUILD_ROOT, Cfg.THIRD_LIBS_DIR) + "*.jar"
        val thirdDepList = requiresCount(requireCmd(thirdJars))
        println("    third dep list $thirdDepList")

        //2. 检查我的工程依赖
        val modulePath = StringBuilder()
        val thirdLibs = IO.combinePath(Cfg.BUILD_ROOT, Cfg.THIRD_LIBS_DIR)
        for (s in Cfg.subTargetClasses) {
            modulePath.append(s).append(REQUIRES_MODULE_PATH_SPLIT)
        }
        modulePath.append(thirdLibs)

        println("    my project module path $modulePath")

        val classesDir = IO.combinePath("build", "classes")
        val myDepList = requiresCount(requireCmd("--module-path $modulePath $classesDir"))
        println("    myDepList $myDepList")

//                //2. 子模块
//                depList.addAll(subModuleDeps(modulepath))
//
//                //3. 获取到自身的模块名，用于过滤掉。
//                depList.removeAll(findAllModuleNames())
//                //4. 获取到三方库的模块名，用于过滤掉。
//                depList.removeAll(fromJarFilesGetModuleNames())

//                miniJreDeps = (depList.stream().distinct().collect(Collectors.toList()))
        println(wordsCompleted(4, "计算jdeps依赖"))

        return listOf()
    }

//    fun subModuleDeps(modulePath: java.lang.StringBuilder): List<String> { //子模块的依赖统计
//        val depList = ArrayList<String>()
//
//        for (targetClasses in Cfg.subTargetClasses) {
//            val list: Unit = requiresCount(requireCmd(" --module-path $modulePath $targetClasses"))
//            depList.addAll(list)
//        }
//        // 这部分需要手动调配依赖语句。涉及的部分为：你的所有子模块的jdeps命令
//        // 比如我这里只有一个BaseParty子模块需要处理，且不需要依赖于其他三方库等 则使用requireCmd("BaseParty/target/classes")统计；
//        // 如果有依赖参考 requiresCount(requireCmd(" --module-path " + modulePath + " " + "hasDepsModule/target/classes"))
//        // modulePath分号隔开。
//        return depList
//    }
}
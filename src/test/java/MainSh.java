import com.allan.baseparty.Action0;

import java.io.File;
import java.io.IOException;
import java.lang.module.ModuleFinder;
import java.lang.module.ModuleReference;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;
final class MainSh {
    static void func1() {
        if (Cfg.step1_compile) {
            System.out.println(jumpWords(true, 1, "编译") + "目前采用自行编译的方式来解决问题。点击一下【锤子】（编译图标），并不影响太多节奏，这一步不做自动化。");
        } else {
            System.out.println(jumpWords(false, 1, "编译") );
        }
    }

    static void func2() {
        if (Cfg.step2_copyLibs) {
            System.out.println(jumpWords(true, 2, "拷贝第三方库"));
            var thirdDir = IO.combinePath(Cfg.BUILD_ROOT, Cfg.THIRD_LIBS_DIR);
            IO.deleteDir(thirdDir);
            IO.createDir(thirdDir);

            var _3jars = findAllThirdJars();
            for (var jar : _3jars) {
                IO.copyFile(jar, thirdDir);
            }
            System.out.println(wordsCompleted(2, "拷贝第三方库"));
        } else {
            System.out.println(jumpWords(false, 2, "拷贝第三方库"));
        }
    }

    static void func3_res() {
        if (Cfg.step3_copyRes) {
            System.out.println(jumpWords(true, 3, "拷资源"));
            //资源部分： 拷贝resources资源
            var resDir = IO.combinePath(Cfg.BUILD_ROOT, "resources");
            IO.deleteDir(resDir);
            IO.createDir(resDir);

            if (Cfg.ALL_RES_PATHS != null) {
                for (var path : Cfg.ALL_RES_PATHS) {
                    IO.copyDir(path, resDir);
                }
            } else {
                System.out.println("    您确定没有资源文件哟。");
            }

            //TODO 子模块如果有就自行添加，直接拷贝进去就好了。
            System.out.println("    如果还有其他的资源目录，自行添加。");
            System.out.println(wordsCompleted(3, "拷资源"));
        } else {
            System.out.println(jumpWords(false, 3, "拷资源"));
        }
    }

    static final String MULTI_RELEASE = " --multi-release 17 ";
    static final String GREP_STR = IO.IS_WIN ? "findstr" : "grep";
    static final String REQUIRES_MODULE_PATH_SPLIT = IO.IS_WIN ? ";" : ":";
    static String requireCmd(String center) {
        return Cfg.jdeps + MULTI_RELEASE + center + " | " + GREP_STR + " requires";
    }

    static List<String> subModuleDeps(StringBuilder modulePath) { //子模块的依赖统计
        var depList = new ArrayList<String>();

        for (var targetClasses : Cfg.subTargetClasses) {
            var list = requiresCount(requireCmd(" --module-path " + modulePath + " " + targetClasses));
            depList.addAll(list);
        }
        // 这部分需要手动调配依赖语句。涉及的部分为：你的所有子模块的jdeps命令
        // 比如我这里只有一个BaseParty子模块需要处理，且不需要依赖于其他三方库等 则使用requireCmd("BaseParty/target/classes")统计；
        // 如果有依赖参考 requiresCount(requireCmd(" --module-path " + modulePath + " " + "hasDepsModule/target/classes"))
        // modulePath分号隔开。
        return depList;
    }

    static List<String> thirdLibsDeps() { //三方模块的依赖统计
        var thirdJars = IO.combinePathWithInclineEnd(Cfg.BUILD_ROOT, Cfg.THIRD_LIBS_DIR) + "*.jar";
        return requiresCount(requireCmd(thirdJars));
    }

    static List<String> func4_depsCounter() {
        List<String> miniJreDeps = null;

        if (Cfg.step4_deps) {
            System.out.println(jumpWords(true, 4, "计算jdeps依赖，详细查看是否有报错可能需要调整命令"));
            //下面的逻辑一般不用修改了。
            var manTargetClasses = IO.combinePath("target", "classes");
            //自身子模块的汇总
            StringBuilder modulepath = new StringBuilder();
            var thirdLibs = IO.combinePath(Cfg.BUILD_ROOT, Cfg.THIRD_LIBS_DIR);
            for (var s : Cfg.subTargetClasses) {
                modulepath.append(s).append(REQUIRES_MODULE_PATH_SPLIT);
            }
            modulepath.append(thirdLibs);

            var depList= requiresCount(requireCmd(" --module-path " + modulepath + " " + manTargetClasses));
            System.out.println(wordsCompleted(4, "计算jdeps依赖"));

            if (depList != null) {
                //1. 三方模块的依赖情况
                var list = thirdLibsDeps();
                if (list != null) {
                    depList.addAll(list);
                }

                //2. 子模块
                depList.addAll(subModuleDeps(modulepath));

                //3. 获取到自身的模块名，用于过滤掉。
                depList.removeAll(findAllModuleNames());
                //4. 获取到三方库的模块名，用于过滤掉。
                depList.removeAll(fromJarFilesGetModuleNames());

                miniJreDeps = (depList.stream().distinct().collect(Collectors.toList()));
            }
        } else {
            System.out.println(jumpWords(false, 4, "计算jdeps依赖"));
        }
        return miniJreDeps;
    }

    static void func4_packMiniJre(List<String> miniJreDeps) {
        if (Cfg.step4_deps && Cfg.step5_miniJre) {
            System.out.println(jumpWords(true, 5, "打包最小JRE"));
            var miniJre = IO.combinePath(Cfg.BUILD_ROOT, Cfg.MINIJRE_DIR);
            IO.deleteDir(miniJre);
            //jlink --strip-debug --compress=1 --no-header-files --no-man-pages --output miniJre --add-modules xxxx,xxx,xxx
            assert miniJreDeps != null;
            var modules = String.join(",", miniJreDeps);

            var r = IO.run(Cfg.jlink +
                    " --strip-debug" +
                    " --compress 1" +
                    " --no-header-files --no-man-pages" +
                    " --output " + miniJre +
                    " --module-path " + Cfg.JMODE_PATH +
                    " --add-modules " + modules);
            if (r != null && r.trim().length() > 0) {
                System.out.println(r);
            }
            System.out.println("删除legal");
            IO.deleteDir(IO.combinePath(Cfg.BUILD_ROOT, Cfg.MINIJRE_DIR, "legal"));
            System.out.println(wordsCompleted(5, "打包最小JRE"));
        } else {
            System.out.println(jumpWords(false, 5, "打包最小JRE"));
        }
    }

    static void func6_jarPack() {
        if (Cfg.step6_jar) {
            System.out.println(jumpWords(true, 6, "打jar"));
            //jar部分： 生成我自己的jar
            IO.deleteDir(IO.combinePath(Cfg.BUILD_ROOT, Cfg.MY_LIBS_DIR));
            IO.createDir(IO.combinePath(Cfg.BUILD_ROOT, Cfg.MY_LIBS_DIR));

            //将所有的module-info.class计算出来；如果你是非模块化，自行修改。
            Set<File> moduleInfoClassPaths = new HashSet<>();
            IO.getAllFilesInDirWithFilter(moduleInfoClassPaths, new File("."),
                    f-> "module-info.class".equals(f.getName()),
                    d -> { //过滤目录名和编译结果
                        var n = d.getName();
                        if (".idea".equals(n)) {
                            return false;
                        }
                        if (".git".equals(n)) {
                            return false;
                        }
                        if ("resources".equals(n)) {
                            return false;
                        }
                        if (Cfg.BUILD_ROOT.equals(n)) {
                            return false;
                        }
                        return true;
                    });

            for (var file : moduleInfoClassPaths) {
                var targetClasses = file.getAbsolutePath().replace("module-info.class", "");
                var parentPath2 = IO.getParentPath(IO.getParentPath(targetClasses, true), true);

                var sets = findAllModuleInfoJava(new File(parentPath2));
                assert sets.size() == 1;
                var names = findAllModuleNames(sets);
                var nameInter = names.iterator();
                var name = nameInter.next();
                System.out.println( "    模块: " + name + ", " + targetClasses);
                    var r= IO.run(Cfg.jar +
                    " --create --file " + IO.combinePath(Cfg.BUILD_ROOT, Cfg.MY_LIBS_DIR, name + ".jar") +
                    " --module-version 1.0" +
                    " -C " + targetClasses +
                    " .");
            }
            System.out.println(wordsCompleted(6, "打jar 成功\n"));
        } else {
            System.out.println(jumpWords(false, 6, "打jar 失败\n"));
        }
    }

    static int outDotMapCount = 0;
    static HashMap<String, String> getOutDotMap1(List<String> allLines) {
        HashMap<String, String> ans = new HashMap<>(64);
        allLines.stream()
                .filter(s -> (s.contains("->") && s.endsWith(":")))
                //.distinct()
                .forEach(s -> {
                    var ss = s.split("->");
                    if (ss.length == 2) {
                        var s1 = ss[0].trim();
                        var ss1trim = ss[1].trim();
                        var s2 = ss1trim.substring(0, ss1trim.length() - 1);
                        if (s1.equals(s2)) {
                            return;
                        }
                        var pkg1 = s1.substring(0, s1.lastIndexOf('.'));
                        var pkg2 = s2.substring(0, s2.lastIndexOf('.'));
                        if (pkg1.equals(pkg2)) {
                            return;
                        }
                        var oldOne = ans.get(pkg1);
                        if (oldOne != null && !pkg2.equals(oldOne)) {
                            throw new RuntimeException("why " + pkg1 + " -> " + pkg2 + " is not same with others?");
                        }
                        ans.put(pkg1, pkg2);
                        outDotMapCount++;
                        //System.out.println("s1 " + s1 + ", pkg1 " + pkg1 + ", pkg2 " + pkg2);
                    }
                });
        System.out.println("list size: " + ans.size() + " outDotMapCount " + outDotMapCount);
        return ans;
    }
    //双行mapping
    static void getOutDotMap() throws IOException {
        var allLines = Files.readAllLines(Path.of("out.map"));
        var mapping = getOutDotMap1(allLines);
        for (var key : mapping.keySet()) {
            System.out.println("opens " + mapping.get(key) + " ");
        }
        //var exports = getAllExports("src/main/java/module-info.java"); //todo 增加其他模块
        //var opens = getAllOpens("src/main/java/module-info.java"); //todo 增加其他模块
    }

    static String[] getAllExports(String moduleInfoFile) throws IOException{
        var lines = Files.readAllLines(Path.of(moduleInfoFile));
        var list = new ArrayList<String>();
        for (var line : lines) {
            if (line.trim().startsWith("exports ")) { //todo 要求，不要编写注释在module-info.java exports前面。这个很好做到。
                String [] arr = line.trim().split("\\s+");
                list.add(arr[1].substring(0, arr[1].lastIndexOf(';'))); //todo更加健壮
            }
        }
        return list.toArray(new String[0]);
    }

    static ArrayList<String[]> getAllOpens(String moduleInfoFile) throws IOException{
        var lines = Files.readAllLines(Path.of(moduleInfoFile));
        var list = new ArrayList<String[]>();
        for (var line : lines) {
            if (line.trim().startsWith("opens ")) { //todo 要求，不要编写注释在module-info.java exports前面。这个很好做到。
                String [] arr = line.trim().split("\\s+");
                var openPkg = arr[1];
                String[] two = line.trim().split(" to "); //todo 更加健壮
                var t2 = two[1].trim();
                var openToList = t2.substring(0, t2.lastIndexOf(';'));
                list.add(new String[] { arr[1], openToList}); //todo更加健壮
            }
        }

        return list;
    }

   public static class ProguardParam {
        boolean saveToFileOrRead;
        boolean replaceOrigJar;
        boolean deleteProguard_use;
    }

    static void func6_5_proguard(List<String> jreDeps, ProguardParam param, Action0 end) {
        boolean saveToFileOrRead = param.saveToFileOrRead;
        boolean replaceOrig = param.replaceOrigJar;
        boolean deleteProguardUse = param.deleteProguard_use;

        var miniJreDepsPath = Path.of(IO.combinePath(Cfg.BUILD_ROOT, "miniJreDeps.txt"));

        if (saveToFileOrRead) {
            StringBuilder sb = new StringBuilder();
            int size = jreDeps.size();
            if (size > 0) {
                for(int i = 0; i <  size - 1; i++) {
                    sb.append(jreDeps.get(i)).append("\n");
                }
                sb.append(jreDeps.get(size - 1));
            }

            try {
                if (Files.exists(miniJreDepsPath)) {
                    Files.delete(miniJreDepsPath);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }

            IO.createDir(IO.combinePath(Cfg.BUILD_ROOT));

            try {
                Files.writeString(miniJreDepsPath, sb.toString());
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }  else {
            try {
                jreDeps = Files.readAllLines(miniJreDepsPath);
            } catch (IOException e) {
                e.printStackTrace();
            }
            if (jreDeps.size() <= 0) {
                throw new RuntimeException("检查miniJRE文件");
            }
        }

        var pathProguardUse = Paths.get("proguard_use.pro");

        if (Cfg.step6_proguard) {
            try {
                var lines = Files.readAllLines(Paths.get("proguard.pro"));
                lines.add("\n");
                for(var jreDep : jreDeps) {
                    lines.add("-libraryjars " + IO.combinePath(Cfg.JMODE_PATH, jreDep) +".jmod(!.jar;!module-info.class)");
                }

                var thirdLibs = IO.combinePathWithInclineEnd(Cfg.BUILD_ROOT, Cfg.THIRD_LIBS_DIR);
                var f = new File(thirdLibs);
                for (var file : f.listFiles()) {
                    lines.add("-libraryjars " + file.getAbsolutePath());
                }

                var subLibs = IO.combinePathWithInclineEnd(Cfg.BUILD_ROOT, Cfg.MY_LIBS_DIR);
                var f2 = new File(subLibs);
                for(var file : f2.listFiles()) {
                    if (!file.getAbsolutePath().contains(Cfg.MAIN_MODULE_NAME + ".jar")) {
                        lines.add("-libraryjars " + file.getAbsolutePath());
                    }
                }

                StringBuilder sb = new StringBuilder();
                for(var line : lines) {
                    sb.append(line).append("\n");
                }

                Files.writeString(pathProguardUse, sb.toString());
            } catch (IOException e) {
                e.printStackTrace();
            }

            new Thread(()->{
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
                if (Files.exists(pathProguardUse)) {
                    do {
                        var srcJar = IO.combinePath(Cfg.BUILD_ROOT, Cfg.MY_LIBS_DIR, Cfg.MAIN_MODULE_NAME + ".jar"); // TODO 多模块
                        var targetJar = IO.combinePath(Cfg.BUILD_ROOT, Cfg.MY_LIBS_DIR, Cfg.MAIN_MODULE_NAME + "_cvt.jar"); // TODO 多模块

                        String cmd;
                        if (IO.IS_WIN) {
                            var atPro = "\"@proguard_use.pro\"";
                            cmd = Cfg.java + " -jar " + Cfg.proguardBinOrJar[1] + " " + atPro + " -injars " + srcJar + " -outjars " + targetJar;
                        } else {
                            var atPro = "@proguard_use.pro";
                            cmd = Cfg.proguardBinOrJar[0] + " " + atPro + " -injars " + srcJar + " -outjars " + targetJar;
                        }

                        System.out.println("执行混淆命令为：" + cmd);
                        List<String> ss = IO.runBig(cmd, false);
                        //第一次混淆成功
                        if (Files.exists(Path.of(targetJar))) {
                            //统计map
                            try {
                                getOutDotMap();
                            } catch (IOException e) {
                                e.printStackTrace();
                            }

                            try {
                                if (replaceOrig) {
                                    Files.delete(Path.of(srcJar));
                                    Files.move(Path.of(targetJar), Path.of(srcJar));
                                }
                                System.out.println("!!!>>>### 混淆成功@@!!： " + srcJar);
                            } catch (IOException e) {
                                e.printStackTrace();
                                System.out.println("Error: 混淆 " + srcJar);
                            }
                        } else {
                            StringBuilder sblog = new StringBuilder();
                            ss.forEach(s -> sblog.append(s).append("\n"));
                            System.out.println(sblog);
                            System.out.println("Error: 混淆失败。不存在混淆后的文件 " + targetJar);
                        }
                    } while(false);

                    try {
                       if(deleteProguardUse) Files.delete(pathProguardUse);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                } else {
                    System.out.println("竟然没有生成文件。");
                }

                end.invoke();
            }).start();
        } else {
            System.out.println("混淆，跳过！");
        }
    }

    static final String JPACKAGE_MODULE_PATH_SPLIT = IO.IS_WIN ? ";" : ":";
    static void func7() {
        if (Cfg.step7_jpackage) {
            System.out.println(jumpWords(true, 7, "打包程序"));

            new ZipCompressor("proguardMap.zip").compress("out.map");
            try {
                Files.delete(Paths.get("out.map"));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }

            System.out.println("请注意：！！！！\ncd到" + Cfg.BUILD_ROOT + "\" !!!!后，自行复制如下命令去执行。\n执行以后，他会有很长的时间，耐心等待...");
            String iconPath = IO.IS_WIN ? "../icons/windows.ico" : "../icons/mac.icns";

            var cmd = Cfg.jpackage + " -n " + Cfg.APP_NAME
                    + " --icon " + iconPath
                    + " -i resources"
                    //+ " --file-associations " + IO.combinePath("..", "FileAssociations", "FA.txt")
                    + " -p " + "\"" + Cfg.THIRD_LIBS_DIR + JPACKAGE_MODULE_PATH_SPLIT + Cfg.MY_LIBS_DIR + "\"";
            var vmOption = getVMOptions();
            if (vmOption != null) {
                cmd += " --java-options " + vmOption;
            }
            cmd += " --runtime-image " + Cfg.MINIJRE_DIR;
            cmd += " --app-version " + Cfg.VERSION;
            cmd += " -m " + Cfg.MAIN_MODULE_NAME + "/" + Cfg.MAIN_CLASS;

            cmd = cmd.replace("-DA_DEBUG=true ", "")
                    .replace("-DA_MEM_WATCHER=print ", "")
                    .replace("-DA_MEM_WATCHER=real ", "");
            System.out.println(cmd);

            String finalCmd = cmd;
            new Thread(()-> {
                try {
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    Files.move(Path.of("proguardMap.zip"), Path.of(IO.combinePath(Cfg.BUILD_ROOT, "resources", "resources", "pro.map")));
                    Files.writeString(Path.of(IO.combinePath(Cfg.BUILD_ROOT, "jpackageCmd.sh")), finalCmd);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }).start();
        } else {
            System.out.println(jumpWords(false, 7, "打包程序"));
        }
    }

    static String jumpWords(boolean runOrIgnore, int step, String name) {
        return String.format("第%d步[%s]: ", step, name) + (runOrIgnore ? " 执行...." : " 跳过。");
    }

    static String wordsCompleted(int step, String name) {
        return String.format("第%d步[%s]: ", step, name) + "完成！";
    }

    //找出所有依赖的第三方库
    static Set<String> findAllThirdJars() {
        //1. 最简单的，从本地的隐藏目录加载但是有可能这个目录不存在；
        //可以通过刷新来解决File->Invalidate.caches->... 或者 git clean -dfx然后重新导入一次。
        Set<String> allJars = new HashSet<>();

        var libDir = new File(IO.combinePath(".idea", "libraries"));
        if (libDir.exists()) {
            var libfiles = libDir.listFiles();
            assert libfiles != null;
            for (var libfile : libfiles) {
                try {
                    var lines = Files.readAllLines(Paths.get(libfile.getAbsolutePath()));
                    for (var line : lines) {
                        //<root url="jar://$MAVEN_REPOSITORY$/com/google/code/gson/gson/2.8.6/gson-2.8.6.jar!/" />
                        if (line.contains(".jar!")
                                && !line.contains("-javadoc.jar!")
                                && !line.contains("-sources.jar!")) {
                            var mr = "$MAVEN_REPOSITORY$";
                            line = line.substring(line.indexOf(mr)  + mr.length() + 1, line.lastIndexOf(".jar!") + 4);
                            line = Cfg.M2_PATH + line;
                            if (IO.IS_WIN) {
                                line = line.replace("/", "\\");
                            }
                            allJars.add(line);
                        }
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        //2. pom.xml中解析。无法把依赖的弄出来。只能从cache中找工程文件。
        if (allJars.size() == 0) {
            if (new File(Cfg.IDEA_CACHE_libraries_xml_PATH).exists()) {
                try {
                    var allLines = Files.readAllLines(Path.of(Cfg.IDEA_CACHE_libraries_xml_PATH));
                    allLines.forEach(line -> {
                        if (line.contains(".jar!")
                                && !line.contains("-javadoc.jar!")
                                && !line.contains("-sources.jar!")) {
                            var mr = "$MAVEN_REPOSITORY$";
                            line = line.substring(line.indexOf(mr)  + mr.length() + 1, line.lastIndexOf(".jar!") + 4);
                            line = Cfg.M2_PATH + line;
                            if (IO.IS_WIN) {
                                line = line.replace("/", "\\");
                            }
                            allJars.add(line);
                        }
                    });
                } catch (IOException e) {
                    e.printStackTrace();
                }
            } else {
                System.out.println("》》打包程序检查依赖的libs出错：解决方案《《");
                System.out.println("""
                        1. 从本工程.idea/libraries/目录，取所有文件每个文件单独的指定了一个依赖包；
                        脚本发现没有本目录，这是idea决定的没有办法，还有第二种方法；
                        """.indent(4));
                System.out.println("""
                        2. 从cache目录，取类似如下文件路径
                        /Users/allan/Library/Caches/JetBrains/IdeaIC202x.x/external_build_system/[currentProjectName].a23c3f67/project/libraries.xml
                        请自行参考上述目录文件，修改代码 Cfg.IDEA_CACHE_libraries_xml_PATH为上述类似的工程文件。
                        并且请注意Idea升级了或者工程导入了多次，可能存在多个目录。仔细核对。
                        """.indent(4));
            }
        }

        //3. 还不行？？再见。
        if (allJars.size() == 0) {
            throw new RuntimeException("无法工作了。依赖jar检测出现问题。请检查。");
        }
        return allJars;
    }

    static Set<String> fromJarFilesGetModuleNames() {
        var thirdModuleNames = new HashSet<String>();
        var thirdJarsPath = new File(IO.combinePathWithInclineEnd(Cfg.BUILD_ROOT, Cfg.THIRD_LIBS_DIR)).listFiles();
        if (thirdJarsPath == null || thirdJarsPath.length == 0) {
            System.out.println("你似乎没有第三方库？确实没有的话，忽略。否则检查错误。");
        } else {
            for (var thirdJar : thirdJarsPath) {
                ModuleFinder finder = ModuleFinder.of(Paths.get(thirdJar.getAbsolutePath()));
                Set<ModuleReference> moduleReferences = finder.findAll();
                Set<String> oneModNm = //因为只放了一个。所以只会有一个。
                        moduleReferences.stream().map(r -> r.descriptor().name()).collect(Collectors.toSet());
                if (oneModNm.size() != 1) {
                    System.out.println("检查这个模块是否有点问题： " + thirdJar);
                }
                thirdModuleNames.addAll(oneModNm);
            }
        }
        return thirdModuleNames;
    }

    static Set<String> findAllModuleNames() {
        return findAllModuleNames(findAllModuleInfoJavas());
    }

    static Set<String> findAllModuleNames(Set<File> moduleInfoFiles) {
        var moduleNames = new HashSet<String>();
        for (var file : moduleInfoFiles) {
            try {
                var lines = Files.readAllLines(Paths.get(file.getAbsolutePath()));
                for (var line : lines) {
                    if (line.contains("module ")) {
                        String[] splits = line.split(" ");
                        if (splits.length > 3) {//检查你的module-info.java的module那一行，怎么会分段多次
                            throw new RuntimeException("自行处理你的module获取方式吧");
                        }
                        String target = splits[1];
                        moduleNames.add(target);
                        break;
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return moduleNames;
    }

    static Set<File> findAllModuleInfoJavas() {
        return findAllModuleInfoJava(new File("."));
    }

    static Set<File> findAllModuleInfoJava(File dir) {
        var moduleInfoJavas = new HashSet<File>();
        IO.getAllFilesInDirWithFilter(moduleInfoJavas,
                dir,
                //过滤我要的文件名
                f-> "module-info.java".equals(f.getName()),
                d -> { //过滤目录名和编译结果
                    var n = d.getName();
                    if (".idea".equals(n)) {
                        return false;
                    }
                    if (".git".equals(n)) {
                        return false;
                    }
                    if ("resources".equals(n)) {
                        return false;
                    }
                    if (Cfg.BUILD_ROOT.equals(n)) {
                        return false;
                    }
                    var p = d.getAbsolutePath();
                    if (p.contains("target") && p.contains("classes")) {
                        return false;
                    }
                    return true;
                });
        return moduleInfoJavas;
    }

    static List<String> requiresCount(String options) {
        String r = IO.run(options);
        assert r != null;
        String err = null;
        List<String> retList = null;

        if (r.contains("Exception") || r.contains("not found") || r.contains("错误") || r.contains("Error") || r.contains("failed")) {
            err = "    运行jdeps命令出错: " + options + "\n    " + r;
        } else {
            String[] lines = r.split("\n");
            List<String> ret = new ArrayList<>();
            int hasRequiresCount = 0;
            for (String line : lines) {
                if (!line.contains("requires")) {
                    continue;
                }
                hasRequiresCount++;
                String [] splits = line.split("\\s+");
                String t;
                if (splits[splits.length - 1].contains("@")) {
                    t = splits[splits.length - 2];
                } else {
                    t = splits[splits.length - 1];
                }
                if (t.length() > 0) ret.add(t);
            }

            if (hasRequiresCount != ret.size()) {
                err = ("    需要检查，为何requires的计算出现了错误，数量不对称。\n    " + r);
            }

            retList = ret.stream().distinct().collect(Collectors.toList());
            System.out.println("    结果为：" + String.join(",", retList));
        }

        if (err == null) {
            return retList;
        }

        System.out.println(err);
        return null;
    }

    static String getVMOptions() {
        String vmOption = null;
        try {
            var lines = Files.readAllLines(Paths.get(IO.combinePath(".idea", "workspace.xml")));
            var vmOptions = lines.stream().filter(l-> l.contains("VM_PARAMETERS")).collect(Collectors.toSet());
            if (vmOptions.size() != 1) {
                System.out.println("请检查你的VMOptions是否正确配置。\n" +
                        "Run->Edit Configurations...-> Application->xxx->Modify Options-> add VM Options." +
                        "如果你确认你不需要vmOption可以修改此处代码。\n" +
                        "将如下throw注释。");
                throw new RuntimeException("竟然没有vmOptions？");
            } else {
                var first = vmOptions.iterator().next();
                first = first.replaceFirst("<option name=\"VM_PARAMETERS\" value=", "");
                vmOption = first.substring(0, first.length() - 3).trim();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return vmOption;
    }
}

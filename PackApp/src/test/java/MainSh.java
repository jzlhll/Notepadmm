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
            var cur = new File(".");
            IO.getAllFilesInDirWithFilter(moduleInfoClassPaths, cur,
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
//                        if (Objects.equals(d.getParent(), cur.getName()) && "build".equals(n)) {
//                            return false;
//                        }
                        return true;
                    });

            for (var file : moduleInfoClassPaths) {
                var targetClasses = file.getAbsolutePath().replace("module-info.class", "");
                var parentPath2 = IO.getParentPath(IO.getParentPath(targetClasses, true), true);

                var sets = findAllModuleInfoJava(new File(parentPath2));
                assert sets.size() == 1;
                var names = findAllModuleNames(sets);

                for (var name : names) {
                    System.out.println( "    模块: " + name + ", " + targetClasses);
                    var r= IO.run(Cfg.jar +
                            " --create --file " + IO.combinePath(Cfg.BUILD_ROOT, Cfg.MY_LIBS_DIR, name + ".jar") +
                            " --module-version 1.0" +
                            " -C " + targetClasses +
                            " .");
                    break; //todo 目前由于扫描问题，把重复的搞了进来。只取第一个就是app的。
                }
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

            System.out.println("最后一步：打包动作, 请打开文件目录，使用cmd命令打开进入到本目录\n" +
                    "请注意：！！！！\n" +
                    "cd到" + Cfg.BUILD_ROOT + "\" 后，执行xxx.sh或者xxx.bash!!");
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

            String finalCmd1 = cmd;
            String finalCmd2 = cmd;
            if (IO.IS_WIN) {
                finalCmd1 = cmd + " --vendor --win-dir-chooser --win-shortcut";
                finalCmd2 = cmd + " --type app-image --vendor raven";
                try {
                    Files.writeString(Path.of(IO.combinePath(Cfg.BUILD_ROOT, "jpackageCmdExe.sh")), finalCmd1);
                    Files.writeString(Path.of(IO.combinePath(Cfg.BUILD_ROOT, "jpackageCmdGreenExe.sh")), finalCmd2);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            } else {
                try {
                    Files.writeString(Path.of(IO.combinePath(Cfg.BUILD_ROOT, "jpackageCmd.sh")), cmd);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }

            try {
                Files.move(Path.of("proguardMap.zip"), Path.of(IO.combinePath(Cfg.BUILD_ROOT, "resources", "resources", "pro.map")));
            } catch (IOException e) {
                e.printStackTrace();
            }
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

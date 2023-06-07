import java.io.IOException;

public class Cfg {
    //todo: 不能的电脑，配置修改这里。
    private static final String CfgConfig = "./src/test/java/cfg_win.config";
    //运行入口
    static final String MAIN_CLASS;
    //主模块
    static final String MAIN_MODULE_NAME;
    static final String APP_NAME;
    static final String VERSION;

    //JAVA_HOME
    static final String JAVA_HOME;
    //maven的本地仓库 末尾的分隔符不得少
    static final String M2_PATH;

    /**
     * 如果是传入bin，就是proguard.bat或者.sh
     * 如果传入lib/proguard.jar，则是第二个参数。
     */
    static final String[] proguardBinOrJar;


    //如果本地没有.idea/library目录。则需要在类似如下目录的地方找到这个文件。 todo modify by you
    static final String IDEA_CACHE_libraries_xml_PATH;

    static String[] ALL_RES_PATHS = new String[] {
            //我的项目只有主工程有资源文件，故而资源目录放一个。你有多个就放多个
            IO.combinePath("src", "main", "resources")
    };

    static {
        ConfigReader reader = new ConfigReader();
        try {
            reader.parse(CfgConfig);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        MAIN_CLASS = reader.get("MAIN_CLASS");
        MAIN_MODULE_NAME = reader.get("MAIN_MODULE_NAME");
        APP_NAME = reader.get("APP_NAME");
        VERSION = reader.get("VERSION");

        {
            JAVA_HOME = reader.get("JDK_HOME");
        }

        M2_PATH = reader.get("M2_PATH");
        proguardBinOrJar = new String[]{null, null};
        proguardBinOrJar[0] = reader.get("proguardBin");
        var proguardJar = reader.get("proguardJar");
        proguardBinOrJar[1] = (proguardJar == null || proguardJar.length() == 0) ? null : proguardJar;
        IDEA_CACHE_libraries_xml_PATH = reader.get("IDEA_CACHE_libraries_xml_PATH");
    }

    //控制执行步骤。step1_compile~4的动作与第二排5-7的动作必须分开来看。
    // 直到你统计后将jre的requires列在这里以后才能继续后面5-7步骤。
    //前期： 调试脚本，尝试的过程，先逐步调试1~4；
    //后期： 在没有新的引入库，没有新的requires module，只有代码逻辑改动的时候： 第一排全部false，第二排的5~7为true即可。
    static boolean step1_compile = false, step2_copyLibs = false, step3_copyRes = false;
    static boolean step4_deps = false, step5_miniJre = false;
    static boolean step6_jar = true, step6_proguard = true, step7_jpackage = true;

    static final String BUILD_ROOT = "buildRoot";
    //如下是BUILD_ROOT目录下一层
    static final String THIRD_LIBS_DIR = "thirdLibs";
    static final String MY_LIBS_DIR = "myLibs";
    static final String MINIJRE_DIR = "minijre";

    static final String ICON_PATH = IO.IS_WIN ? "" : (IO.IS_OSX ? "icons/mac.icns" : ""); //图标位置

    static final String JMODE_PATH = IO.combinePath(JAVA_HOME, "jmods");


    static final String jmod = IO.combinePath(JAVA_HOME, "bin", "jmod");

    static final String jpackage = IO.combinePath(JAVA_HOME, "bin", "jpackage");
    static final String jar = IO.combinePath(JAVA_HOME, "bin", "jar");
    static final String jlink = IO.combinePath(JAVA_HOME, "bin", "jlink");
    static final String jdeps = IO.combinePath(JAVA_HOME, "bin", "jdeps");
    static final String java = IO.combinePath(JAVA_HOME, "bin", "java");

    static final String[] subTargetClasses = new String[] {
            IO.combinePath("BaseParty", "target", "classes"),
            IO.combinePath("BaseUiLibs", "target", "classes"),
            //TODO 修改和追加其他子模块。不带最后一个/
    };

}

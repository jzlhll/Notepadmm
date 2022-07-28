import java.io.IOException;
import java.util.List;

public class MainShAll {
    public static void main(String[] args) throws IOException {
        Cfg.step1_compile = true;
        Cfg.step2_copyLibs = true;
        Cfg.step3_copyRes = true;
        Cfg.step4_deps = true;
        Cfg.step5_miniJre = true;
        Cfg.step6_jar = true;
        Cfg.step6_proguard = true;
        Cfg.step7_jpackage = true;

        mainAction();
        MainSh.func4_packMiniJre(miniJreDeps);
        System.out.println("================================");
        System.out.println();

        /////777
        MainSh.func7();
    }

    static List<String> miniJreDeps;
    public static void mainAction() {
        IO.currentDirPrint();

        /////111
        MainSh.func1();
        System.out.println();

        System.out.println("================================");

        /////222
        MainSh.func2();
        System.out.println("================================");
        System.out.println();

        ///333
        MainSh.func3_res();
        System.out.println("================================");
        System.out.println();

        /////444
        var miniJreDeps = MainSh.func4_depsCounter();
        var miniJreDepsStr = miniJreDeps == null ? null : String.join(",", miniJreDeps);
        MainShAll.miniJreDeps = miniJreDeps;
        if(miniJreDepsStr != null) System.out.println("    有必要的时候检查下第三步的结果是否有用：" + miniJreDepsStr);

        System.out.println("================================");
        System.out.println();

        /////666
        MainSh.func6_jarPack();
        System.out.println("================================");
        System.out.println();
        MainSh.func6_5_proguard(miniJreDeps, true, true);
        System.out.println("================================");
        System.out.println();
    }
}

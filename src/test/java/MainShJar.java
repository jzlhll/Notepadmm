import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

public class MainShJar {
    public static void main(String[] args) {
        Cfg.step6_proguard = true;
        Cfg.step6_jar = true;
        MainSh.func6_jarPack();
        var param = new MainSh.ProguardParam();
        param.replaceOrigJar = false;
        param.saveToFileOrRead = false;
        param.deleteProguard_use = false;
        MainSh.func6_5_proguard(null, param, ()->{});
    }
}

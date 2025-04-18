public class MainShJar {
    public static void main(String[] args) {
        Cfg.step6_proguard = true;
        Cfg.step6_jar = true;
        var param = new MainSh.ProguardParam();
        param.replaceOrigJar = false;
        param.saveToFileOrRead = false;
        param.deleteProguard_use = false;
        MainSh.func6_5_proguard(null, param, ()->{});
    }
}

public class MainShJar {
    public static void main(String[] args) {
        Cfg.step6_proguard = true;
        Cfg.step6_jar = true;
        MainSh.func6_jarPack();
        MainSh.func6_5_proguard(null, false, false);
    }
}

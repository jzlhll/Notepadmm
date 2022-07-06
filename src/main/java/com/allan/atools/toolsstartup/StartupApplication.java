package com.allan.atools.toolsstartup;

import com.allan.atools.toolsstartupimpl.StartupNotepadInitImp;
import com.allan.atools.utils.DirectFileLog;
import javafx.application.Application;
import javafx.stage.Stage;

public final class StartupApplication extends Application{
    private IStartupInit create() {
        try {
            return new StartupNotepadInitImp();
        } catch (Throwable e) {
            e.printStackTrace();
        }
        return null;
    }

//    private IStartupInit create() {
//        try {
//            var clazz = getStartupEntroClass();
//            Object obj = clazz.getConstructor().newInstance();
//            return (IStartupInit) obj;
//        } catch (Throwable e) {
//            e.printStackTrace();
//        }
//        return null;
//    }

//    private static Class<?> getStartupEntroClass() {
//        var list = ClassUtil.getClasses("com.allan.entro", false);
//        assert list.size() > 0;
//        for (Class<?> cls : list) {
//            if (cls.getAnnotation(StartupEntro.class) != null) {
//                return cls;
//            }
//        }
//        return null;
//    }

    @Override
    public void start(Stage stage) {
        var init = create();
        DirectFileLog.append2("start111");
        assert init != null;
        init.beforeStart(stage);
        DirectFileLog.append2("beforeStart aftaer");

        //标记主程序
        init.createMainView(stage);
        DirectFileLog.append2("createMainView aftaer");
        stage.show();
    }

}

package com.allan.atools.toolsstartup;

import com.allan.atools.toolsstartupimpl.StartupNotepadInitImp;
import com.allan.atools.utils.Log;
import javafx.application.Application;
import javafx.stage.Stage;

public final class StartupApplication extends Application{
    private IStartupInit create() {
        try {
            return new StartupNotepadInitImp();
        } catch (Throwable e) {
            Log.e("startup: initializer creation failed", e);
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
    public void init() {
        Log.e("startup: JavaFX application init");
    }

    @Override
    public void start(Stage stage) {
        Log.e("startup: JavaFX application start begin");
        try {
            var init = create();
            Log.e("startup: initializer created");
            assert init != null;
            init.beforeStart(stage);
            Log.e("startup: beforeStart completed");

            //标记主程序
            init.createMainView(stage);
            Log.e("startup: main view created");
            stage.show();
            Log.e("startup: main stage shown");
        } catch (RuntimeException | Error e) {
            Log.e("startup: JavaFX application start failed", e);
            throw e;
        }
    }

}

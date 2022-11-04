package com.allan.atools.bases;

import com.allan.atools.utils.Locales;
import com.allan.atools.utils.Log;
import com.allan.atools.utils.ResLocation;
import com.allan.baseparty.memory.RefWatcher;
import javafx.fxml.FXMLLoader;
import javafx.fxml.JavaFXBuilderFactory;
import javafx.scene.Parent;
import javafx.stage.Stage;

import java.io.IOException;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.net.URL;

public abstract class AbstractController {
    private Parent mParent;
    private Stage mStage;

    public final void setRootView(Parent view) {
        this.mParent = view;
    }

    public final Parent getRootView() {
        return this.mParent;
    }

    /**
     * 当一个control第一次加载的时候触发。
     */
    public void init(Stage stage) {
        mStage = stage;
    }

    public final Stage getStage() {
        return mStage;
    }

    public static <T extends AbstractController> T load(Class<T> clazz) throws IOException {
        var annotation = clazz.getAnnotationsByType(XmlPaths.class) [0];
        var url = ResLocation.getURL(annotation.paths());
        return load(url);
    }

    /*private static <T extends AbstractController> T loadtest() throws IOException {

         如下列举了5种方法获取类。结果都获取的是AbstractController
         因为只有父类才有。
         * public static void testGetClassName()
              {
                  // 方法1：通过SecurityManager的保护方法getClassContext()
                  String clazzName = new SecurityManager()
                  {
                      public String getClassName()
                      {
                          return getClassContext()[1].getName();
                      }
                  }.getClassName();
                  System.out.println(clazzName);
                  // 方法2：通过Throwable的方法getStackTrace()
                  String clazzName2 = new Throwable().getStackTrace()[1].getClassName();
                  System.out.println(clazzName2);
                  // 方法3：通过分析匿名类名称()
                  String clazzName3 = new Object()    {
                      public String getClassName()
                      {
                          String clazzName = this.getClass().getName();
                          return clazzName.substring(0, clazzName.lastIndexOf('$'));
                      }
                  }.getClassName();
                  System.out.println(clazzName3);
              }
              * //方法4：Available only since Java 7 and Android API 26!
              *  MethodHandles.lookup().lookupClass();
              * 方法5：
              * new Object(){}.getClass().getEnclosingClass();
              *
              方法1：219ms
    方法2：953ms
    方法3：31ms
        改成泛型获取
        return null;
    }*/

    public static <T extends AbstractController> T loadPath(String... paths) throws IOException {
        return load(ResLocation.getURL(paths));
    }

    private static <T extends AbstractController> T load(URL xmlPath) throws IOException {
        FXMLLoader fxmlLoader = new FXMLLoader();
        fxmlLoader.setResources(Locales.getResource());
        fxmlLoader.setLocation(xmlPath);
        fxmlLoader.setBuilderFactory(new JavaFXBuilderFactory());
        Parent view = fxmlLoader.load();
        T c = fxmlLoader.getController();
        if (c != null) {
            c.setRootView(view);
        }

        RefWatcher.watchs(c, xmlPath.toString());
        return c;
    }

//    public static Parent createViewByFxml(URL xmlPath)  throws IOException{
//        FXMLLoader fxmlLoader = new FXMLLoader();
//        fxmlLoader.setResources(Locales.getResource());
//        fxmlLoader.setLocation(xmlPath);
//        fxmlLoader.setBuilderFactory(new JavaFXBuilderFactory());
//        return fxmlLoader.load();
//    }

    public void destroy() {
        mParent = null;
        mStage = null;
    }
}

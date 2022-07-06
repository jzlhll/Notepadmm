module atools {
    requires atools.baseparty;
    requires atools.baseuilibs;

    requires java.base;
    requires java.desktop;
    requires jdk.charsets;

    requires javafx.base;
    requires javafx.fxml;
    requires javafx.controls;
    requires javafx.graphics;

    requires org.fxmisc.richtext;
    requires org.fxmisc.flowless;
    requires wellbehavedfx;
    requires reactfx;
    requires com.google.gson;
    requires com.jfoenix;
    requires org.fxmisc.undo;
    requires org.jetbrains.annotations;
    requires kotlin.stdlib;

    exports com.allan.atools.bean;
    opens com.allan.atools.bean         to com.google.gson;

    opens com.allan.atools.ui.controls  to com.jfoenix, javafx.base, javafx.controls, javafx.fxml, javafx.graphics;
    opens com.allan.atools.toolsstartup to com.jfoenix, javafx.base, javafx.controls, javafx.fxml, javafx.graphics;
    opens com.allan.atools.controller   to com.jfoenix, javafx.base, javafx.controls, javafx.fxml, javafx.graphics;
    opens com.allan.atools.richtext     to com.jfoenix, javafx.base, javafx.controls, javafx.fxml, javafx.graphics;
}
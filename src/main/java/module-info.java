module atools {
//    requires org.burningwave.core;

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
    requires annotations;
    requires kotlin.stdlib;
    requires org.objectweb.asm;

    exports com.allan.atools.bean;

    opens com.allan.atools.bean         to com.google.gson;

    exports com.allan.uilibs.controls;
    exports com.allan.uilibs.richtexts;
    exports com.allan.uilibs.jfoenix;

    exports com.allan.baseparty;
    exports com.allan.baseparty.utils;
    exports com.allan.baseparty.content;
    exports com.allan.baseparty.handler;
    exports com.allan.baseparty.memory;
    exports com.allan.baseparty.exception;
    exports com.allan.baseparty.collections;

    opens com.allan.atools.ui.controls  to com.jfoenix, javafx.base, javafx.controls, javafx.fxml, javafx.graphics;
    opens com.allan.atools.toolsstartup to com.jfoenix, javafx.base, javafx.controls, javafx.fxml, javafx.graphics;
    opens com.allan.atools.controller   to com.jfoenix, javafx.base, javafx.controls, javafx.fxml, javafx.graphics;
    opens com.allan.atools.richtext     to com.jfoenix, javafx.base, javafx.controls, javafx.fxml, javafx.graphics;

    exports com.allan.atools.toolsstartup;
}
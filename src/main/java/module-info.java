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
    requires javafx.swing;
    requires javafx.web;
    requires jdk.jsobject;

    requires com.twelvemonkeys.imageio.webp;
    requires org.commonmark;
    requires org.commonmark.ext.gfm.tables;
    requires org.commonmark.ext.gfm.strikethrough;
    requires org.commonmark.ext.autolink;
    requires org.commonmark.ext.task.list.items;
    requires org.commonmark.ext.footnotes;
    requires org.commonmark.ext.front.matter;
    requires org.jsoup;
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
    opens com.allan.atools.tools        to com.jfoenix, javafx.base, javafx.controls, javafx.fxml, javafx.graphics;
    opens com.allan.atools.tools.modulenotepad.manager to javafx.web;
    opens com.allan.atools.tools.modulenotepad.session to com.google.gson;
}

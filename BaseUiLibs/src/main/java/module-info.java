module atools.baseuilibs {
    requires atools.baseparty;

    requires org.fxmisc.undo;
    requires java.base;
    requires javafx.graphics;
    requires com.jfoenix;
    requires javafx.controls;
    requires org.fxmisc.richtext;
    requires org.fxmisc.flowless;
    requires reactfx;

    exports com.allan.uilibs.controls;
    exports com.allan.uilibs.richtexts;
    exports com.allan.uilibs.jfoenix;
}
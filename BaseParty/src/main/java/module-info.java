module atools.baseparty {
    requires java.base;

    requires org.jetbrains.annotations;
    requires com.google.gson;

    exports com.allan.baseparty;
    exports com.allan.baseparty.utils;
    exports com.allan.baseparty.content;
    exports com.allan.baseparty.handler;
    exports com.allan.baseparty.memory;
    exports com.allan.baseparty.exception;
    exports com.allan.baseparty.collections;
}
package com.allan.atools.richtext.codearea;

import com.allan.baseparty.ActionR;

import java.io.File;

final class EditorKeywordHelperFactory {
    private EditorKeywordHelperFactory() {}

//    static AbstractEditorKeywordHelper createKeywordHelper(String extension) {
//        return switch (extension) {
//            case "java" -> new EditorKeywordHelperImplJava();
//            case "cs" -> new EditorKeywordHelperImplCSharp();
//            case "c", "cpp", "h" -> new EditorKeywordHelperImplCC();
//            case "xml" -> new EditorKeywordHelperImplXml();
//            case "module-info" -> new EditorKeywordHelperImplModuleInfo();
//            default -> null; //no case
//        };
//    }

    static EditorKeywordHelperAbstract create(File file) {
        return switch (sFilePathToExtension.invoke(file)) {
            case "java" -> new EditorKeywordHelperImplJava();
            case "cs" -> new EditorKeywordHelperImplCSharp();
            case "c", "cpp", "h" -> new EditorKeywordHelperImplCC();
            case "xml" -> new EditorKeywordHelperImplXml();
            case "module-info" -> new EditorKeywordHelperImplModuleInfo();
            default -> null; //no case
        };
    }

    static ActionR<File, String> sFilePathToExtension = (file) -> {
        if(file.getName().equalsIgnoreCase("module-info.java")) {
            return "module-info";
        }
        int lastIndexOf = file.getName().lastIndexOf(".");
        //获取文件的后缀名
        var suffix = file.getName().substring(lastIndexOf + 1);
        if (suffix.equalsIgnoreCase("java")) {
            return "java";
        }
        if (suffix.equalsIgnoreCase("cs")) {
            return "cs";
        }
        if (suffix.equalsIgnoreCase("cpp") || suffix.equalsIgnoreCase("cc")
                || suffix.equalsIgnoreCase("hpp") || suffix.equalsIgnoreCase("h")
                || suffix.equalsIgnoreCase("c")) {
            return "cpp";
        }
        if (suffix.toLowerCase().endsWith("xml")) {
            return "xml";
        }
        return null;
    };
}

package com.allan.atools.tools.lua.parse;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

public final class LuaTab {
    //已经是没有回车,没有注释的文本
    private String dealText;
    private LuaTabImp tab;

    private void prepare(String text) {
        text = text.trim();
        if (text.endsWith(",")) {
            text = text.substring(0, text.length() - 1);
        }

        var lines = text.split("\n");

        boolean isZhushiStarted = false;
        StringBuilder sb = new StringBuilder();

        for(int i = 0; i < lines.length; i++) {
            lines[i] = lines[i].trim();
            if (lines[i].startsWith("--")) {
                //do nothing
            } else if (lines[i].startsWith(("--[["))) {
                isZhushiStarted = true;
            } else if (lines[i].startsWith(("--]]"))) {
                isZhushiStarted = false;
            } else if (isZhushiStarted) {
                //do nothing
            } else {
                sb.append(lines[i]);
            }
        }

        dealText = sb.toString();
    }

    public void parse(String text) throws LuaParseException {
        prepare(text);

        tab = new LuaTabImp(dealText);
    }

    public static void main(String[] args) {
        try {
            var s = Files.readString(Paths.get("/Users/allan/Downloads/luatab.txt"));
            LuaTab parser = new LuaTab();
            parser.parse(s);
        } catch (IOException | LuaParseException e) {
            e.printStackTrace();
        }
    }
}

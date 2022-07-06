package com.allan.atools.tools.lua.parse;

import java.util.ArrayList;
final class LuaTabImp {
    public final String originalText;

    public String value;
    public String key;
    public ArrayList<LuaTabImp> nodes = new ArrayList<>();

    public LuaTabImp(String text) throws LuaParseException {
        originalText = text;
        parse();
    }

    private void parse() throws LuaParseException {
        String trimmedText = originalText.trim();
        if (trimmedText.endsWith(",")) {
            trimmedText = trimmedText.substring(0, trimmedText.length() - 1);
        }
        value = trimmedText;
        genKeyAndMayChangeValue();
        value = value.substring(1, value.length() - 1);
        parseChildren();
    }

    private class Provider implements WordsParser.ICharsProvider {
        private int index = 0;
        private int startIndex = 0;
        private int endIndex = 0;
        private String endStr;

        @Override
        public Character readNext() {
            if (index == value.length()) {
                return null;
            }
            return value.charAt(index++);
        }

        @Override
        public void markStart() {
            startIndex = index;
        }

        @Override
        public void markEnd() {
            endIndex = index;
            endStr = value.substring(startIndex, endIndex);
        }
    }

    /**
     * 目的是将识别为一个table或者一份元素
     */
    private void parseChildren() throws LuaParseException {
        Provider provider = new Provider();
        WordsParser wordsParser = new WordsParser(provider);

        while (true) {
            boolean isEnd = wordsParser.parse();
            if (provider.endStr != null && provider.endStr.length() > 0) {
                nodes.add(new LuaTabImp(provider.endStr));
            }
            if (!isEnd) {
                break;
            }
        }
    }

    private void genKeyAndMayChangeValue() throws LuaParseException {
        //1. [key] = {..}
        //2. ["key"] = {..}
        //3. key = {..}
        //4. "key" = {...}
        //5. {}
        StringBuilder sb = new StringBuilder();
        boolean isEq = false;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '=') {
                isEq = true;
            } else if (c == '{') {
                if (isEq) {
                    //1~4 "key" = 或者 [key] = 或者 key =  或者 ["key"] =
                    value = value.substring(i); //将value截断
                    key = matchKey(sb.toString());
                    return;
                } else {
                    if (i != 0) {
                        throw new LuaParseException("error tab ex");
                    }
                    //5.
                    key = null;
                    return;
                }
            }
            sb.append(c);
        }

        throw new LuaParseException("error tab!");
    }

    private static String matchKey(String words) {
        //1~4 "key" = 或者 [key] = 或者 key =  或者 ["key"] =
        words = words.trim();
        words = words.substring(0, words.length() - 1);
        words = words.trim(); //"key"或者 [key] 或者 key 或者 ["key"]  追加一种[  "key" ], [ key  ]
        if (words.startsWith("[\"") && words.endsWith("\"]")) {
            return words.substring(2, words.length() - 2);
        }

        if (words.startsWith("[") && words.endsWith("]")) {
            var r = words.substring(1, words.length() - 1);
            r = r.trim(); //追加"key",或者key
            if (r.startsWith("\"") && r.endsWith("\"")) {
                return r.substring(1, r.length() - 1);
            }
            return r;
        }

        if (words.startsWith("\"") && words.endsWith("\"")) {
            return words.substring(1, words.length() - 1); //不能trim，可能key就是包含前后空格
        }

        return words.trim();
    }

}

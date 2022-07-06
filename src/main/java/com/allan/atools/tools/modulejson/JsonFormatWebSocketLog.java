package com.allan.atools.tools.modulejson;

import com.allan.atools.utils.Log;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;

public class JsonFormatWebSocketLog implements IJsonFormat {
    private static Gson beautifulGson;

    public String formatWebSocket(String log) {
        return formatInner(log, "receive text--", true);
    }

    private static final String SPACE = "   ";

    public String formatWebSocketAppendEnd(String log) {
        return formatInner(log, "receive text--", false);
    }

    public String formatAio(String log) {
        return formatInner(log, "UNetDelegate.cpp:onPartEnd", false);
    }

    public String formatWithoutEnter(String log) {
        log = log.replace("\n", "");
        return formatJson(log);
    }

    private String formatJson(String realJson) {
        System.out.println("==============");
        Log.largeLog(realJson);
        System.out.println("==============");

        Gson gson = new Gson();
        JsonElement fe = (JsonElement) gson.fromJson(realJson, JsonElement.class);
        Log.largeLog(fe.toString());

        System.out.println("-----------");
        return printAsJsonBeautifulGson(fe);
    }

    private String formatInner(String origin, String beContainedStr, boolean withEnd) {
        String[] lines = origin.split("\n");
        StringBuilder sb = new StringBuilder();

        for (String line : lines) {
            if (line != null && line.trim().length() > 0 && line.contains(beContainedStr)) {
                int i = line.indexOf("::");
                String sub = line.substring(i + 2);
                sb.append(sub);
            }
        }

        if (withEnd) sb.append("\"}");

        return formatJson(sb.toString());
    }


    static String printAsJsonBeautifulGson(Object json) {
        if (beautifulGson == null) {
            beautifulGson = (new GsonBuilder()).setPrettyPrinting().create();
        }

        String newjson = beautifulGson.toJson(json);
        Log.largeLogWithLine(newjson);
        return newjson;
    }

    static String printAsJsonBeautifulSelf(String json) {
        StringBuilder result = new StringBuilder();
        int length = json.length();
        int number = 0;
        char key = Character.MIN_VALUE;

        for (int i = 0; i < length; i++) {


            key = json.charAt(i);


            if (key == '[' || key == '{') {


                if (i - 1 > 0 && json.charAt(i - 1) == ':') {

                    result.append('\n');
                    result.append(indent(number));
                }


                result.append(key);


                result.append('\n');


                number++;
                result.append(indent(number));


            } else if (key == ']' || key == '}') {


                result.append('\n');


                number--;
                result.append(indent(number));


                result.append(key);


                if (i + 1 < length && json.charAt(i + 1) != ',') {
                    result.append('\n');


                }


            } else if (key == ',') {

                result.append(key);
                result.append('\n');
                result.append(indent(number));

            } else {

                result.append(key);
            }
        }
        return result.toString();
    }


    private static String indent(int number) {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < number; i++) {
            result.append("   ");
        }
        return result.toString();
    }
}
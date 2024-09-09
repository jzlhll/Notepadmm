package com.allan.atools.tools.modulejson;

import com.allan.atools.utils.Log;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.stream.MalformedJsonException;

public class JsonFormatLog implements IJsonFormat {
    private static Gson beautifulGson;

    private static final String SPACE = "   ";

    @Override
    public String removeEnter(String str) {
        var strs = str.split("\n");
        var sb = new StringBuilder();
        for (String s : strs) {
            sb.append(s.trim());
        }

        return sb.toString();
    }

    @Override
    public String removeFanxieExtraQuote(String str) {
        return removeEnter(str)
                .replace("\\", "")
                .replace(": \"{", ": {")
                .replace(":\"{", ":{")
                .replace("}\"}", "}}")
                .replace("}\",", "},")
                ;
    }

    @Override
    public String format(String realJson) {
        System.out.println("==============");
        Log.largeLog(realJson);
        System.out.println("==============");

        Gson gson = new Gson();
        JsonElement fe = null;
        try {
            fe = gson.fromJson(realJson, JsonElement.class);
        } catch (Throwable e) {
            e.printStackTrace();
        }

        Log.largeLog(fe.toString());

        System.out.println("-----------");
        return printAsJsonBeautifulGson(fe);
    }


    static String printAsJsonBeautifulGson(Object json) {
        if (beautifulGson == null) {
            beautifulGson = (new GsonBuilder()).setPrettyPrinting().create();
        }

        String newjson = beautifulGson.toJson(json);
        Log.largeLogWithLine(newjson);
        return newjson;
    }

    private static String indent(int number) {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < number; i++) {
            result.append("   ");
        }
        return result.toString();
    }
}
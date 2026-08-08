package com.allan.atools.tools.modulejson;

import com.allan.atools.utils.Log;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;

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
        var compactJson = removeEnter(str);
        var jsonElement = tryParse(compactJson);
        if (jsonElement != null) {
            if (jsonElement.isJsonPrimitive() && jsonElement.getAsJsonPrimitive().isString()) {
                var innerJsonElement = tryParse(jsonElement.getAsString());
                if (innerJsonElement != null) {
                    return innerJsonElement.toString();
                }
            }
            return jsonElement.toString();
        }

        var removedExtraQuote = compactJson
                .replace("\\", "")
                .replace(": \"{", ": {")
                .replace(":\"{", ":{")
                .replace("}\"}", "}}")
                .replace("}\",", "},");
        var removedExtraQuoteElement = tryParse(removedExtraQuote);
        return removedExtraQuoteElement == null ? compactJson : removedExtraQuoteElement.toString();
    }

    @Override
    public String format(String realJson) {
        System.out.println("==============");
        Log.largeLog(realJson);
        System.out.println("==============");

        var fe = tryParse(realJson);
        if (fe == null) {
            Log.e("json format failed");
            return realJson;
        }

        Log.largeLog(fe.toString());

        System.out.println("-----------");
        return printAsJsonBeautifulGson(fe);
    }

    private static JsonElement tryParse(String json) {
        try {
            return new Gson().fromJson(json, JsonElement.class);
        } catch (RuntimeException e) {
            return null;
        }
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

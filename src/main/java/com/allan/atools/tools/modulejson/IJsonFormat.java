package com.allan.atools.tools.modulejson;

public interface IJsonFormat {
    String format(String realJson);
    String removeEnter(String str);
    String removeFanxieExtraQuote(String str);
}
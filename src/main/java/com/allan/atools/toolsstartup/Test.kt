package com.allan.atools.toolsstartup

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken


class Test {
    fun test() {
        val gson = Gson()
        val list = listOf<String>("a1", "bb", "cc", "dd", "er124")
        val jsonStr = gson.toJson(list)

        println("jsonStr " + jsonStr)

        val collectionType: TypeToken<List<String>> = object : TypeToken<List<String>>() {}
        val newList = gson.fromJson(jsonStr, collectionType)
        println("newList " + newList)
    }
}
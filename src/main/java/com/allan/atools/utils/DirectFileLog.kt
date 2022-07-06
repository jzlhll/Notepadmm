package com.allan.atools.utils

import java.io.File
import java.io.FileWriter
import java.io.IOException
import java.io.RandomAccessFile

object DirectFileLog {
    private const val FILE = "/Users/allan/Downloads/a.log"
    private val file = File(FILE)
    private val DEBUG = false

    @Synchronized
    fun append1(s: String) {
        if (!DEBUG) {
            return
        }
        //FileWriter writer = new FileWriter(file, true);
        //            writer.write("test RandomAccessFile append \r\n");
        //            writer.close();
        try {
            RandomAccessFile(file, "rw").use { raf ->
                //将写文件指针移到文件尾。
                raf.seek(raf.length())
                raf.writeBytes(s + "\n")
            }
        } catch (e: IOException) {
            throw RuntimeException(e)
        }
    }

    @JvmStatic
    @Synchronized
    fun append2(s: String) {
        if (!DEBUG) {
            return
        }
        try {
            FileWriter(file, true).use { writer -> writer.write(Log.time() + ": " + s + "\n") }
        } catch (e: IOException) {
            throw RuntimeException(e)
        }
    }
}
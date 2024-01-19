package com.allan.atools.utils

import java.io.File

fun HashSet<File>.getAllFilesInDirWithFilter(
    dir: File,
    fileFilter: ((File?)->Boolean) ?= null,
    dirFilter: ((File?)->Boolean) ?= null,
) {
    val fs = dir.listFiles()
    if (fs != null) for (f in fs) {
        if (f.isDirectory) {
            if (dirFilter == null || dirFilter(f)) {
                this.getAllFilesInDirWithFilter(f, fileFilter, dirFilter)
            }
        } else if (f.isFile) {
            if (fileFilter == null || fileFilter(f)) this.add(f)
        }
    }
}
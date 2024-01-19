package com.allan.atools.android

import com.allan.atools.threads.ThreadUtils
import com.allan.atools.utils.Log
import com.allan.atools.utils.getAllFilesInDirWithFilter
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths

class UnusedLayout(unusedDir:String) {
    private val unusedDirFile = File(if(unusedDir.isNotBlank())unusedDir else "D:\\code\\typhur\\typhurApp")

    private val codesMap = hashMapOf<File, Array<String>>()
    private val layoutsMap = hashMapOf<File, Array<String>>()
    private val otherGoodMap = hashMapOf<File, Array<String>>()

    private fun initFileToMap(map:MutableMap<File, Array<String>>, file:File) {
        if (map.containsKey(file)) {
            Log.d("How this.....! $file")
        } else {
            val str = Files.readAllLines(Paths.get(file.absolutePath))
            map[file] = str.toTypedArray()
        }
    }

    private fun isResourceBeUsed(f:File) : Boolean {
        if (ignoreResource(f)) {
            Log.d(f.name + " --(ignore)-> " + f)
            return true
        }

        val nameDotIndex = f.name.lastIndexOf(".")
        val name = if (nameDotIndex > 0) {
            f.name.substring(0, nameDotIndex)
        } else {
            f.name
        }
        for (kv in codesMap) {
            val lines = kv.value
            for (line in lines) {
                if (line.contains(".$name") || line.contains(f.name)) {
                    Log.d(f.name + " --(use)-> " + kv.key)
                    return true
                }
            }
        }

        for (kv in layoutsMap) {
            val lines = kv.value
            for (line in lines) {
                if (line.contains("/$name")) {
                    Log.d(f.name + " --(use)-> " + kv.key)
                    return true
                }
            }
        }
        return false
    }

    private fun isLayoutBeUsed(f:File) : Boolean {
        if (ignoreResource(f)) {
            Log.d(f.name + " --(ignore)-> " + f)
            return true
        }

        val nameDotIndex = f.name.lastIndexOf(".")
        val noExName = if (nameDotIndex > 0) {
            f.name.substring(0, nameDotIndex)
        } else {
            f.name
        }
        var bindingName = f.name.replace(".xml", "").replace("_", "")
        bindingName += "Binding"
        bindingName = bindingName.lowercase()

        for (kv in codesMap) {
            val lines = kv.value
            for (line in lines) {
                if (line.contains(".$noExName") || line.lowercase().contains(bindingName)) {
                    Log.d(f.name + " --(use)-> " + kv.key)
                    return true
                }
            }
        }

        for (kv in layoutsMap) {
            val lines = kv.value
            for (line in lines) {
                if (line.contains("/$noExName")) {
                    Log.d(f.name + " --(use)-> " + kv.key)
                    return true
                }
            }
        }
        return false
    }

    private fun ignoreResource(f:File) : Boolean  {
        if (f.name == "AndroidManifest.xml") {
            return true
        }

        if (f.absolutePath.contains("\\res\\values") &&
            (f.name.endsWith("colors.xml")
                    || f.name.endsWith("attrs.xml")
                    || f.name.endsWith("styles.xml")
                    || f.name.endsWith("dimens.xml")
                    || f.name.endsWith("themes.xml")
                    || f.name.endsWith("color.xml")
                    || f.name.endsWith("attr.xml")
                    || f.name.endsWith("style.xml")
                    || f.name.endsWith("dimen.xml")
                    || f.name.endsWith("theme.xml"))
        ) {
            return true
        }
        return false
    }

    fun scan() {
        ThreadUtils.execute {
            Log.d("start----$unusedDirFile")
            val alls = allResources()
            val allsList = ArrayList<File>()
            allsList.addAll(alls)
            allsList.removeIf { it.absolutePath.isEmpty() }
            Log.d("所有的资源----")

            val codesSet = HashSet<File>()
            val imgsSet = HashSet<File>()
            val layoutsSet = HashSet<File>()

            var index = allsList.size - 1
            while (index >= 0) {
                val f = allsList[index]
                val path = f.absolutePath

                if (path.contains("\\src\\") && path.contains("\\java\\")) {
                    codesSet.add(allsList.removeAt(index))
                } else if (path.contains("res\\drawable") ||
                        path.contains("res\\mipmap")
                    || path.contains("\\assets\\")
                    || path.contains("res\\anim\\")
                    || path.contains("res\\raw\\")) {
                    imgsSet.add(allsList.removeAt(index))
                } else if (f.absolutePath.contains("res")
                    || f.name.equals("AndroidManifest.xml")) {
                    layoutsSet.add(allsList.removeAt(index))
                }
                index--
            }

            val codeList = codesSet.sortedBy { it.absolutePath }
            val imgList = imgsSet.sortedBy { it.absolutePath }
            val layoutList = layoutsSet.sortedBy { it.absolutePath }

            Log.d("--所有的代码------")
            for (item in codeList) {
                Log.d(item.absolutePath)
                initFileToMap(codesMap, item)
            }

            Log.d("--\n\n\n\n\n\n所有的images------")
            for (item in imgList) {
                Log.d(item.absolutePath)
            }

            Log.d("--\n\n\n\n\n\n所有的layouts------")
            for (item in layoutList) {
                Log.d(item.absolutePath)
                initFileToMap(layoutsMap, item)
            }

            Log.d("--\n\n\n\n\n\n剩余的------")
            for (item in allsList) {
                Log.d(item.absolutePath)
            }

            Log.d("--\n\n\n\n\n\n开工处理资源判断------")
            for (item in layoutList) {
                if (isLayoutBeUsed(item)) {
                } else {
                    Log.d("NoUsed: $item")
                }
            }

            for (item in imgList) {
                if (isResourceBeUsed(item)) {
                } else {
                    Log.d("NoUsed: $item")
                }
            }
        }
    }

    private fun allResources() : Set<File> {
        // /res/layout/
        val allList = HashSet<File>()
        allList.getAllFilesInDirWithFilter(unusedDirFile, fileFilter = {
            val r: Boolean
            if (it == null) {
                r = false
            } else {
                r = !it.name.contains("strings.xml")
                        && !it.name.contains("gradlew")
                        && !it.name.contains("lint-baseline.xml")
                        && !it.name.contains("maven-meta")

                        && !it.name.endsWith(".md5")
                        && !it.name.endsWith(".gradle")
                        && !it.name.endsWith(".sha1")
                        && !it.name.endsWith(".sha512")
                        && !it.name.endsWith(".sha256")
                        && !it.name.endsWith(".properties")
                        && !it.name.endsWith(".pro")
                        && !it.name.endsWith(".gitignore")
                        && !it.name.endsWith(".groovy")
                        && !it.name.endsWith(".pom")
                        && !it.name.endsWith(".md")
                        && !it.name.endsWith(".jar")
                        && !it.name.endsWith(".doc")
                        && !it.name.endsWith(".module")
                        && !it.name.endsWith(".jks")
                        && !it.name.endsWith(".options")
            }
            r
        }, dirFilter = {
            if (it == null) {
                false
            } else {
                !(it.absolutePath.contains("\\.idea")
                        || it.absolutePath.contains("\\build\\")
                        || it.absolutePath.contains("\\.gradle")
                        || it.absolutePath.contains("\\.git"))
            }
        })

        return allList
    }
}
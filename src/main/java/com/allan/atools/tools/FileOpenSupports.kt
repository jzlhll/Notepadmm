package com.allan.atools.tools

import com.allan.atools.controllerwindow.PictureWindow
import com.allan.atools.tools.modulenotepad.manager.AllEditorsManager
import com.allan.atools.utils.FileExtersions
import java.io.File

fun open(file:File):Boolean {
    if (file.isFile) {
        val ex = FileExtersions.getExtensionLowerCase(file.absolutePath)
        if (FileExtersions.isSupportTxt(ex)) {
            AllEditorsManager.Instance.openFile(file, true, true)
            return true
        } else if (FileExtersions.isSupportPicture(ex)) {
            PictureWindow.show(file)
            return true
        }
    }

    return false
}

fun open(str:String):Boolean {
    return open(File(str));
}
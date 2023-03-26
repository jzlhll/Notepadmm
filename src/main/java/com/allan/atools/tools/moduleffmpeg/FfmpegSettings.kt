package com.allan.atools.tools.moduleffmpeg

import com.allan.atools.UIContext
import com.allan.atools.controller.FfmpegController
import com.allan.atools.ui.IconfontCreator.createJFXButton
import com.allan.atools.ui.JfoenixDialogUtils
import com.allan.atools.utils.Locales
import com.allan.atools.utils.Utils
import com.allan.baseparty.Action2
import com.jfoenix.controls.JFXButton
import javafx.beans.value.ChangeListener
import javafx.beans.value.ObservableValue
import javafx.css.converter.StringConverter
import javafx.stage.DirectoryChooser
import java.io.File

class FfmpegSettings(private val ctrl:FfmpegController) {
    var ffmpegBinDir:String
    var workspaceDir:String

    var startSecond:Int = 0
    var totalSecond:Int = 1

    var crf:Int = 18
    var speed:String = "placebo"//0.ultrafast 1.superfast 2.veryfast 3.faster 4.fast 5.medium(default) 6.slow 7.slower 8.veryslow  9.placebo

    private val highSpeed = "placebo"
    private val midHighSpeed = "veryslow"
    private val midSpeed = "slower"
    private val lowMidSpeed = "medium"
    private val lowSpeed = "fast"

    private fun speedToStr(p0:Double) :String{
        return if (p0 < 1.0) {
            lowSpeed
        } else if (p0 >= 1.0 && p0 < 2.0) {
            lowMidSpeed
        } else if (p0 >= 2.0 && p0 < 3.0) {
            midSpeed
        } else if (p0 >= 3.0 && p0 < 4.0) {
            midHighSpeed
        } else {
            highSpeed
        }
    }

    /**
     * 第二个参数是true表示是video；false表示图片
     */
    val onFileSelectedListenerArray:ArrayList<Action2<String, Boolean>> = ArrayList()

    init {
        ffmpegBinDir = UIContext.sharedPref.getString("ffmpegBinDir", "")
        workspaceDir = UIContext.sharedPref.getString("ffmpegWorkspaceDir", "")

        ctrl.ffmpegDirLabel.text = ffmpegBinDir
        ctrl.selectADirLabel.text = workspaceDir

        ctrl.selectFfmpegDir.setOnMouseClicked {
            val directoryChooser = DirectoryChooser()
            val file = directoryChooser.showDialog(ctrl.stage) ?: return@setOnMouseClicked
            if (file.exists() && file.isDirectory) {
                ctrl.ffmpegDirLabel.text = file.absolutePath
                ffmpegBinDir = file.absolutePath
                UIContext.sharedPref.edit().putString("ffmpegBinDir", file.absolutePath).commit()
            } else {
                JfoenixDialogUtils.alert(Locales.str("error"), Locales.str("setting.dirIsWrong"))
            }
        }

        ctrl.selectADirBtn.setOnMouseClicked {
            val directoryChooser = DirectoryChooser()
            val file = directoryChooser.showDialog(ctrl.stage) ?: return@setOnMouseClicked
            if (file.exists() && file.isDirectory) {
                ctrl.selectADirLabel.text = file.absolutePath
                workspaceDir = file.absolutePath
                UIContext.sharedPref.edit().putString("ffmpegWorkspaceDir", file.absolutePath).commit()
                ctrl.stage.toFront()
            } else {
                JfoenixDialogUtils.alert(Locales.str("error"), Locales.str("setting.dirIsWrong"))
            }
        }

        ctrl.coverStartSecondMinusBtn.setOnMouseClicked {
            startSecond -= 1
            if (startSecond < 0) {
                startSecond = 0
            }
            ctrl.coverStartSecondLabel.text = "$startSecond"
        }

        ctrl.coverStartSecondPlusBtn.setOnMouseClicked {
            startSecond += 1
            ctrl.coverStartSecondLabel.text = "$startSecond"
        }

        ctrl.coverTotalSecondMinusBtn.setOnMouseClicked {
            totalSecond -= 1
            if (totalSecond < 1) {
                totalSecond = 1
            }
            ctrl.coverTotalSecondLabel.text = "$totalSecond"
        }

        ctrl.coverTotalSecondPlusBtn.setOnMouseClicked {
            totalSecond += 1
            ctrl.coverTotalSecondLabel.text = "$totalSecond"
        }

        ctrl.refreshFileListBtn.setOnMouseClicked {
            updateFileList()
        }

        ctrl.openToExploreBtn.setOnMouseClicked {
            Utils.openFolderExplore(File(workspaceDir))
        }

        ctrl.compressCrfSlide.min = 5.0
        ctrl.compressCrfSlide.max = 51.0
        ctrl.compressCrfSlide.value = crf.toDouble()
        ctrl.compressCrfSlide.valueProperty().addListener { p0, p1, p2 ->
            crf = p2.toInt()
            ctrl.compressCrfLabel.text = "$crf"
        }

        ctrl.compressSpeedSlide.min = 0.0
        ctrl.compressSpeedSlide.max = 5.0
        ctrl.compressSpeedLabel.text = "高"
        ctrl.compressSpeedSlide.valueProperty().addListener {p0, p1, p2 ->
            speed = speedToStr(p2.toDouble())
            ctrl.compressSpeedLabel.text = doubleCvtToWord(p2.toDouble())
        }

        ctrl.compressSpeedSlide.labelFormatter = object : javafx.util.StringConverter<Double?>() {
            override fun toString(p0: Double?): String {
                p0?:return ""
                return doubleCvtToWord(p0)
            }

            override fun fromString(p0: String?): Double? {
                return when (p0) {
                    "低" -> {
                        0.0
                    }
                    "中低" -> {
                        1.0
                    }
                    "中" -> {
                        2.0
                    }
                    "中高" -> {
                        4.0
                    }
                    else -> {
                        5.0
                    }
                }
            }

        }
        updateFileList()
    }

    private fun doubleCvtToWord(p0: Double):String {
        return if (p0 < 1.0) {
            "低"
        } else if (p0 >= 1.0 && p0 < 2.0) {
            "中低"
        } else if (p0 >= 2.0 && p0 < 3.0) {
            "中"
        } else if (p0 >= 3.0 && p0 < 4.0) {
            "中高"
        } else {
            "高"
        }
    }

    fun updateFileList() {
        val dir = File(workspaceDir)
        if (!dir.isDirectory) {
            return
        }
        val fileList = dir.listFiles()
        if (fileList != null && fileList.isNotEmpty()) {
            val flowPane = ctrl.listFileFlowPane
            flowPane.children.clear()

            fileList.forEach {
                val fileNm = it.name.toLowerCase()
                if (fileNm.endsWith("jpg") || fileNm.endsWith("jpeg")) {
                    flowPane.children.add(createJFXButton("jpg", 17, it.name).also { btn->
                        listBtnEx(btn)
                    })
                } else if (fileNm.endsWith("mp4")) {
                    flowPane.children.add(createJFXButton("video", 21, it.name).also { btn->
                        listBtnEx(btn)
                    })
                }
            }
        }
    }

    private fun listBtnEx(btn: JFXButton) {
        btn.isMnemonicParsing = false
        btn.setOnMouseClicked { event->
            onFileSelectedListenerArray.forEach {
                it.invoke(btn.text, btn.text.toLowerCase().endsWith("mp4"))
            }
        }
    }

}
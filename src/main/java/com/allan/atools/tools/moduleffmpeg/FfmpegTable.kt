package com.allan.atools.tools.moduleffmpeg

import com.allan.atools.UIContext
import com.allan.atools.controller.FfmpegController
import com.allan.atools.threads.ThreadUtils
import com.allan.atools.ui.SnackbarUtils
import com.allan.atools.utils.IO
import com.allan.baseparty.Action2
import javafx.application.Platform
import java.io.File

class FfmpegTable(private val ctrl:FfmpegController): Action2<String, Boolean> {
    private var currentTabIndex = 0
    private var mGotoGenerateCoverVideo:String? = null

    init {
        ctrl.tabPane.selectionModel.selectedIndexProperty().addListener { p0, p1, p2 ->
            currentTabIndex = p2.toInt()
        }

        ctrl.compressStartBtn.setOnMouseClicked {
            ctrl.compressStartBtn.isDisable = true

            ThreadUtils.execute{
                val ff = Ffmpeg(ctrl.mSetting)

                Platform.runLater {
                    ctrl.coverSureVideoBtn.isDisable = false
                }
            }
        }

        ctrl.coverSureVideoBtn.setOnMouseClicked {
            ctrl.coverSureVideoBtn.isDisable = true

            ThreadUtils.execute{
                val ff = Ffmpeg(ctrl.mSetting)
                ff.deleteCoverFiles()

                Platform.runLater {
                    ctrl.coverToastLabel.text = ("covers清理完毕，开始生成...")
                    ctrl.mSetting.updateFileList()
                }

                val file = File(IO.combinePath(ctrl.mSetting.workspaceDir, mGotoGenerateCoverVideo))
                if (file.exists()) {
                    ff.generateCovers(file, ctrl.mSetting.startSecond, ctrl.mSetting.totalSecond, 5)
                    Platform.runLater {
                        ctrl.coverToastLabel.text = ("covers生成完毕！")
                        ctrl.mSetting.updateFileList()
                    }
                } else if (mGotoGenerateCoverVideo == null) {
                    Platform.runLater { ctrl.coverToastLabel.text = ("warn: 请选择一个视频，建议是原视频") }
                }

                Platform.runLater {
                    ctrl.coverSureVideoBtn.isDisable = false
                }
            }
        }
    }

    override fun invoke(name: String?, isVideo:Boolean) {
        if (currentTabIndex == 0) {
        } else if (currentTabIndex == 1) {
            ctrl.coverSureVideoLabel.text = name
            mGotoGenerateCoverVideo = name
        } else if (currentTabIndex == 2) {
            if (isVideo) {
                ctrl.compressStartSureFileLabel.text = name
            } else {
                ctrl.combineCoverLabel.text = name
            }
        }
    }
}
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
    private var mGotoCompressVideo:String? = null
    private var mGotoCombineVideo:String? = null
    private var mGotoCombineImage:String? = null

    init {
        ctrl.tabPane.selectionModel.selectedIndexProperty().addListener { p0, p1, p2 ->
            currentTabIndex = p2.toInt()
        }

        ctrl.combineCover2Btn.setOnMouseClicked {
            ctrl.combineCover2Btn.isDisable = true

            ThreadUtils.execute{
                val ff = Ffmpeg(ctrl.mSetting)
                val imageFile = File(IO.combinePath(ctrl.mSetting.workspaceDir, mGotoCombineImage))
                val videoFile = File(IO.combinePath(ctrl.mSetting.workspaceDir, mGotoCombineVideo))

                if (!imageFile.exists()) {
                    Platform.runLater {
                        ctrl.combineCoverHint.text = "请在右侧选择封面。"
                    }
                } else if (!videoFile.exists()) {
                    Platform.runLater {
                        ctrl.combineCoverHint.text = "请在右侧选择没有封面的视频。"
                    }
                } else {
                    ff.combine(videoFile, imageFile)
                    Platform.runLater {
                        ctrl.combineCoverHint.text = "完成！"
                        ctrl.mSetting.updateFileList()
                    }
                }

                Platform.runLater {
                    ctrl.combineCover2Btn.isDisable = false
                }
            }
        }

        ctrl.compressStartBtn.setOnMouseClicked {
            ctrl.compressStartBtn.isDisable = true

            ThreadUtils.execute{
                val ff = Ffmpeg(ctrl.mSetting)
                val file = File(IO.combinePath(ctrl.mSetting.workspaceDir, mGotoCompressVideo))
                if (file.exists()) {
                    Platform.runLater {
                        ctrl.compressStartHint.text = "压缩开始...十分耗时，耐心等待...todo时间显示..."
                    }
                    ff.compressVideo(file, ctrl.mSetting.crf, ctrl.mSetting.speed)
                    Platform.runLater {
                        ctrl.compressStartHint.text = "压缩完成!"
                        ctrl.mSetting.updateFileList()
                    }
                } else {
                    Platform.runLater {
                        ctrl.compressStartHint.text = "请选择要压缩的视频。"
                        ctrl.mSetting.updateFileList()
                    }
                }

                Platform.runLater {
                    ctrl.compressStartBtn.isDisable = false
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
        if (currentTabIndex == 1) {
            ctrl.coverSureVideoLabel.text = name
            mGotoGenerateCoverVideo = name
        } else if (currentTabIndex == 2) {
            if (isVideo) {
                ctrl.compressStartSureFileLabel.text = name
                mGotoCompressVideo = name
            }
        } else if (currentTabIndex == 3) {
            if (isVideo) {
                mGotoCombineVideo = name
                ctrl.combineCoverLabel.text = (mGotoCombineImage?:"") + " " + (mGotoCombineVideo?:"")
            } else {
                mGotoCombineImage = name
                ctrl.combineCoverLabel.text = (mGotoCombineImage?:"") + " " + (mGotoCombineVideo?:"")
            }
        }
    }
}
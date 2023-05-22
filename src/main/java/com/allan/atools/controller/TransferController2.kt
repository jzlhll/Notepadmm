package com.allan.atools.controller

import com.allan.atools.tools.moduletransfer.ITransfer
import com.allan.atools.tools.moduletransfer.impl.Client
import com.allan.atools.tools.moduletransfer.impl.SingleServer
import javafx.application.Platform

class TransferController2(ctrl:TransferController) {
    private var transfer:ITransfer? = null

    init {
        ctrl.startAServerBtn.setOnMouseClicked {
            val isDragF = ctrl.isDragFileOrDirectory
            if (isDragF.isNullOrBlank()) {
                ctrl.logText.set("未选择文件夹。")
                return@setOnMouseClicked
            }

            if (isDragF == "file") {
                ctrl.logText.set("选择的是文件，不能作为接收端。")
                return@setOnMouseClicked
            }

            ctrl.startAServerBtn.isDisable = true
            transfer = SingleServer(
            logger = {
                Platform.runLater { ctrl.logText.set(it) }
            }, endCallback = {
                Platform.runLater {
                    transfer = null
                    ctrl.startAServerBtn.isDisable = false
                }
            })

            (transfer as SingleServer).publishReceiver(ctrl.dragInFile)
        }

        ctrl.startSendFileBtn.setOnMouseClicked {
            val isDragF = ctrl.isDragFileOrDirectory
            if (isDragF.isNullOrBlank()) {
                ctrl.logText.set("未选择文件。")
                return@setOnMouseClicked
            }

            if (isDragF == "dir") {
                ctrl.logText.set("选择的是文件夹，不能作为发送端。")
                return@setOnMouseClicked
            }

            if (ctrl.sendFileIpEdit.text.isNullOrBlank()) {
                ctrl.logText.set("ip没有填写")
                return@setOnMouseClicked
            }

            if (ctrl.sendFilePortEdit.text.isNullOrBlank()) {
                ctrl.logText.set("port没有填写")
                return@setOnMouseClicked
            }

            ctrl.startSendFileBtn.isDisable = true
            transfer = Client(
            logger = {
                Platform.runLater { ctrl.logText.set(it) }
            },
            endCallback = {
                Platform.runLater {
                    transfer = null
                    ctrl.startSendFileBtn.isDisable = false
                }
            }
            )
            (transfer as Client).startTransfer(ctrl.dragInFile, ctrl.sendFileIpEdit.text, ctrl.sendFilePortEdit.text.toInt())
        }
    }
}
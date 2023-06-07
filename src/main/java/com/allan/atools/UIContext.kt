package com.allan.atools

import com.allan.atools.bases.AbstractMainController
import com.allan.atools.controller.AToolsController
import com.allan.atools.controller.NotepadController
import com.allan.atools.richtext.codearea.EditorArea
import com.allan.atools.threads.ThreadUtils
import com.allan.atools.tools.AToolsControllerInitial
import com.allan.atools.utils.CacheLocation
import com.allan.atools.utils.Log
import com.allan.atools.utils.ResLocation
import com.allan.baseparty.content.SharedPref
import com.allan.baseparty.content.SharedPrefImp
import javafx.beans.property.*
import javafx.collections.FXCollections
import javafx.scene.control.Tab
import javafx.stage.Window
import java.io.File

object UIContext {
    @JvmField
    val DEBUG:Boolean = "true" == System.getProperty("A_DEBUG")

    @JvmField
    val WARN:Boolean = "true" == System.getProperty("A_WARN")

    @JvmField
    val CAN_DECORATOR:Boolean = !ResLocation.isOsx

    @JvmField
    val sharedPref: SharedPref = SharedPrefImp(File(CacheLocation.getUserConfigFile()))

    @JvmField
    var mainController: AbstractMainController? = null

    @JvmStatic
    fun context(): NotepadController {
        if (mainController != null) {
            if (mainController is NotepadController) {
                return mainController as NotepadController
            }
        }

        throw java.lang.RuntimeException("not init main controller!")
    }

    @JvmField
    var uiMainThread: Thread? = null

    @JvmField
    var mainWindow: Window? = null

    @JvmField
    var toolsController: AToolsController? = null

    fun showToolsController() {
        val ctrl = toolsController
        if (ctrl == null || ctrl.stage == null) {
            toolsController = AToolsControllerInitial().createAToolsWindow()
            toolsController?.stage?.show()
        } else {
            ctrl.stage.toFront()
        }
    }

    /**
     * 监听tab变化。其实是无所谓他的返回值了。
     */
    @JvmField
    val currentAreaProp = SimpleObjectProperty<EditorArea>()

    @JvmField
    val currentTabProp = SimpleObjectProperty<Tab>()

    @JvmField
    val focus:FocusHelper = FocusHelper()
    @JvmField
    val allOpenedFileList = FXCollections.observableArrayList<File>()
    @JvmField
    val fileEncodeIndicateProp = SimpleStringProperty("")

    @JvmField
    val bottomIndicateProp = SimpleStringProperty("")
    @JvmField
    val bottomSearchedIndicateProp = SimpleStringProperty("")

    @JvmField
    val isMultiSelectedProp = SimpleBooleanProperty(false)

    private var fontSizeProperty: SimpleIntegerProperty? = null
    private var fontThemeProperty: SimpleIntegerProperty? = null
    @JvmStatic
    fun getFontSizeProperty(): SimpleIntegerProperty {
        if (fontSizeProperty == null) {
            fontSizeProperty = SettingPreferences.getIntProp(SettingPreferences.editorFontSizeKey)
        }
        return fontSizeProperty!!
    }
    @JvmStatic
    fun updateFontSize(fontSize: Int) {
        fontSizeProperty!!.set(fontSize)
        sharedPref.edit().putInt(SettingPreferences.editorFontSizeKey, fontSize).commit()
    }
    @JvmStatic
    fun getFontThemeProperty(): SimpleIntegerProperty {
        if (fontThemeProperty == null) {
            fontThemeProperty = SettingPreferences.getIntProp(SettingPreferences.fontThemeIdKey)
        }
        return fontThemeProperty!!
    }
    @JvmStatic
    fun updateFontThemeId(id: Int) {
        fontThemeProperty!!.set(id)
    }
}

class FocusHelper {
    @Volatile
    private var mMainStageFocus = false
    @Volatile
    private var mResultNewWindowFocus = false
    @Volatile
    private var mAllLostFocus = false

    private val TAG_FOCUS = "<tag_focus>"
    private val DEBUG = false

    fun isNeedRequestMainStage(): Boolean {
        if (DEBUG) Log.d(TAG_FOCUS, "mAllLostFocus $mAllLostFocus")
        return mAllLostFocus
    }

    fun notifyResultNewWindowFocusChanged(focus: Boolean) {
        if (DEBUG) Log.d(TAG_FOCUS, "newWindow focus $focus")
        mResultNewWindowFocus = focus
        if (!mAllLostFocus) {
            if (focus) {
                mNotifyFocus.run()
            } else {
                notifyFocus()
            }
        } else {
            if (DEBUG) Log.d(TAG_FOCUS, "newWindow focus change and mAllLostFocus true ignore to notify")
        }
    }

    fun notifyMainStageFocusChanged(focus: Boolean) {
        if (DEBUG) Log.d(TAG_FOCUS, "main stage focus $focus")
        mMainStageFocus = focus
        if (focus) {
            mNotifyFocus.run()
        } else {
            notifyFocus()
        }
    }

    private val mNotifyFocus = Runnable {
        mAllLostFocus = if (!mMainStageFocus && !mResultNewWindowFocus) {
            if (DEBUG) Log.d(TAG_FOCUS, "ALL LOST FOCUS SET true: all lost focus!")
            true
        } else {
            if (DEBUG) Log.d(TAG_FOCUS, "ALL LOST FOCUS SET false: has focus!")
            false
        }
    }

    private fun notifyFocus() {
        ThreadUtils.globalHandler().removeCallback(mNotifyFocus)
        ThreadUtils.globalHandler().postDelayed(mNotifyFocus, 500)
    }
}
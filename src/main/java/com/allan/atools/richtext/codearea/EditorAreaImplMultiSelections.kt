package com.allan.atools.richtext.codearea

import com.allan.atools.Colors
import com.allan.atools.UIContext
import com.allan.atools.bean.MultiSelection
import com.allan.atools.utils.Log
import com.allan.baseparty.Action5
import javafx.beans.value.ChangeListener
import javafx.event.EventHandler
import javafx.scene.input.KeyCode
import javafx.scene.input.KeyEvent
import org.fxmisc.richtext.SelectionImpl
import org.fxmisc.richtext.SelectionPath
import kotlin.math.max

class EditorAreaImplMultiSelections(private val area: EditorAreaImpl) {
    companion object {
        private val TAG = EditorAreaImplMultiSelections::class.java.simpleName
    }

    private var cachedSelections: HashMap<Int, SelectionImpl<Collection<String>, String, Collection<String>>>? =
        null

    /**
     * 外部可以访问用于操控
     */
    private var curMultiSelection: MultiSelection? = null
    private var mNextMultiSectionIndex: Long = 0
    var isMultiSelected = false

    private fun getNextMultiSelectName(): String {
        return "SelectionImpl" + mNextMultiSectionIndex++
    }

    private var eventFilter : EventHandler<KeyEvent>? = null
    private fun addEventFilter() {
        if (eventFilter == null) {
            eventFilter = EventHandler { event: KeyEvent ->
                val keyArea = event.source as EditorAreaImpl
                if (keyArea.multiSelections.isMultiSelected) {
                    when (event.code) {
                        KeyCode.DELETE, KeyCode.BACK_SPACE -> {
                            event.consume()
                            delete()
                        }
                        else -> {}
                    }
                }
            }
        }
        area.addEventFilter(KeyEvent.KEY_PRESSED, eventFilter)
    }

    private fun removeEventFilter() {
        area.removeEventFilter(KeyEvent.KEY_PRESSED, eventFilter)
    }

    fun multiSelect(beforeLine: Int, afterLine: Int, beforeColumn: Int, afterColumn: Int, start:Int, end:Int) {
        val cur = MultiSelection(
            beforeLine.coerceAtMost(afterLine), beforeLine.coerceAtLeast(afterLine),
            beforeColumn.coerceAtMost(afterColumn), beforeColumn.coerceAtLeast(afterColumn))
        curMultiSelection = cur
        UIContext.isMultiSelectedProp.set(true)
        isMultiSelected = true

        addEventFilter()

        selectInner(cur, false)

        listeners()
    }

    private fun multiSelectionChangedAction(type:String?) {
        Log.d("area: type $type isInMultiSelection: $isMultiSelected")
        if (isMultiSelected) {
            if ("scrolled" == type) {
                curMultiSelection?.let { selectInner(it, true) }
            } else {
                removeAllMultiSelect()
            }
        }
    }

    private fun listeners() {
        Log.d("area: listener----")
       val editor = area.getEditor()
        if (!mIsListenersInit) {
            mIsListenersInit = true
            editor.textChanged.addAction {
                multiSelectionChangedAction(null)
            }
            editor.caretPosChanged.addAction(Action5 {
                    _, _,_,_,_ ->
                multiSelectionChangedAction(null)
            })
            editor.selectionChanged.addAction {
                multiSelectionChangedAction(null)
            }
            editor.visibleParagraphChanged.addAction {
                multiSelectionChangedAction("scrolled")
            }
        }

        if (sizeXyListener == null) {
            sizeXyListener = ChangeListener<Number?> { _, _, _ -> multiSelectionChangedAction("scrolled")}
        }
        if (!isSizeXyListenerSet) {
            isSizeXyListenerSet = true
            UIContext.sizeXyChangedProp.addListener(sizeXyListener)
        }
    }

    private var sizeXyListener:ChangeListener<Number?>? = null
    private var mIsListenersInit = false
    private var isSizeXyListenerSet = false

    private fun removeListeners() {
        Log.d("area: removeListeners----")
        if (sizeXyListener == null) return
        if (isSizeXyListenerSet) {
            UIContext.sizeXyChangedProp.removeListener(sizeXyListener)
            isSizeXyListenerSet = false
        }
    }

    fun destroy() {
        //todo 目前只尝试清理这1个。避免过多持有Editor和area
        removeListeners()
    }

    private fun removeAllMultiSelect() {
        Log.d("area: removeAll MultiSelect")
        UIContext.isMultiSelectedProp.set(false)
        isMultiSelected = false

        mNextMultiSectionIndex = 0
        cachedSelections?.let {
            for (k in it.keys) {
                val selectionImpl = it[k]
                if (selectionImpl != null) {
                    selectionImpl.selectRange(0, 0)
                    area.removeSelection(selectionImpl)
                }
            }
            it.clear()
        }

        removeEventFilter()
        removeListeners()
    }

    private fun selectInner(multiSelection: MultiSelection, removeCached:Boolean) {
        Log.d("area: selectInner: $multiSelection")
        //copy
        val beforeLine = multiSelection.startParagraph
        val afterLine = multiSelection.endParagraph
        val beforeCol = multiSelection.startColumn
        val afterCol = multiSelection.endColumn
        val firstVisibleIndex = 0.coerceAtLeast(area.firstVisibleParToAllParIndex() - 3)
        val lastVisibleIndex: Int = (area.paragraphs.size - 1).coerceAtMost(area.lastVisibleParToAllParIndex() + 3)

        if (cachedSelections == null) {
            cachedSelections = HashMap(64)
        }

        cachedSelections?.let {
            //追加特殊代码：移除不可见区域
            if (removeCached) {
                val delList = ArrayList<Int>()
                for (k in it.keys) {
                    if (k > lastVisibleIndex || k < firstVisibleIndex) {
                        delList.add(k)
                    }
                }

                for (k in delList) {
                    val selectionImp = it.remove(k)
                    if (selectionImp != null) {
                        selectionImp.selectRange(0, 0)
                        area.removeSelection(selectionImp)
                    }
                }
            }

            for (i in firstVisibleIndex.coerceAtLeast(beforeLine)..lastVisibleIndex.coerceAtMost(afterLine)) {
                if (removeCached && it.containsKey(i)) { //追加特殊代码：避免重复添加
                    continue
                }
                val paraLen = area.getParagraphLength(i)
                // select something so it is visible
                if (paraLen >= beforeCol) {
                    val extraSelection: SelectionImpl<Collection<String>, String, Collection<String>> =
                        SelectionImpl<Collection<String>, String, Collection<String>>(getNextMultiSelectName(), area,
                            { path: SelectionPath ->
                                // make rendered selection path look like a yellow highlighter
                                path.strokeWidth = 0.0
                                path.fill = Colors.ColorsMultiSelection.invoke()
                            }
                        )
                    check(area.addSelection(extraSelection)) { "selection was not added to area" }
                    // select something so it is visible
                    extraSelection.selectRange(i, beforeCol, i, Math.min(paraLen, afterCol))
                    it[i] = extraSelection
                }
            }
        }

        // copy
    }

    fun delete() {
        Log.d(TAG + "area: delete()")
        removeAllMultiSelect()
        deleteOrReplaceUsingText("")
    }

    fun deleteMulti() {
        //方式1：直接搞，通过多更新
        curMultiSelection?.let {
            val c = it
            val mc = area.createMultiChange()
            for (i in c.startParagraph..c.endParagraph) {
                val maxLen = area.getParagraphLength(i)
                if (maxLen >= c.endColumn) {
                    mc.deleteText(i, c.startColumn, i, c.endColumn)
                } else if (maxLen >= c.startColumn) {
                    mc.deleteText(i, c.startColumn, i, maxLen)
                }
            }
            mc.commit()
        }
    }

    private fun deleteOrReplaceUsingText(str:String) {
        //方式2：通过我们的List来处理
        curMultiSelection?.let {
            val startOffset = area.getAbsolutePosition(it.startParagraph, it.startColumn)
            val fullText = java.lang.StringBuilder(area.getText(startOffset, area.getAbsolutePosition(it.endParagraph, it.endColumn)))

            val c = it
            //倒序才能在修改的时候，offset不需要动
            for (i in c.endParagraph downTo c.startParagraph) {
                val maxLen = area.getParagraphLength(i)
                val start = area.getAbsolutePosition(i, it.startColumn) - startOffset
                if (maxLen >= c.endColumn) {
                    val end = area.getAbsolutePosition(i, it.endColumn) - startOffset
                    fullText.replace(start, end, str)
                    area.getText(i, c.startColumn, i, c.endColumn)
                } else if (maxLen >= c.startColumn) {
                    val end = area.getAbsolutePosition(i, maxLen) - startOffset
                    fullText.replace(start, end, str)
                }
            }

            area.replaceText(it.startParagraph, it.startColumn, it.endParagraph, it.endColumn, fullText.toString())
        }
    }

    fun replace(str:String) {
        Log.d(TAG + "area: replace(str)")
        removeAllMultiSelect()
        //replaceMulti(str)
        deleteOrReplaceUsingText(str)
    }

    private fun replaceMulti(str:String) {
        val curTs = System.currentTimeMillis()
        curMultiSelection?.let {
            val c = it
            //方式2：直接搞，通过多更新
            val mc = area.createMultiChange()
            for (i in c.startParagraph..c.endParagraph) {
                val maxLen = area.getParagraphLength(i)
                if (maxLen >= c.endColumn) {
                    mc.replaceText(area.getAbsolutePosition(i, c.startColumn), area.getAbsolutePosition(i, c.endColumn), str)
                } else if (maxLen >= c.startColumn) {
                    mc.replaceText(area.getAbsolutePosition(i, c.startColumn), area.getAbsolutePosition(i, maxLen), str)
                }
            }
            mc.commit()
        }
        Log.d("replace time: " + (System.currentTimeMillis() - curTs))
    }
}
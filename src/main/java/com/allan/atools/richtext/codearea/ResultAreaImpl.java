package com.allan.atools.richtext.codearea;

import com.allan.atools.UIContext;
import com.allan.atools.richtext.FoldableTextArea;
import com.allan.atools.richtext.ParStyle;
import com.allan.atools.richtext.TextStyle;
import com.allan.atools.text.IAreaEx;
import com.allan.atools.FontTheme;
import com.allan.atools.tools.modulenotepad.Highlight;
import com.allan.atools.utils.Locales;
import com.allan.atools.utils.Log;
import com.allan.baseparty.Action;
import com.allan.baseparty.Action0;
import com.allan.baseparty.Action2;
import javafx.beans.value.ChangeListener;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import org.fxmisc.richtext.GenericStyledArea;

import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.util.Optional;

public final class ResultAreaImpl extends FoldableTextArea implements IAreaEx<ParStyle, String, TextStyle> {
    private static final String TAG = "ResultAreaImpl";
    public static final String NAME_SP = "resultArea";

    private Action<ResultAreaImpl> clearSelf, clearOthers;
    private Action0 clearAll;

    public void setClearSelf(Action<ResultAreaImpl> clearSelf) {this.clearSelf = clearSelf;}
    public void setClearOthers(Action<ResultAreaImpl> clearOthers) {this.clearOthers = clearOthers;}
    public void setClearAll(Action0 clearAll) {this.clearAll = clearAll;}

    private Object ex1;

    public void setObject1(Object o1) {
        ex1 = o1;
    }

    public Object getObject1() {
        return ex1;
    }

    private ChangeListener<Number> fontChanged;
    private ChangeListener<Number> fontThemeChanged;

    public ResultAreaImpl() {
        super(NAME_SP);
        Highlight.initGenericAreaFont(this);
        fontThemeChanged = (observable, oldValue, newValue) -> {
            var fm = FontTheme.fontFamily();
            var oldFm = FontTheme.fontFamily(oldValue.intValue());
            Log.d(TAG, "new font family " + fm + ", old font family " + oldFm);
            Highlight.updateGenericAreaFont(ResultAreaImpl.this, fm, oldFm);
        };
        UIContext.getFontThemeProperty().addListener(fontThemeChanged);

        fontChanged = (observable, oldValue, newValue) -> {
            getContent().setStyle(0, getText().length(), TextStyle.fontSize(newValue.intValue() - 4));
            Log.d("font changed " + ResultAreaImpl.this);
        };
        UIContext.getFontSizeProperty().addListener(fontChanged);

        initArea();
    }

    private boolean mIsDestroyed = false;

    @Override
    public void destroy() {
        Log.d(TAG,"DESTROY:...");
        dispose();

        UIContext.getFontSizeProperty().removeListener(fontChanged);
        fontChanged = null;
        UIContext.getFontThemeProperty().removeListener(fontThemeChanged);
        fontThemeChanged = null;
        mIsDestroyed = true;
    }

    @Override
    public boolean isDestroyed() {
        return mIsDestroyed;
    }

    private void initArea() {
        setEditable(false);

        ContextMenu contextMenu = createMenu();
        setContextMenu(contextMenu);

        caretPositionProperty().addListener((observable, oldValue, newValue) -> {
            //实现数组的位移操作，点击一次，左移一位，末尾补上当前开机时间（cpu时间）
            var startPar = Highlight.getCurrentStartLineNum(this);
            if (startPar == mLastClickedLineNum && startPar != Integer.MIN_VALUE) {
                long cur = System.currentTimeMillis();
                var col = getCaretColumn();
                Log.d("col " + col);
                if (cur >= mLastDoubleTriggerTime + 300L) {
                    mLastDoubleTriggerTime = cur;
                    outLineTrigger.invoke(startPar, col);
                }
            }

            mLastClickedLineNum = startPar;
            //if(mDoubleClick != null) mDoubleClick.onClick();
        });
    }

    @Override
    public ContextMenu createMenu() {
        //Create Menu Items
        MenuItem clearMenu = new MenuItem(Locales.str("result.clear"));
        MenuItem clearOthersMenu = new MenuItem(Locales.str("result.clearOthers"));
        MenuItem clearAllMenu = new MenuItem(Locales.str("result.clearAll"));
        MenuItem emptyMenu = new MenuItem("");

        MenuItem copyLine = new MenuItem(Locales.str("result.copyLine"));
        MenuItem allCopy = new MenuItem(Locales.str("copyAll"));
        //Add Event Handler
        clearMenu.setOnAction(event -> Optional.ofNullable(clearSelf).ifPresent(cs -> cs.invoke(ResultAreaImpl.this)));
        clearOthersMenu.setOnAction(event -> Optional.ofNullable(clearOthers).ifPresent(co -> co.invoke(ResultAreaImpl.this)));
        clearAllMenu.setOnAction(event -> Optional.ofNullable(clearAll).ifPresent(Action0::invoke));
        copyLine.setOnAction(e -> {
            var clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
            // 封装文本内容
            var s = getText(Highlight.getCurrentCaretLineNum(this));
            var trans = new StringSelection(s);
            // 把文本内容设置到系统剪贴板
            clipboard.setContents(trans, null);
        });
        allCopy.setOnAction(e -> {
            var clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
            // 封装文本内容
            var trans = new StringSelection(getText());
            // 把文本内容设置到系统剪贴板
            clipboard.setContents(trans, null);
        });

        //Add Menu items in Context Menu
        ContextMenu contextMenu = new ContextMenu();
        contextMenu.getItems().add(clearMenu);
        contextMenu.getItems().add(clearOthersMenu);
        contextMenu.getItems().add(clearAllMenu);
        contextMenu.getItems().add(emptyMenu);
        contextMenu.getItems().add(allCopy);
        contextMenu.getItems().add(copyLine);

        return contextMenu;
    }

    @Override
    public void setFileEncoding(String encoding) {
        throw new RuntimeException("不用支持2");
    }

    @Override
    public String getFileEncoding() {
        throw new RuntimeException("不用支持1");
    }

    @Override
    public GenericStyledArea<ParStyle, String, TextStyle> getArea() {
        return this;
    }

    /**
     * 第一个参数返回lineNum，第二个参数colIndex
     */
    private Action2<Integer, Integer> outLineTrigger;
    private int mLastClickedLineNum;
    private long mLastDoubleTriggerTime = 0L;

    public void setDoubleClickListener(Action2<Integer, Integer> listener) {
        outLineTrigger = listener;
    }
}

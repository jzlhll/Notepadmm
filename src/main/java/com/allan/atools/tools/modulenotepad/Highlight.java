package com.allan.atools.tools.modulenotepad;

import com.allan.atools.utils.Locales;
import com.allan.atools.utils.Log;
import com.allan.atools.FontTheme;
import com.allan.atools.ui.SnackbarUtils;
import com.allan.uilibs.richtexts.CodeArea;
import javafx.scene.control.IndexRange;
import org.fxmisc.richtext.Caret;
import org.fxmisc.richtext.GenericStyledArea;

import static org.fxmisc.richtext.model.TwoDimensional.Bias.Forward;

public final class Highlight {
    public static class CurrentLineNumsInfo {
        public int startPar;
        public int endPar;

        public boolean isOnlyOne() {return startPar == endPar;}
    }

    public static int getCurrentCaretLineNum(GenericStyledArea<?,?,?> area) {
        int position = area.getCaretPosition();
        return area.offsetToPosition(position, Forward).getMajor();
    }

    public static int[] getCurrentCaretPosAndLineNum(GenericStyledArea<?,?,?>  area) {
        int position = area.getCaretPosition();
        return new int[]{position, area.offsetToPosition(position, Forward).getMajor()};
    }

    public static int getCurrentStartLineNum(GenericStyledArea<?,?,?> area) {
        IndexRange selection = area.getSelection();
        return area.offsetToPosition(selection.getStart(), Forward).getMajor();
    }

    public enum JumpMode {
        JumpCenter,
        GoDown,
        GoUp,
    }

    public static void jumpToHead(GenericStyledArea<?,?,?> area) {
        //area.moveTo(0, 0);
        area.setShowCaret(Caret.CaretVisibility.AUTO);
        //area.selectRange(0, 0);
        area.showParagraphAtTop(0);
        Log.d("jumpToHead showParagraph AtTop " + 0);
    }

    public static void initGenericAreaFont(GenericStyledArea<?,?,?> area) {
        var className = FontTheme.fontFamily();
        area.getStyleClass().add(className);
    }

    public static void updateGenericAreaFont(GenericStyledArea<?,?,?> area, String newClassName, String lastClassName) {
        area.getStyleClass().remove(lastClassName);
        area.getStyleClass().add(newClassName);
    }

    private static final int OFFSET_OF_VISIBLE = 2;

    public static void jumpToLineAndSelectWordMore(CodeArea area, JumpMode mode, String lineStr, int paraIndex, int lineStart, int lineEnd) {
        int maxLines = area.getParagraphs().size();
        if (paraIndex < 0 || maxLines <= paraIndex) {
            SnackbarUtils.show(Locales.str("areaTextChanged"));
            return;
        }

        var para = area.getParagraph(paraIndex);
        if (lineStr != null && !para.getText().equals(lineStr)) {
            SnackbarUtils.show(Locales.str("areaTextChanged"));
            return;
        }
        jumpToLineAndSelectWord(area, mode, paraIndex, lineStart, lineEnd);
    }

    public static void jumpToLineAndSelectWord(CodeArea area, JumpMode mode, int paraIndex, int lineStart, int lineEnd) {
        String tag = "jumpToLine SelectWord: ";

        Log.d(">>>>>>>>>>>>>>>" + tag + paraIndex + ", " + lineStart);
        int maxLines = area.getParagraphs().size();
        if (paraIndex < 0 || maxLines <= paraIndex) {
            Log.d(tag + "maxLines is changed...");
            return;
        }

        int lineLen = area.getParagraph(paraIndex).length();
        if (lineStart < 0 || lineEnd < lineStart || lineEnd > lineLen) {
            Log.d(tag + "match range is changed");
            return;
        }

        int firstVisibleIndex = area.firstVisibleParToAllParIndex();
        int lastVisibleIndex = area.lastVisibleParToAllParIndex();
        boolean isWithin = firstVisibleIndex <= paraIndex && lastVisibleIndex >= paraIndex;

        // 一次设置最终选区，避免延迟回调覆盖用户随后点击或输入的位置。
        area.setShowCaret(Caret.CaretVisibility.AUTO);
        area.selectRange(area.getAbsolutePosition(paraIndex, lineStart),
                area.getAbsolutePosition(paraIndex, lineEnd));
        if (!isWithin) {
            switch (mode) {
                case JumpCenter -> {
                    var halfVisibleSize = area.getVisibleParagraphs().size();
                    if (halfVisibleSize > 4) {
                        halfVisibleSize = halfVisibleSize / 2;
                    }
                    area.showParagraphAtTop(paraIndex > halfVisibleSize ? paraIndex - halfVisibleSize : paraIndex);
                }
                case GoDown -> {
                    int showLine = paraIndex + OFFSET_OF_VISIBLE;
                    area.showParagraphAtBottom(showLine < maxLines ? showLine : maxLines - 1);
                }
                case GoUp -> area.showParagraphAtTop(paraIndex >= OFFSET_OF_VISIBLE ? paraIndex - OFFSET_OF_VISIBLE : 0);
            }
        }
        area.requestFollowCaret();
    }
}

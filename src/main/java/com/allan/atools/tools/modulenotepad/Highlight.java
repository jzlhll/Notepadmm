package com.allan.atools.tools.modulenotepad;

import com.allan.atools.threads.ThreadUtils;
import com.allan.atools.utils.Locales;
import com.allan.atools.utils.Log;
import com.allan.baseparty.handler.TextUtils;
import com.allan.atools.FontTheme;
import com.allan.atools.ui.SnackbarUtils;
import com.allan.uilibs.richtexts.CodeArea;
import javafx.application.Platform;
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
        area.setShowCaret(Caret.CaretVisibility.ON);
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
        //String tag = "jumpToLine SelectWord More: ";

        //Log.d(">>>>>>>>>>>>>>>" + tag + paraIndex + ", " + lineStart);
        int maxLines = area.getParagraphs().size();
        if (maxLines <= paraIndex) {
            //Log.d(tag + "maxLines is changed...");
            SnackbarUtils.show(Locales.str("areaTextChanged"));
            return;
        }

        var para = area.getParagraph(paraIndex);
        if (lineStr != null && !para.getText().equals(lineStr)) {
            //Log.d(tag + " 文字已经变化 todo");
            SnackbarUtils.show(Locales.str("areaTextChanged"));
            return;
        }

        int selectWordLen = lineEnd - lineStart;
        int lineTotalCharLen = TextUtils.isEmpty(lineStr) ? 0 : lineStr.length();
        int moreMoveLen = Math.min(lineTotalCharLen - lineEnd, 3);

        Runnable run1 = ()->{
            area.setShowCaret(Caret.CaretVisibility.OFF);
            area.moveTo(paraIndex, 0);
            area.requestFollowCaret();//第一步，跳到该行的最前面

            int firstVisibleIndex = area.firstVisibleParToAllParIndex();
            int lastVisibleIndex = area.lastVisibleParToAllParIndex();

            boolean isWithin = firstVisibleIndex < paraIndex && lastVisibleIndex > paraIndex;
            if (isWithin) {
                //Log.d(tag + " with in");
            } else {
                switch (mode) {
                    case JumpCenter -> {
                        var halfVisibleSize = area.getVisibleParagraphs().size();
                        if (halfVisibleSize > 4) {
                            halfVisibleSize = halfVisibleSize / 2;
                        }

                        if (paraIndex > halfVisibleSize) {
                            var m = paraIndex - halfVisibleSize;
                            area.showParagraphAtTop(m);
                            //Log.d(tag + " JumpCenter 1showParagraph AtTop " + m);
                        } else {
                            area.showParagraphAtTop(paraIndex);
                            //Log.d(tag + " JumpCenter 2showParagraph AtTop " + paraIndex);
                        }
                    }
                    case GoDown -> {
                        area.showParagraphAtBottom(paraIndex + OFFSET_OF_VISIBLE);
                        //Log.d(tag + " GoDown showParagraph AtBottom " + paraIndex);
                    }
                    case GoUp -> {
                        area.showParagraphAtTop(paraIndex >= OFFSET_OF_VISIBLE ? paraIndex - OFFSET_OF_VISIBLE : 0);
                        //Log.d(tag + " GoUp showParagraph AtTop " + paraIndex);
                    }
                }
            }
        };

        Runnable run2 = ()->{
            area.moveTo(paraIndex, lineEnd + moreMoveLen);
            //Log.d(tag + " run2 moveTo and follow more: " + lineEnd + ", " + moreMoveLen);
            area.requestFollowCaret();//第2步，跳到需要额外显示的后面
        };

        Runnable run3 = () -> {
            area.setShowCaret(Caret.CaretVisibility.ON);
            area.moveTo(paraIndex, lineEnd); //第三步，将光标移动到需要的位置&选中words
            //Log.d(tag + " run3 " + lineEnd);
            var po = area.getCaretPosition();
            area.selectRange(po - selectWordLen, po);
        };

        run1.run();
        //area.suspendVisibleParsWhileInvoke(run1);

        ThreadUtils.executeDelay(StaticsProf.getJumpLinesDeltaTime(), () -> {
            Platform.runLater(()->{
                area.suspendVisibleParsWhileInvoke(run2);
            });

            ThreadUtils.executeDelay(StaticsProf.getJumpLinesDeltaTime(), () -> {
                Platform.runLater(()->{
                    area.suspendVisibleParsWhileInvoke(run3);
                });
            });
        });
    }

    public static void jumpToLineAndSelectWord(CodeArea area, JumpMode mode, int paraIndex, int lineStart, int lineEnd) {
        String tag = "jumpToLine SelectWord: ";

        Log.d(">>>>>>>>>>>>>>>" + tag + paraIndex + ", " + lineStart);
        int maxLines = area.getParagraphs().size();
        if (maxLines <= paraIndex) {
            Log.d(tag + "maxLines is changed...");
            return;
        }

        int selectWordLen = lineEnd - lineStart;
        int more;
        if (lineStart < 60) { //左侧
            more = lineStart - Math.min(lineStart, 3);
        } else { //右侧
            var lineLen = area.getParagraph(paraIndex).length();
            more = Math.min(lineLen, lineEnd + 3) - lineEnd;
        }

        Runnable run1 = ()-> {
            area.setShowCaret(Caret.CaretVisibility.OFF);
            area.moveTo(paraIndex, lineStart < 60 ? lineStart - more : lineEnd + more);
            Log.d(tag + " run2 moveTo and follow more: " + lineEnd);
            area.requestFollowCaret();//底部直跳整合1-2步，跳到该行的最前面

            int firstVisibleIndex = area.firstVisibleParToAllParIndex();
            int lastVisibleIndex = area.lastVisibleParToAllParIndex();

            boolean isWithin = firstVisibleIndex < paraIndex && lastVisibleIndex > paraIndex;
            if (isWithin) {
                Log.d(tag + " with in");
            } else {
                switch (mode) {
                    case JumpCenter -> {
                        var halfVisibleSize = area.getVisibleParagraphs().size();
                        if (halfVisibleSize > 4) {
                            halfVisibleSize = halfVisibleSize / 2;
                        }

                        if (paraIndex > halfVisibleSize) {
                            var m = paraIndex - halfVisibleSize;
                            area.showParagraphAtTop(m);
                            Log.d(tag + " JumpCenter 1showParagraph AtTop " + m);
                        } else {
                            area.showParagraphAtTop(paraIndex);
                            Log.d(tag + " JumpCenter 2showParagraph AtTop " + paraIndex);
                        }
                    }
                    case GoDown -> {
                        area.showParagraphAtBottom(paraIndex + OFFSET_OF_VISIBLE);
                        Log.d(tag + " GoDown showParagraph AtBottom " + paraIndex);
                    }
                    case GoUp -> {
                        area.showParagraphAtTop(paraIndex >= OFFSET_OF_VISIBLE ? paraIndex - OFFSET_OF_VISIBLE : 0);
                        Log.d(tag + " GoUp showParagraph AtTop " + paraIndex);
                    }
                }
            }
        };

        Runnable run3 = () -> {
            if (more > 0) {
                area.moveTo(paraIndex, lineStart < 60 ? lineStart : lineEnd);
            }
            area.setShowCaret(Caret.CaretVisibility.ON);
            Log.d(tag + " run3 " + lineEnd);
            var po = area.getCaretPosition();
            if (lineStart < 60) {
                area.selectRange(po, po + selectWordLen);
            } else {
                area.selectRange(po - selectWordLen, po);
            }
        };

        run1.run();
        //area.suspendVisibleParsWhileInvoke(run1);

        ThreadUtils.executeDelay(StaticsProf.getJumpLinesDeltaTime(), () -> {
            Platform.runLater(()->{
                area.suspendVisibleParsWhileInvoke(run3);
            });
        });
    }
}

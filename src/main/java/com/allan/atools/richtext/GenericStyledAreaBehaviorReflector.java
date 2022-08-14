package com.allan.atools.richtext;

import com.allan.atools.richtext.codearea.EditorArea;
import com.allan.atools.utils.Log;
import com.allan.baseparty.utils.ReflectionUtils;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import org.fxmisc.wellbehaved.event.template.InputMapTemplate;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import static org.fxmisc.wellbehaved.event.EventPattern.mousePressed;
import static org.fxmisc.wellbehaved.event.template.InputMapTemplate.consume;

public final class GenericStyledAreaBehaviorReflector {
    private static Method handleShiftPressMethod, handleFirstPrimaryPressMethod;

    public static void action() {
        try {
            var clazz = ReflectionUtils.getProtectedClass("org.fxmisc.richtext.GenericStyledAreaBehavior");
            var EVENT_TEMPLATE = ReflectionUtils.getStaticPrivateFieldValue(clazz, "EVENT_TEMPLATE");
            if (EVENT_TEMPLATE != null) {
                var EVENT_TEMPLATE_t = ReflectionUtils.getPrivateFieldValue(EVENT_TEMPLATE, "templates");
                if (EVENT_TEMPLATE_t instanceof InputMapTemplate[] EVENT_TEMPLATE_t_arr) {
                    var mouseTemplate = EVENT_TEMPLATE_t_arr[0];
                    var mouseTemplate_t = ReflectionUtils.getPrivateFieldValue(mouseTemplate, "templates");
                    if (mouseTemplate_t instanceof InputMapTemplate[] mouseTemplate_t_arr) {
                        var mousePressedTemplate = mouseTemplate_t_arr[0];
                        var mouseTemplateTemplates_t = ReflectionUtils.getPrivateFieldValue(mousePressedTemplate, "templates");
                        if (mouseTemplateTemplates_t instanceof InputMapTemplate[] mouseTemplateTemplates_t_arr) {
                            Log.d("is array " + mouseTemplateTemplates_t_arr);
                            mouseTemplateTemplates_t_arr[2] = consume(
                                    mousePressed(MouseButton.PRIMARY).onlyIf(GenericStyledAreaBehaviorReflector::isShiftAndAltDown),
                                    GenericStyledAreaBehaviorReflector::handleShiftOrAltPress
                            );
                        }
                    }
                } else {
                    Log.d("is not array ");
                }
            } else {
                Log.d("error ");
            }
        } catch (ClassNotFoundException | NoSuchFieldException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    private static boolean isShiftAndAltDown(MouseEvent mouseEvent) {
        return mouseEvent.isAltDown() | mouseEvent.isShiftDown();
    }

    private static void handleShiftOrAltPress(Object behavior, MouseEvent e) {
        if (e.isAltDown()) {
            EditorArea area = (EditorArea) e.getSource();

            var editorBase = area.getEditor();
            var beforeCol = editorBase.getCurrentCaretColNum();
            var beforeLine = editorBase.getCurrentCaretLineNum();
            var beforePos = editorBase.getCurrentCaretPos();

            Method m = null;
            if (handleFirstPrimaryPressMethod != null) {
                m = handleFirstPrimaryPressMethod;
            } else {
                try {
                    m = ReflectionUtils.getPrivateMethod(behavior, "handleFirstPrimaryPress", new Class[] {MouseEvent.class});
                } catch (NoSuchMethodException ex) {
                    ex.printStackTrace();
                }

                if (m == null) {
                    return;
                }

                handleFirstPrimaryPressMethod = m;
            }

            try {
                m.invoke(behavior, e);
            } catch (IllegalAccessException | InvocationTargetException ex) {
                ex.printStackTrace();
            }

            var afterCol = editorBase.getCurrentCaretColNum();
            var afterLine = editorBase.getCurrentCaretLineNum();
            var afterPos = editorBase.getCurrentCaretPos();

            Log.d("area: " + beforeCol + ", " + beforeLine + ",,,, " + afterCol + ", " + afterLine);
//            JfoenixDialogUtils.confirm("delete", "area: " + beforeCol + ", " + beforeLine + ",,,, " + afterCol + ", " + afterLine, 0, 0,
//                    new JfoenixDialogUtils.DialogActionInfo(JfoenixDialogUtils.ConfirmMode.Accept, null, ()-> {}),
//                    new JfoenixDialogUtils.DialogActionInfo(JfoenixDialogUtils.ConfirmMode.Cancel, null, null));
            area.getMultiSelections().multiSelect(beforeLine, afterLine, beforeCol, afterCol, beforePos, afterPos);
        } else if (e.isShiftDown()) {
            Method m = null;
            if (handleShiftPressMethod != null) {
                m = handleShiftPressMethod;
            } else {
                try {
                    m = ReflectionUtils.getPrivateMethod(behavior, "handleShiftPress", new Class[] {MouseEvent.class});
                } catch (NoSuchMethodException ex) {
                    ex.printStackTrace();
                }

                if (m == null) {
                    return;
                }

                handleShiftPressMethod = m;
            }
            try {
                m.invoke(behavior, e);
            } catch (IllegalAccessException | InvocationTargetException ex) {
                ex.printStackTrace();
            }
        }
    }
}

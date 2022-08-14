package com.allan.atools.richtext.codearea;

import com.allan.uilibs.richtexts.CodeArea;
import com.allan.baseparty.Action;

public final class EditorAreaAddModifyAction implements Action<CodeArea> {
    private final EditorAreaMgr eb;
    public EditorAreaAddModifyAction(EditorAreaMgr eb) {
        this.eb = eb;
    }

    public EditorAreaAddModifyAction() {
        eb = null;
    }

    @Override
    public void invoke(CodeArea codeArea) {
        if (eb instanceof EditorAreaMgrCode ebk && codeArea instanceof EditorArea selfImpl) {
            selfImpl.getVisibleParagraphs().addModificationObserver(
                    new VisibleParagraphStyler<>(selfImpl, ebk.getHelper().getComputeHighlightFun()));
        }
    }
}

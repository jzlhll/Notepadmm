package com.allan.atools.richtext.codearea;

import com.allan.uilibs.richtexts.MyCodeArea;
import com.allan.baseparty.Action;

public final class EditorAreaAddModifiAction implements Action<MyCodeArea> {
    private final EditorBase eb;
    public EditorAreaAddModifiAction(EditorBase eb) {
        this.eb = eb;
    }

    public EditorAreaAddModifiAction() {
        eb = null;
    }

    @Override
    public void invoke(MyCodeArea myCodeArea) {
        if (eb instanceof EditorBaseImplCode ebk && myCodeArea instanceof EditorAreaImpl selfImpl) {
            selfImpl.getVisibleParagraphs().addModificationObserver(
                    new VisibleParagraphStyler<>(selfImpl, ebk.getHelper().getComputeHighlightFun()));
        }
    }
}

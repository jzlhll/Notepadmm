package com.allan.atools.bean

class MultiSelection(val startParagraph: Int, val endParagraph: Int, val startColumn: Int, val endColumn: Int) {
    override fun toString(): String {
        return "MultiSelection{" +
                "startParagraph=" + startParagraph +
                ", endParagraph=" + endParagraph +
                ", startColumn=" + startColumn +
                ", endColumn=" + endColumn +
                '}'
    }
}
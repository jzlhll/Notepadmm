package com.allan.atools.ui

import javafx.geometry.Insets
import javafx.scene.control.Label
import javafx.scene.layout.Background
import javafx.scene.layout.BackgroundFill
import javafx.scene.layout.CornerRadii
import javafx.scene.paint.Paint

fun setBackground(node:Label, color:String, radius:Double? = 0.0) {
    val backgroundFill = BackgroundFill(Paint.valueOf(color), CornerRadii(radius ?: 0.0), Insets.EMPTY)
    val background = Background(backgroundFill)
    node.background = background
}
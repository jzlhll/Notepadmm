package com.allan.atools.controllermgr

import com.allan.atools.controller.PictureController
import com.allan.atools.controller.PictureController.TAG
import com.allan.atools.utils.Log
import javafx.beans.property.DoubleProperty
import javafx.beans.property.SimpleDoubleProperty
import javafx.geometry.Insets
import javafx.scene.image.Image
import javafx.scene.input.ScrollEvent
import javafx.scene.layout.*
import javafx.scene.paint.Paint
import java.io.File
import java.net.MalformedURLException

class PictureControllerImageMgr(val host: PictureController) {
    companion object {
        @JvmField val WIN_MAX_WIDTH = 1280.0
        @JvmField val WIN_MAX_HEIGHT = 780.0
        @JvmField val WIN_MIN_WIDTH = 750.0
        @JvmField val WIN_MIN_HEIGHT = 520.0
        @JvmField val DELTA_SIZE = 3

        @JvmField val MAX_ZOOM = 2.5
        @JvmField val MIN_ZOOM = 0.05
        @JvmField val STEP_OF_ZOOM = 0.05
    }

    private val zoomProperty: DoubleProperty = SimpleDoubleProperty(1.0)
    fun getZoom():Double {
        return zoomProperty.get();
    }


    var enableColorPickMode:Boolean = false

    fun initZoom() {
        zoomProperty.addListener { _ ->
            zoomPropChanged(zoomProperty.get())
        }

        host.outAnchorPane.addEventFilter(ScrollEvent.ANY
        ) { event: ScrollEvent ->
            if (event.deltaY > 0) {
                zoomBig()
            } else if (event.deltaY < 0) {
                zoomSmall()
            }
        }

        host.currentSizeLabel.setText(
            java.lang.String.format(
                "size: %.1f*%.1f, zoom: %.1f",
                imageWindowSize.defWidth, imageWindowSize.defHeight,
                zoomProperty.get() * 100
            ) + "%"
        )
    }

    private fun zoomPropChanged(zoomProp:Double) {
        Log.d("zoom property changed! $zoomProp")
        if (imageWindowSize.image != null) {
            val w: Double = imageWindowSize.defWidth * zoomProp
            val h: Double = imageWindowSize.defHeight * zoomProp
            host.currentSizeLabel.setText(
                java.lang.String.format(
                    "size: %.1f*%.1f, zoom: %.1f",
                    imageWindowSize.defWidth, imageWindowSize.defHeight,
                    zoomProp * 100
                ) + "%"
            )
            Log.d(
                TAG,
                "after zoom changed: set imageView fit Width And height $w, $h"
            )
            host.imageViewBoxStackPane.setPrefSize(w, h)
            host.imageViewBoxStackPane.setMinSize(w, h)
            host.imageViewBoxStackPane.setMaxSize(w, h)
            //host.imageViewBoxStackPane?.setFitHeight(h)
        }
    }

    fun zoomReset() {
        Log.d("zoom reset!")
        //fix 因为我们默认就是1.0，所以没有变化的时候，就不会重置。但是imageView旋转后不得不重置一下
        if (zoomProperty.get() == 1.0) {
            zoomPropChanged(1.0)
        } else {
            zoomProperty.set(1.0)
        }
    }

    fun zoomBig() {
        if (zoomProperty.get() <= MAX_ZOOM) {
            zoomProperty.set(zoomProperty.get() + STEP_OF_ZOOM)
        }
    }

    fun zoomSmall() {
        if (zoomProperty.get() >= MIN_ZOOM) {
            zoomProperty.set(zoomProperty.get() - STEP_OF_ZOOM)
        }
    }

    data class ImageWindowSizes(val image: Image?, val prepareWindowWidth:Int, val prepareWindowHeight:Int, val scrollPaneOffsetY:Double) {
        val defWidth:Double
        val defHeight:Double

        var deltaRealWindowToDefWidth:Double = 0.0
        var deltaRealWindowToDefHeight:Double = 0.0

        init {
            if (image != null) {
                defWidth = image.width
                defHeight = image.height

            } else {
                defWidth = 10.0
                defHeight = 10.0
            }
        }

        override fun toString(): String {
            return "defSize $defWidth*$defHeight, prepareSize $prepareWindowWidth*$prepareWindowHeight, delta $deltaRealWindowToDefWidth*$deltaRealWindowToDefHeight"
        }

        companion object {
            @JvmStatic
            val Empty: ImageWindowSizes = ImageWindowSizes(null, 0, 0, 0.0);
        }
    }

    var imageWindowSize: ImageWindowSizes = ImageWindowSizes.Empty
    fun loadImage(pathFile:File) {
        try {
            val localUrl = pathFile.toURI().toURL().toString()
            // don"t load in the background
            val localImage = Image(localUrl, false)
            val w = localImage.width
            val h = localImage.height
            val bigW = Math.max(w, h)
            Log.d(TAG, "loadImage size: $w * $h, bigImgWidth $bigW")

            val prepareW:Int
            val prepareH:Int
            if (w > WIN_MAX_WIDTH - DELTA_SIZE) {
                prepareW = WIN_MAX_WIDTH.toInt()
            } else {
                if (w < WIN_MIN_WIDTH) {
                    prepareW = WIN_MIN_WIDTH.toInt() + DELTA_SIZE
                } else {
                    prepareW = w.toInt() + DELTA_SIZE
                }
            }

            if (h > WIN_MAX_HEIGHT - DELTA_SIZE) {
                prepareH = WIN_MAX_HEIGHT.toInt()
            } else {
                if (h < WIN_MIN_HEIGHT) {
                    prepareH = WIN_MIN_HEIGHT.toInt() + DELTA_SIZE
                } else {
                    prepareH = h.toInt() + DELTA_SIZE
                }
            }

            imageWindowSize = ImageWindowSizes(localImage, prepareW, prepareH + host.draggerScrollPane.layoutY.toInt(), host.draggerScrollPane.layoutY)
        } catch (e: MalformedURLException) {
            e.printStackTrace()
        }

    }

    var currentColorHexAndroid:String? = null

    fun attachColorCloth(x:Double, y:Double) {
        val scaleY = y / zoomProperty.get()
        val scaleX = x / zoomProperty.get()

        host.fixWholeWidthLabel.isVisible = true
        val color = host.imageView.image.pixelReader.getColor(scaleX.toInt(), scaleY.toInt())
        val hex = color.toString()
        val backgroundFill = BackgroundFill(Paint.valueOf(hex), CornerRadii(2.0), Insets.EMPTY)
        val background = Background(backgroundFill)
        host.fixWholeWidthLabel.background = background

        host.colorInfoLabel.text = hexColor2Str(hex)
    }

    private fun hexColor2Str(hex:String):String {
        var newHex = hex
        if (hex.startsWith("0x")) {
            newHex = hex.substring(2)
        }
        val len = newHex.length
        if (len == 6) {
            val r = newHex.substring(0, 2).toInt(16)
            val g = newHex.substring(2, 4).toInt(16)
            val b = newHex.substring(4, 6).toInt(16)
            currentColorHexAndroid = String.format("#%02x%02x%02x", r,g, b)
            return String.format("javafx: %s, android: %s", hex, currentColorHexAndroid)
        } else if (len == 8) {
            val r = newHex.substring(0, 2).toInt(16)
            val g = newHex.substring(2, 4).toInt(16)
            val b = newHex.substring(4, 6).toInt(16)
            val a = newHex.substring(6, 8).toInt(16)
            if (a == 255) {
                currentColorHexAndroid = String.format("#%02x%02x%02x", r,g, b)
                return String.format("javafx: %s, android: %s", hex.substring(0, hex.length - 2), currentColorHexAndroid)
            } else {
                currentColorHexAndroid = String.format("#%02x%02x%02x%02x", a, r, g, b)
                return String.format("javafx: %s, android: %s", hex, currentColorHexAndroid)
            }
        } else {
            currentColorHexAndroid = ""
        }
        return "error $hex"
    }
}
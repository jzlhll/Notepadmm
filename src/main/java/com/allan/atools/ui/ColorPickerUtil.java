package com.allan.atools.ui;

import javafx.scene.paint.Color;

public final class ColorPickerUtil {
//    private static double[] getPreDefColors(JFXColorPicker picker) {
//        var obj = ReflectionUtils.iteratorGetPrivateField(picker, "preDefinedColors");
//        if (obj instanceof double[] preColors) {
//            return preColors;
//        }
//
//        return null;
//    }

    public static Color[] getMatchedColors(String bgColor) {
        if (bgColor.startsWith("#")) {
            bgColor = bgColor.substring(1);
        }

        return getOffset(bgColor);
    }

    /**
     * 0~9转变为另外一个数值
     */
    private static int i2o(int i) {
        if (i <= 1) {
            return i + 1;
        }

        if (i <= 4) {
            return i + 2;
        }

        if (i < 9) {
            return i - 2;
        }
        return i - 3;
    }

    private static Color[] getOffset(String _6StrColor) {
        var preDefColors = preDefColors();
        int red = Integer.parseInt(_6StrColor.substring(0, 2), 16);
        int green = Integer.parseInt(_6StrColor.substring(2, 4), 16);
        int blue = Integer.parseInt(_6StrColor.substring(4, 6), 16);

        int ROW_NUM = 30;
        for (int row = 0; row < 19; row++) {
            int startOffset = ROW_NUM * row;
            for (int col = 0; col < 10; col++) {
                int index = startOffset + col * 3;
                if (preDefColors[index] == red && preDefColors[index + 1] == green && preDefColors[index + 2] == blue) {
                    var offCol = i2o(col);
                    var bgColor  = Color.color(
                            preDefColors[startOffset + offCol * 3] / 255d,
                            preDefColors[startOffset + offCol * 3 + 1] / 255d,
                            preDefColors[startOffset + offCol * 3 + 2] / 255d);
                    var textColor = offCol <= 3 ? Color.BLACK : Color.WHITE;
                    return new Color[]{bgColor, textColor};
                }
            }
        }

        throw new RuntimeException(" get offset is null");
    }

    public static double[] preDefColors() {
        return new double[] {
                // WARNING: always make sure the number of colors is a divisable by NUM_OF_COLUMNS
                // first row
                255, 255, 255,
                245, 245, 245,
                238, 238, 238,
                224, 224, 224,
                189, 189, 189,
                158, 158, 158,
                117, 117, 117,
                97, 97, 97,
                50, 50, 50,
                0, 0, 0,
                // second row
                236, 239, 241,
                207, 216, 220,
                176, 190, 197,
                144, 164, 174,
                120, 144, 156,
                96, 125, 139,
                84, 110, 122,
                69, 90, 100,
                55, 71, 79,
                38, 50, 56,
                // third row
                255, 235, 238,
                255, 205, 210,
                239, 154, 154,
                229, 115, 115,
                239, 83, 80,
                244, 67, 54,
                229, 57, 53,
                211, 47, 47,
                198, 40, 40,
                183, 28, 28,
                // forth row
                252, 228, 236,
                248, 187, 208,
                244, 143, 177,
                240, 98, 146,
                236, 64, 122,
                233, 30, 99,
                216, 27, 96,
                194, 24, 91,
                173, 20, 87,
                136, 14, 79,
                // fifth row
                243, 229, 245,
                225, 190, 231,
                206, 147, 216,
                186, 104, 200,
                171, 71, 188,
                156, 39, 176,
                142, 36, 170,
                123, 31, 162,
                106, 27, 154,
                74, 20, 140,
                // sixth row
                237, 231, 246,
                209, 196, 233,
                179, 157, 219,
                149, 117, 205,
                126, 87, 194,
                103, 58, 183,
                94, 53, 177,
                81, 45, 168,
                69, 39, 160,
                49, 27, 146,
                // seventh row
                232, 234, 246,
                197, 202, 233,
                159, 168, 218,
                121, 134, 203,
                92, 107, 192,
                63, 81, 181,
                57, 73, 171,
                48, 63, 159,
                40, 53, 147,
                26, 35, 126,
                // eigth row
                227, 242, 253,
                187, 222, 251,
                144, 202, 249,
                100, 181, 246,
                66, 165, 245,
                33, 150, 243,
                30, 136, 229,
                25, 118, 210,
                21, 101, 192,
                13, 71, 161,
                // ninth row
                225, 245, 254,
                179, 229, 252,
                129, 212, 250,
                79, 195, 247,
                41, 182, 246,
                3, 169, 244,
                3, 155, 229,
                2, 136, 209,
                2, 119, 189,
                1, 87, 155,
                // tenth row
                224, 247, 250,
                178, 235, 242,
                128, 222, 234,
                77, 208, 225,
                38, 198, 218,
                0, 188, 212,
                0, 172, 193,
                0, 151, 167,
                0, 131, 143,
                0, 96, 100,
                // eleventh row
                224, 242, 241,
                178, 223, 219,
                128, 203, 196,
                77, 182, 172,
                38, 166, 154,
                0, 150, 136,
                0, 137, 123,
                0, 121, 107,
                0, 105, 92,
                0, 77, 64,
                // twelfth row
                232, 245, 233,
                200, 230, 201,
                165, 214, 167,
                129, 199, 132,
                102, 187, 106,
                76, 175, 80,
                67, 160, 71,
                56, 142, 60,
                46, 125, 50,
                27, 94, 32,

                // thirteenth row
                241, 248, 233,
                220, 237, 200,
                197, 225, 165,
                174, 213, 129,
                156, 204, 101,
                139, 195, 74,
                124, 179, 66,
                104, 159, 56,
                85, 139, 47,
                51, 105, 30,
                // fourteenth row
                249, 251, 231,
                240, 244, 195,
                230, 238, 156,
                220, 231, 117,
                212, 225, 87,
                205, 220, 57,
                192, 202, 51,
                175, 180, 43,
                158, 157, 36,
                130, 119, 23,

                // fifteenth row
                255, 253, 231,
                255, 249, 196,
                255, 245, 157,
                255, 241, 118,
                255, 238, 88,
                255, 235, 59,
                253, 216, 53,
                251, 192, 45,
                249, 168, 37,
                245, 127, 23,

                // sixteenth row
                255, 248, 225,
                255, 236, 179,
                255, 224, 130,
                255, 213, 79,
                255, 202, 40,
                255, 193, 7,
                255, 179, 0,
                255, 160, 0,
                255, 143, 0,
                255, 111, 0,

                // seventeenth row
                255, 243, 224,
                255, 224, 178,
                255, 204, 128,
                255, 183, 77,
                255, 167, 38,
                255, 152, 0,
                251, 140, 0,
                245, 124, 0,
                239, 108, 0,
                230, 81, 0,

                // eighteenth row
                251, 233, 231,
                255, 204, 188,
                255, 171, 145,
                255, 138, 101,
                255, 112, 67,
                255, 87, 34,
                244, 81, 30,
                230, 74, 25,
                216, 67, 21,
                191, 54, 12,

                // nineteenth row
                239, 235, 233,
                215, 204, 200,
                188, 170, 164,
                161, 136, 127,
                141, 110, 99,
                121, 85, 72,
                109, 76, 65,
                93, 64, 55,
                78, 52, 46,
                62, 39, 35,
        };
    }
//
//    public static void prepareForJFxColorPicker() {
//    }
//
//    public static void changeCustomColorBtnName(JFXColorPicker picker) {
//        Log.d(Locales.str("customColor"));
//        ThreadUtils.executeDelay(150, ()-> {
//            Object skinBase = null;
//            Object popupContent = null;
//            while (true) {
//                if (ThreadUtils.sBeClosing) {
//                    break;
//                }
//
//                try {
//                    if (skinBase == null) {
//                        var skinObject = ReflectionUtils.iteratorGetPrivateField(picker, "skinBase");
//                        if (skinObject instanceof JFXColorPickerSkin) {
//                            skinBase = skinObject;
//                            Log.d("get skin base");
//                        }
//                    }
//                    //不else
//                    if (skinBase != null && popupContent == null) {
//                        popupContent = ReflectionUtils.getPrivateField(skinBase, "popupContent");
//                        Log.d("get popup Content");
//                    }
//                    //不else
//                    if (popupContent != null) {
//                        var customColorLinkObject = ReflectionUtils.getPrivateField(popupContent, "customColorLink");
//                        Log.d("get customColorLinkObject");
//                        if (customColorLinkObject instanceof JFXButton btn) {
//                            Log.d("run customColorLinkObject");
//                            //Platform.runLater(()-> btn.setText(Locales.str("customColor")));
//                            break;
//                        }
//                    }
//
//                    Thread.sleep(100);
//                } catch (NoSuchFieldException e) {
//                    e.printStackTrace();
//                } catch (IllegalAccessException e) {
//                    e.printStackTrace();
//                } catch (InterruptedException e) {
//                    e.printStackTrace();
//                }
//            }
//        });
//    }
}

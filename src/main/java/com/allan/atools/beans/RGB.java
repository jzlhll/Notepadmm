package com.allan.atools.beans;

public final class RGB {
    public int red;
    public int green;
    public int blue;

    public RGB(){ }

    public RGB(int red,int green,int blue){
        this.red = red;
        this.blue = blue;
        this.green = green;
    }

    public String toString() {
        return "RGB {" + red + ", " + green + ", " + blue + "}";
    }

    public static HSL RGB2HSL(RGB rgb) {
        if (rgb == null) {
            return null;
        }

        float H, S, L, var_Min, var_Max, del_Max, del_R, del_G, del_B;

        H = 0;

        var_Min = Math.min(rgb.red, Math.min(rgb.blue, rgb.green));
        var_Max = Math.max(rgb.red, Math.max(rgb.blue, rgb.green));
        del_Max = var_Max - var_Min;
        L = (var_Max + var_Min) / 2;

        if (del_Max == 0) {
            H = 0;
            S = 0;
        } else {
            if (L < 128) {
                S = 256 * del_Max / (var_Max + var_Min);
            } else {
                S = 256 * del_Max / (512 - var_Max - var_Min);
            }

            del_R = ((360 * (var_Max - rgb.red) / 6) + (360 * del_Max / 2))
                    / del_Max;
            del_G = ((360 * (var_Max - rgb.green) / 6) + (360 * del_Max / 2))
                    / del_Max;
            del_B = ((360 * (var_Max - rgb.blue) / 6) + (360 * del_Max / 2))
                    / del_Max;

            if (rgb.red == var_Max) {
                H = del_B - del_G;
            } else if (rgb.green == var_Max) {
                H = 120 + del_R - del_B;
            } else if (rgb.blue == var_Max) {
                H = 240 + del_G - del_R;
            }

            if (H < 0) {
                H += 360;
            }

            if (H >= 360) {
                H -= 360;
            }

            if (L >= 256) {
                L = 255;
            }

            if (S >= 256) {
                S = 255;
            }
        }

        return new HSL(H, S, L);
    }
}

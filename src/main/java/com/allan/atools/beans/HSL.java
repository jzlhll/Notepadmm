package com.allan.atools.beans;

public final class HSL {
    private float h = 0;

    /**
     * 饱和度
     */
    private float s = 0;

    /**
     * 深度
     */
    private float l = 0;

    public HSL() {
    }

    public HSL(float h, float s, float l) {
        setH(h);
        setS(s);
        setL(l);
    }

    public float getH() {
        return h;
    }

    public void setH(float h) {
        if (h < 0) {
            this.h = 0;
        } else if (h > 360) {
            this.h = 360;
        } else {
            this.h = h;
        }
    }

    public float getS() {
        return s;
    }

    public void setS(float s) {
        if (s < 0) {
            this.s = 0;
        } else if (s > 255) {
            this.s = 255;
        } else {
            this.s = s;
        }
    }

    public float getL() {
        return l;
    }

    public void setL(float l) {
        if (l < 0) {
            this.l = 0;
        } else if (l > 255) {
            this.l = 255;
        } else {
            this.l = l;
        }
    }

    public String toString() {
        return "HSL {" + h + ", " + s + ", " + l + "}";
    }

    public static RGB HSL2RGB(HSL hsl) {
        if (hsl == null) {
            return null;

        }
        float H = hsl.getH();
        float S = hsl.getS();
        float L = hsl.getL();
        float R, G, B, var_1, var_2;
        if (S == 0) {
            R = L;
            G = L;
            B = L;
        } else {
            if (L < 128) {
                var_2 = (L * (256 + S)) / 256;
            } else {
                var_2 = (L + S) - (S * L) / 256;
            }

            if (var_2 > 255) {
                var_2 = Math.round(var_2);
            }

            if (var_2 > 254) {
                var_2 = 255;
            }

            var_1 = 2 * L - var_2;
            R = RGBFromHue(var_1, var_2, H + 120);
            G = RGBFromHue(var_1, var_2, H);
            B = RGBFromHue(var_1, var_2, H - 120);
        }

        R = R < 0 ? 0 : R;
        R = R > 255 ? 255 : R;
        G = G < 0 ? 0 : G;
        G = G > 255 ? 255 : G;
        B = B < 0 ? 0 : B;
        B = B > 255 ? 255 : B;
        return new RGB((int) Math.round(R), (int) Math.round(G), (int) Math.round(B));
    }

    /**
     * @param a
     * @param b
     * @param h
     * @return
     */
    public static float RGBFromHue(float a, float b, float h) {
        if (h < 0) {
            h += 360;
        }

        if (h >= 360) {
            h -= 360;
        }

        if (h < 60) {
            return a + ((b - a) * h) / 60;
        }
        if (h < 180) {
            return b;
        }
        if (h < 240) {
            return a + ((b - a) * (240 - h)) / 60;
        }
        return a;
    }
}

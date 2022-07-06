package com.allan.atools.tools.modulecvt;

import java.math.BigInteger;

public final class ConvertCenter {
    private String hex;
    private NumberUnion number;
    private final int type;

    public ConvertCenter(String hex) {
        this.hex = hex.toLowerCase();
        this.type = 2;
    }

    public ConvertCenter(int num) {
        this.number = new NumberUnion();
        this.number.num = num;
        this.type = 0;
    }

    public ConvertCenter(long num) {
        this.number = new NumberUnion();
        this.number.lNum = num;
        this.type = 1;
    }

    public String convert() {
        BigInteger bigint;
        long ln;
        int in;
        switch (this.type) {
            case 1 -> {
                this.hex = Integer.toHexString(this.number.num);
                return "0x" + this.hex;
            }
            case 0 -> {
                this.hex = Long.toHexString(this.number.lNum);
                return "0x" + this.hex;
            }
            case 2 -> {
                bigint = new BigInteger(this.hex, 16);
                ln = bigint.longValue();
                in = bigint.intValue();
                return "long: " + ln + " ,in:" + in;
            }
        }

        return "";
    }

    static final class NumberUnion {
        int num;
        long lNum;
    }

    static final class Type {
        static final int TypeInt = 0;
        static final int TypeLong = 1;
        static final int TypeHex = 2;
    }
}
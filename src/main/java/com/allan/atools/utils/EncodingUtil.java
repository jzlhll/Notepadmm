package com.allan.atools.utils;

import java.io.*;
import java.nio.charset.Charset;

public final class EncodingUtil {
    public static class EncodingInfo {
        public String encoding;
        public int bomSize;

        public EncodingInfo(String encode, int bomSize) {
            this.encoding = encode;
            this.bomSize = bomSize;
        }
    }

    private static final boolean DEBUG = false;

    public static final String CHOISE_ENCODING_UTF8 = "UTF-8";
    public static final String CHOISE_ENCODING_UTF8_NO_BOM = "UTF-8-NO-BOM";
    public static final String CHOISE_ENCODING_UTF32BE = "UTF-32BE";
    public static final String CHOISE_ENCODING_UTF32LE = "UTF-32LE";
    public static final String CHOISE_ENCODING_UTF16BE = "UTF-16BE";
    public static final String CHOISE_ENCODING_UTF16LE = "UTF-16LE";
    public static final String CHOISE_ENCODING_GBK = "GBK";

    public static EncodingInfo forceEncoding(String encoding) {
        EncodingInfo info;
        switch (encoding) {
            case CHOISE_ENCODING_UTF8:
                info = new EncodingInfo(CHOISE_ENCODING_UTF8, 3);
                break;
            case CHOISE_ENCODING_UTF32BE :
                info = new EncodingInfo(CHOISE_ENCODING_UTF32BE, 3);
                break;
            case CHOISE_ENCODING_UTF32LE:
                info = new EncodingInfo(CHOISE_ENCODING_UTF32LE, 3);
                break;
            case CHOISE_ENCODING_UTF16BE:
                info = new EncodingInfo(CHOISE_ENCODING_UTF16BE, 3);
                break;
            case CHOISE_ENCODING_UTF16LE:
                info = new EncodingInfo(CHOISE_ENCODING_UTF16LE, 3);
                break;
            case CHOISE_ENCODING_GBK:
                info = new EncodingInfo(CHOISE_ENCODING_GBK, 3);
                break;
            case CHOISE_ENCODING_UTF8_NO_BOM:
                info = new EncodingInfo(CHOISE_ENCODING_UTF8, 0);
                break;
            default:
                info =
                        //CHOISE_ENCODING_UTF8_NO_BOM
                        new EncodingInfo(CHOISE_ENCODING_UTF8, 0);
                break;
        }
        return info;
    }

    public static EncodingInfo ultimateEncodeDetect(String file) {
        long t = System.currentTimeMillis();
        //第一步，通过头字节判断, 如果有了bom完成
        EncodingInfo info = fileEncoding(file);
        if (info == null) {
            //第二步，通过统计判断是不是GBK
            if(DEBUG) Log.d("Encoding Step2: 检测gbk开始....");
            if (EncodingUtil.isGbk(new File(file))) {
                info = new EncodingInfo(Charset.forName("GBK").name(), 0);
                if(DEBUG) Log.d("Encoding Step2: 经过一个耗时判断，检测到是GBK, 耗时为：" + (System.currentTimeMillis() - t));
                return info;
            }
        } else {
            return info;
        }

        if(DEBUG) Log.d("Encoding Step2: 最终仍然无法确认，耗时为：" + (System.currentTimeMillis() - t) + "  将采用" + DEFAULT.encoding);
        return DEFAULT;
    }

    /**
     * https://stackoverflow.com/questions/26268132/all-inclusive-charset-to-avoid-java-nio-charset-malformedinputexception-input
     *
     * ISO-8859-1 is an all-inclusive charset, in the sense that it's guaranteed not to throw MalformedInputException.
     * So it's good for debugging, even if your input is not in this charset.
     * I had some double-right-quote/double-left-quote characters in my input,
     * and both US-ASCII and UTF-8 threw MalformedInputException on them, but ISO-8859-1 worked.
     * 对于老外而言，上述是可以的。
     * 但是对于我们来讲，使用这个会乱码。所以中文建议使用UTF-8
     */
    public static final EncodingInfo DEFAULT = new EncodingInfo("UTF-8", 0);


    /**
     * 适用于当做第一步检测。通过头来判断。判断文件的编码格式 只能判断带bom的文本，如果非bom文本，GBK、UTF8-no-bom等别的区分不了。
     */
    private static EncodingInfo fileEncoding(String path) {
        try (InputStream inputStream = new FileInputStream(path)) {
            byte[] bom = new byte[4];
            inputStream.read(bom);
            EncodingInfo encoding;
            if ((bom[0] == (byte) 0x00) && (bom[1] == (byte) 0x00) &&
                    (bom[2] == (byte) 0xFE) && (bom[3] == (byte) 0xFF)) {
                encoding = new EncodingInfo("UTF-32BE", 4);
            } else if ((bom[0] == (byte) 0xFF) && (bom[1] == (byte) 0xFE) &&
                    (bom[2] == (byte) 0x00) && (bom[3] == (byte) 0x00)) {
                encoding = new EncodingInfo("UTF-32LE", 4);
            } else if ((bom[0] == (byte) 0xEF) && (bom[1] == (byte) 0xBB) &&
                    (bom[2] == (byte) 0xBF)) {
                encoding = new EncodingInfo("UTF-8", 3);
            } else if ((bom[0] == (byte) 0xFE) && (bom[1] == (byte) 0xFF)) {
                encoding = new EncodingInfo("UTF-16BE", 2);
            } else if ((bom[0] == (byte) 0xFF) && (bom[1] == (byte) 0xFE)) {
                encoding = new EncodingInfo("UTF-16LE", 2);
            } else {
                // Unicode BOM mark not found, unread all bytes
                encoding = null;
            }

            if (encoding == null) {
                if(DEBUG) Log.d("Encoding Step1: 什么都有可能，GBK、GB2312、UTF8(其他)-no-bom等。只是检查了4个字节。");
                return null;
            } else {
                if(DEBUG) Log.d("Encoding Step1: 准确识别带bom: " + encoding);
                return encoding;
            }
        } catch (Exception e) {
            //ignore
            return null;
        }
    }

    /**
     * 判断文件的编码格式 只能判断带bom的文本，如果非bom文本,或者GBK等别的区分不了
     * @deprecated 这个方法，还不如fileEncoding()牛皮。
     */
    private static void fileEncoding2(String path) throws IOException {
        FileInputStream in = new FileInputStream(path);
        BufferedInputStream bin = new BufferedInputStream(in);
        int p = (bin.read() << 8) + bin.read();
        String code = null;
        bin.close();
        in.close();

        switch (p) {
            case 0xefbb:
                code = "UTF-8";
                break;
            case 0xfffe:
                code = "Unicode";
                break;
            case 0xfeff:
                code = "UTF-16BE";
                break;
            default:
                code = "GBK";

        }
    }

    /**
     * 如果文件是utf8编码返回true,反之false
     * @param file
     * @return
     */
    private static Boolean isUtf8(File file) throws IOException {
        boolean isUtf8 = true;
        byte[] buffer = readByteArrayData(file);
        int end = buffer.length;
        for (int i = 0; i < end; i++) {
            byte temp = buffer[i];
            if ((temp & 0x80) == 0) {// 0xxxxxxx
                continue;
            } else if ((temp & 0xC0) == 0xC0 && (temp & 0x20) == 0) {// 110xxxxx 10xxxxxx
                if (i + 1 < end && (buffer[i + 1] & 0x80) == 0x80 && (buffer[i + 1] & 0x40) == 0) {
                    i = i + 1;
                    continue;
                }
            } else if ((temp & 0xE0) == 0xE0 && (temp & 0x10) == 0) {// 1110xxxx 10xxxxxx 10xxxxxx
                if (i + 2 < end && (buffer[i + 1] & 0x80) == 0x80 && (buffer[i + 1] & 0x40) == 0
                        && (buffer[i + 2] & 0x80) == 0x80 && (buffer[i + 2] & 0x40) == 0) {
                    i = i + 2;
                    continue;
                }
            } else if ((temp & 0xF0) == 0xF0 && (temp & 0x08) == 0) {// 11110xxx 10xxxxxx 10xxxxxx 10xxxxxx
                if (i + 3 < end && (buffer[i + 1] & 0x80) == 0x80 && (buffer[i + 1] & 0x40) == 0
                        && (buffer[i + 2] & 0x80) == 0x80 && (buffer[i + 2] & 0x40) == 0
                        && (buffer[i + 3] & 0x80) == 0x80 && (buffer[i + 3] & 0x40) == 0) {
                    i = i + 3;
                    continue;
                }
            }
            isUtf8 = false;
            break;
        }
        return isUtf8;
    }

    /**
     * 如果文件是gbk编码或者gb2312返回true,反之false
     */
    private static boolean isGbk(File file) {
        boolean isGbk = true;
        byte[] buffer = readByteArrayData(file);
        int end = buffer.length;
        for (int i = 0; i < end; i++) {
            byte temp = buffer[i];
            if ((temp & 0x80) == 0) {
                continue;// B0A1-F7FE//A1A1-A9FE
            } else if ((Byte.toUnsignedInt(temp) < 0xAA && Byte.toUnsignedInt(temp) > 0xA0)
                    || (Byte.toUnsignedInt(temp) < 0xF8 && Byte.toUnsignedInt(temp) > 0xAF)) {
                if (i + 1 < end) {
                    if (Byte.toUnsignedInt(buffer[i + 1]) < 0xFF && Byte.toUnsignedInt(buffer[i + 1]) > 0xA0
                            && Byte.toUnsignedInt(buffer[i + 1]) != 0x7F) {
                        i = i + 1;
                        continue;
                    }
                } // 8140-A0FE
            } else if (Byte.toUnsignedInt(temp) < 0xA1 && Byte.toUnsignedInt(temp) > 0x80) {
                if (i + 1 < end) {
                    if (Byte.toUnsignedInt(buffer[i + 1]) < 0xFF && Byte.toUnsignedInt(buffer[i + 1]) > 0x3F
                            && Byte.toUnsignedInt(buffer[i + 1]) != 0x7F) {
                        i = i + 1;
                        continue;
                    }
                } // AA40-FEA0//A840-A9A0
            } else if ((Byte.toUnsignedInt(temp) < 0xFF && Byte.toUnsignedInt(temp) > 0xA9)
                    || (Byte.toUnsignedInt(temp) < 0xAA && Byte.toUnsignedInt(temp) > 0xA7)) {
                if (i + 1 < end) {
                    if (Byte.toUnsignedInt(buffer[i + 1]) < 0xA1 && Byte.toUnsignedInt(buffer[i + 1]) > 0x3F
                            && Byte.toUnsignedInt(buffer[i + 1]) != 0x7F) {
                        i = i + 1;
                        continue;
                    }
                }
            }
            isGbk = false; //如果匹配到了中文区域，则立刻跳出
            break;
        }
        return isGbk;
    }

    private static final long MAX_SIZE_TO_READ_ALL = 2 * 1024 * 1024L;
    private static final int PAGE_SIZE = 1024 * 256;//全读的话，256K一次
    private static final int DETECT_PAGE_SIZE = 1024 * 16; //探测的话，16k一次
    private static final int DETECT_NUM = 100; //探测多少次

    /**
     * 从文件中直接读取字节
     */
    private static byte[] readByteArrayData(File file) {
        byte[] rebyte = null;
        int len;
        try (FileInputStream fis = new FileInputStream(file);
             BufferedInputStream bis = new BufferedInputStream(fis);
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            if (file.length() <= MAX_SIZE_TO_READ_ALL) {
                //如果文件比较小，就读完它；
                byte[] byt = new byte[PAGE_SIZE];
                while ((len = bis.read(byt)) != -1) {
                    if (len < PAGE_SIZE) {
                        output.write(byt, 0, len);
                    } else
                        output.write(byt);
                }
            } else {
                //文件比较大，基于统计的
                byte[] byt = new byte[DETECT_PAGE_SIZE];
                long leftSize = (file.length() - MAX_SIZE_TO_READ_ALL);
                long perSkipSize = leftSize  / (DETECT_NUM + 3); //多除一点，步幅小一点。避免触底。
                int count = DETECT_NUM;
                while ((len = bis.read(byt)) != -1) {
                    if (len < DETECT_PAGE_SIZE) {
                        output.write(byt, 0, len);
                        break;
                    } else
                        output.write(byt);

                    if (--count <= 0) {
                        break;
                    }
                    bis.skip(perSkipSize);
                }
            }

            rebyte = output.toByteArray();
        } catch (Exception e) {
            //ignore
        }

        if(DEBUG) Log.d("Encoding Step2: detect bytes size= " + rebyte.length);
        return rebyte;
    }
}


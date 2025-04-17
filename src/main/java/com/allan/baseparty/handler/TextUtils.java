package com.allan.baseparty.handler;

public final class TextUtils {
private TextUtils() {}
        /**
  * Returns true if a and b are equal, including if they are both null.
  * <p><i>Note: In platform versions 1.1 and earlier, this method only worked well if
  * both the arguments were instances of String.</i></p>
  * @param a first CharSequence to check
  * @param b second CharSequence to check
  * @return true if a and b are equal
  */
        public static boolean equals(CharSequence a, CharSequence b) {
            if (a == b) return true;
            int length;
            if (a != null && b != null && (length = a.length()) == b.length()) {
                    if (a instanceof String && b instanceof String) {
                            return a.equals(b);
                        } else {
                            for (int i = 0; i < length; i++) {
                                    if (a.charAt(i) != b.charAt(i)) return false;
                                }
                            return true;
                        }
                }
            return false;
        }

    /**
     * 如果是null，或者空字符串也相等
     * @param a
     * @param b
     * @return
     */
        public static boolean equalsAllowNullOrEmptyEqual(CharSequence a, CharSequence b) {
            if (a == null && b != null && b.length() == 0) {
                return true;
            }
            if (b == null && a != null && a.length() == 0) {
                return true;
            }
            return equals(a, b);
        }

        /**
  * Returns true if the string is null or 0-length.
  * @param str the string to be examined
  * @return true if str is null or zero length
  */
        public static boolean isEmpty(CharSequence str) {
            return str == null || str.length() == 0;
        }

        public static String nullIfEmpty(String str) {
            return isEmpty(str) ? null : str;
        }

        public static String emptyIfNull(String str) {
            return str == null ? "" : str;
        }

        public static String firstNotEmpty(String a,String b) {
            return !isEmpty(a) ? a : checkStringNotEmpty(b);
        }

        public static  <T extends CharSequence> T checkStringNotEmpty(final T string) {
            if (TextUtils.isEmpty(string)) {
                    throw new IllegalArgumentException();
                }
            return string;
        }

        public static int length(String s) {
            return isEmpty(s) ? 0 : s.length();
        }
}
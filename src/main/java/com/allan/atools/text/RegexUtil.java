package com.allan.atools.text;

import java.util.ArrayList;
import java.util.List;

public final class RegexUtil {
    private RegexUtil(){}

    private static boolean readBase(String str, int[] value, int base, int size)
    {
        int i = 0, temp = 0;
        value[0] = 0;
        //TCHAR max = '0' + static_cast<TCHAR>(base) - 1;
        char max = (char) ('0' + base - 1);

        char current;
        while (i < size)
        {
            current = str.charAt(i);
            if (current >= 'A')
            {
                current &= 0xdf;
                current -= ('A' - '0' - 10);
            }
            else if (current > '9')
                return false;

            if (current >= '0' && current <= max)
            {
                temp *= base;
                temp += (current - '0');
            }
            else
            {
                return false;
            }
            ++i;
        }
        value[0] = temp;
        return true;
    }

    public static String convertSearchWords(String words) {
        List<Character> result = new ArrayList<>();

        int i = 0;
        int length = words.length();
        int charLeft = length;
        char[] query = words.toCharArray();
        char current;
        while (i < length)
        {	//because the backslash escape quences always reduce the size of the generic_string,
            // no overflow checks have to be made for target, assuming parameters are correct
            current = query[i];
            --charLeft;
            if (current == '\\' && charLeft > 0)
            {	//possible escape sequence
                ++i;
                --charLeft;
                current = query[i];
                switch(current)
                {
                    case 'r':
                        result.add('\r');
                        break;
                    case 'n':
                        result.add('\n');
                        break;
                    case '0':
                        result.add('\0');
                        break;
                    case 't':
                        result.add('\t');
                        break;
                    case '\\':
                        result.add('\\');
                        break;
                    case 'b':
                    case 'd':
                    case 'o':
                    case 'x':
                    case 'u':
                    {
                        int size = 0, base = 0;
                        if (current == 'b')
                        {	//11111111
                            size = 8; base = 2;
                        }
                        else if (current == 'o')
                        {	//377
                            size = 3; base = 8;
                        }
                        else if (current == 'd')
                        {	//255
                            size = 3; base = 10;
                        }
                        else if (current == 'x')
                        {	//0xFF
                            size = 2; base = 16;
                        }
                        else if (current == 'u')
                        {	//0xCDCD
                            size = 4; base = 16;
                        }

                        if (charLeft >= size)
                        {
                            int[] res = {0};
                            //c++ //readBase( query+(i+1), &res, base, size)
                            if (readBase(words.substring(i + 1), res, base, size))
                            {
                                //c++ // result[j] = static_cast<TCHAR>(res);
                                //to java start
                                var casted = ("" + res[0]).toCharArray();
                                for (var ch : casted) {
                                    result.add(ch);
                                }
                                // to java end
                                i += size;
                                break;
                            }
                        }
                        //not enough chars to make parameter, use default method as fallback
                    }

                    default:
                    {	//unknown sequence, treat as regular text
                        result.add('\\');
                        result.add(current);
                        break;
                    }
                }
            }
            else
            {
                result.add(query[i]);
            }
            ++i;
        }

        //result[j] = 0; c++最后TCHAR 需要补0

        StringBuilder sb = new StringBuilder();
        for (var c : result) {
            sb.append(c);
        }
        return sb.toString();
    }

    public static void main(String[] args) {
        String s = "test中国";
        System.out.println("11: " + convertSearchWords(s));
        s = "aaa.*bbb";
        System.out.println("22: " + convertSearchWords(s));
        s = "aaa.*中国";
        System.out.println("33: " + convertSearchWords(s));
        s = "bb^dd";
        System.out.println("44: " + convertSearchWords(s));
        s = "bb->dd";
        System.out.println("55: " + convertSearchWords(s));
        s = "bb->dd\\x";
        System.out.println("66: " + convertSearchWords(s));
    }
}

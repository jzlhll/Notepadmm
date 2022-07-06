package com.allan.atools.text.beans;

public final class CharDefineHelper {
    private static final int DEF_SPLIT = 0;
    private static final int DEF_BASE_LATIN = 1;
    private static final int DEF_EX_LATIN = 2;
    private static final int DEF_CJK = 1001;
    /**
     * 给出一个字符的定义！
     * 如果是0表示他是一种分隔符；不论是任何语言体系的；该char本身都与任何char不组合word；
     * 如果大于0，则按照规定，相同的define可以形成word。不同的define不能组成word。
     */
    public static int define(char c) {
        if (c <= 127) {
            if (c == 95) { //_下划线 当做基础文字
                return DEF_BASE_LATIN; //基础拉丁字母
            }
            if ((c <= 47)  //ASCRII 0 ~ 31, 空格 ~ /
                    || (c >= 58 && c <= 64) //: ~ @
                    || (c >= 91 && c <= 96) //[ ~ `
                    || (c >= 123) // { ~ DEL) {
            ) {
                return DEF_SPLIT;
            }

            return DEF_BASE_LATIN; //基础拉丁字母
        }

        //CJK范围 重新编排顺序。加速比较
        if (c > 0x2E7F && c <= 0x9FFF) {
            if (c > 0x4dFF && c <= 0x9FFF) {
                //中日韩统一表意文字
                //范围: 4E00—9FFF 字符总数: 20992
                return DEF_CJK;
            }
            //中日韩符号和标点
            //范围: 3000—303F 字符总数: 64
            if (c > 0x2FFF && c <= 0x303F) { //提前到前面
                return DEF_SPLIT;
            }

            //带圈中日韩字母和月份
            //范围: 3200—32FF 字符总数: 256
            if (c > 0x31FF && c <= 0x32ff) { //中前
                return DEF_SPLIT;
            }

            if (c > 0x32ff && c <= 0x4DBF) { //中前
                //中日韩字符集兼容
                //范围: 3300—33FF 字符总数: 256
                //中日韩统一表意文字扩展A
                //范围: 3400—4DBF 字符总数: 6592
                return DEF_CJK;
            }

            if (c > 0x303F && c <= 0x318F) { //提前到中间
                //日文平假名
                //范围: 3040—309F 字符总数: 96
                //日文片假名
                //范围: 30A0—30FF 字符总数: 96
                //注音字母
                //范围: 3100—312F 字符总数: 48
                //谚文兼容字母
                //范围: 3130—318F 字符总数: 96
                return DEF_CJK;
            }

            if (c <= 0x2EFF) { ////提前到中后
                //中日韩部首补充
                //范围: 2E80—2EFF 字符总数: 128
                //康熙部首
                //范围: 2F00—2FDF 字符总数: 224
                return DEF_CJK;
            }

            if (c > 0x2EFF && c <= 0x2FFF) { //最后
                //表意文字描述符
                //范围: 2FF0—2FFF 字符总数: 16
                return DEF_SPLIT;
            }


            if (c > 0x318F && c <= 0x319F) { //最后
                //象形字注释标志
                //范围: 3190—319F 字符总数: 16
                return DEF_SPLIT;
            }

            if (c > 0x319F && c <= 0x31FF) {  //最后
                //注音字母扩展
                //范围: 31A0—31BF 字符总数: 32
                //中日韩笔画
                //范围: 31C0—31EF 字符总数: 48
                //日文片假名语音扩展
                //范围: 31F0—31FF 字符总数: 16
                return DEF_CJK;
            }

            if (c > 0x4DBF && c <= 0x4DFF) { //最后
                //易经六十四卦符号
                //范围: 4DC0—4DFF 字符总数: 64
                return DEF_SPLIT;
            }
        }

        if (c <= 255) { //拉丁文补充1
            if (c == 169 || c == 170
                    || (c >= 174 && c <= 211) //都认为是分隔符
                    || (c >= 214 && c <= 223)
                    || (c >= 240 && c <= 243)
                    || (c >= 246)
            ) {
                return DEF_SPLIT;
            }

            return DEF_EX_LATIN;
        }

        //不太常用的
        if (c <= 0x10FF) {
            if (c <= 0x024f) {//拉丁扩展A B
                if (c >= 0x01c0 && c <= 0x01c3) {
                    return DEF_SPLIT;
                }

                return DEF_EX_LATIN;
            }

            if (c <= 0x02AF) { //国际音标
                return 3;
            }

            if (c <= 0x036F) { //0x2b00 0x02FF 占位修饰符号 0300—036F  都当做分隔符
                return DEF_SPLIT;
            }

            if (c <= 0x03FF) { //希腊字母及科普特字母
                if (c == 0x0374
                        || c == 0x0375
                        || c >= 0x037a && c <= 0x0387
                        || c == 0x03F5
                        || c == 0x03f6
                        || c >= 0x03fc) {
                    return DEF_SPLIT;
                }

                return 4;
            }

            if (c <= 0x052F) { //西里尔字母 0400—04FF 西里尔字母补充 0500—052F
                return 5;
            }

            if (c <= 0x058F) { //亚美尼亚字母 0530—058F
                //todo
                return 6;
            }

            if (c <= 0x05FF) { //希伯来文 0590—05FF
                return 7; //todo
            }

            if (c <= 0x06FF) { //阿拉伯文 0600—06FF
                return 8; //todo
            }

            if (c <= 0x074F) { //叙利亚文 0700—074F
                return 9; //todo
            }

            if (c <= 0x077F) { //阿拉伯文补充0750—077F
                return 8;
            }

            if (c <= 0x07BF) { //它拿字母0780—07BF
                return 10;
            }

            if (c <= 0x07FF) { //西非书面语言 07C0—07FF
                return 11; //todo
            }

            if (c <= 0x083F) { //撒玛利亚字母 范围: 0800—083F 字符总数: 64
                return 12; //todo
            }

            if (c <= 0x085F) { //Mandaic 围: 0840—085F 字符总数: 32
                return 13; //todo
            }

            if (c <= 0x086F) { //Syriac Supplement 范围: 0860—086F 字符总数: 16
                return 14; //todo
            }

            if (c <= 0x08FF) { //阿拉伯语扩展 范围: 08A0—08FF 字符总数: 96
                return 8; //todo
            }

            if (c <= 0x0DFF) { //天城文 范围: 0900—097F 字符总数: 128
                //孟加拉文 范围: 0980—09FF 字符总数: 128
                //果鲁穆奇字母  范围: 0A00—0A7F 字符总数: 128
                //古吉拉特文 范围: 0A80—0AFF 字符总数: 128
                //奥里亚文 范围: 0B00—0B7F 字符总数: 128
                //泰米尔文 范围: 0B80—0BFF 字符总数: 128
                //泰卢固文 范围: 0C00—0C7F 字符总数: 128
                //卡纳达文 范围: 0C80—0CFF 字符总数: 128
                //马拉雅拉姆文
                //范围: 0D00—0D7F 字符总数: 128
                //僧伽罗文
                //范围: 0D80—0DFF 字符总数: 128
                return 15; //todo 暂时将这些总结起来
            }

            if (c <= 0x0E7F) { //泰文 范围: 0E00—0E7F 字符总数: 128
                return 16;
            }

            if (c <= 0x0EFF) { //老挝文 范围: 0E80—0EFF 字符总数: 128
                return 17;
            }

            if (c <= 0x0FFF) { //藏文   范围: 0F00—0FFF 字符总数: 256
                return 18;
            }

            if (c <= 0x10FF) { //缅甸文 范围: 1000—109F 字符总数: 160
                //格鲁吉亚字母
                //范围: 10A0—10FF 字符总数: 96
                return 19;
            }
        }
        //谚文字母 范围: 1100—11FF 字符总数: 256
        if (c <= 0x11FF) {
            return DEF_CJK;
        }
        //不太常用的
        if (c <= 0x2E7F) {
            if (c <= 0x139F) { //埃塞俄比亚语 范围: 1200—137F 字符总数: 384
                //埃塞俄比亚语补充
                //范围: 1380—139F 字符总数: 32
                return 21;
            }

            if (c <= 0x13FF) { //切罗基字母 范围: 13A0—13FF 字符总数: 96
                return 22;
            }

            if (c <= 0x17FF) { //统一加拿大原住民音节文字 范围: 1400—167F 字符总数: 640
                //欧甘字母
                //范围: 1680—169F 字符总数: 32
                //卢恩字母
                //范围: 16A0—16FF 字符总数: 96
                //他加禄字母
                //范围: 1700—171F 字符总数: 32
                //哈努诺文
                //范围: 1720—173F 字符总数: 32
                //布迪文
                //范围: 1740—175F 字符总数: 32
                //塔格巴努亚文
                //范围: 1760—177F 字符总数: 32
                //高棉文
                //范围: 1780—17FF 字符总数: 128
                return 23;
            }

            if (c <= 0x18AF) { //蒙古文 范围: 1800—18AF 字符总数: 176
                if (c <= 0x180f) {
                    return DEF_SPLIT;
                }
                return 24;
            }

            if (c <= 0x1A1F) {
                //统一加拿大原住民音节文字扩展
                //范围: 18B0—18FF 字符总数: 80
                //林布文
                //范围: 1900—194F 字符总数: 80
                //德宏傣文
                //范围: 1950—197F 字符总数: 48
                //新傣仂文
                //范围: 1980—19DF 字符总数: 96
                //高棉文符号
                //范围: 19E0—19FF 字符总数: 32
                //布吉文
                //范围: 1A00—1A1F 字符总数: 32
                return 23;
            }

            if (c <= 0x1AAF) {
                //老傣文
                //范围: 1A20—1AAF 字符总数: 144
                return 25;
            }

            if (c <= 0x1CCF) {
                //Combining Diacritical Marks Extended
                //范围: 1AB0—1AFF 字符总数: 80
                //巴厘字母
                //范围: 1B00—1B7F 字符总数: 128
                //巽他字母
                //范围: 1B80—1BBF 字符总数: 64
                //巴塔克文
                //范围: 1BC0—1BFF 字符总数: 64
                //雷布查字母
                //范围: 1C00—1C4F 字符总数: 80
                //Ol-Chiki
                //范围: 1C50—1C7F 字符总数: 48
                //Cyrillic Extended C
                //范围: 1C80—1C8F 字符总数: 16
                //Georgian Extended
                //范围: 1C90—1CBF 字符总数: 48
                //巽他字母补充
                //范围: 1CC0—1CCF 字符总数: 16
                return 26;
            }

            if (c <= 0x1CFF) {
                //吠陀梵文
                //范围: 1CD0—1CFF 字符总数: 48
                return 27;
            }

            if (c <= 0x1DBF) {
                //语音学扩展
                //范围: 1D00—1D7F 字符总数: 128
                //语音学扩展补充
                //范围: 1D80—1DBF 字符总数: 64
                return 28;
            }

            if (c <= 0x1dff) { //结合附加符号补充 范围: 1DC0—1DFF 字符总数: 64
                return DEF_SPLIT;
            }

            if (c <= 0x1EFF) {
                //拉丁文扩展附加
                //范围: 1E00—1EFF 字符总数: 256
                return DEF_EX_LATIN;
            }

            if (c <= 0x1FFF) {
                //希腊语扩展
                //范围: 1F00—1FFF 字符总数: 256
                return 4;
            }

            if (c <= 0x2BFF) {
                //常用标点
                //范围: 2000—206F 字符总数: 112
                //上标及下标
                //范围: 2070—209F 字符总数: 48
                //货币符号
                //范围: 20A0—20CF 字符总数: 48
                //组合用记号
                //范围: 20D0—20FF 字符总数: 48
                //字母式符号
                //范围: 2100—214F 字符总数: 80
                //数字形式
                //范围: 2150—218F 字符总数: 64
                //箭头
                //范围: 2190—21FF 字符总数: 112
                //数学运算符
                //范围: 2200—22FF 字符总数: 256
                //杂项工业符号
                //范围: 2300—23FF 字符总数: 256
                //控制图片
                //范围: 2400—243F 字符总数: 64
                //光学识别符
                //范围: 2440—245F 字符总数: 32
                //带圈或括号的字母数字
                //范围: 2460—24FF 字符总数: 160
                //制表符
                //范围: 2500—257F 字符总数: 128
                //方块元素
                //范围: 2580—259F 字符总数: 32
                //几何图形
                //范围: 25A0—25FF 字符总数: 96
                //杂项符号
                //范围: 2600—26FF 字符总数: 256
                //印刷符号
                //范围: 2700—27BF 字符总数: 192
                //杂项数学符号A
                //范围: 27C0—27EF 字符总数: 48
                //杂项符号和箭头
                //范围: 2B00—2BFF 字符总数: 256
                return DEF_SPLIT;
            }

            if (c <= 0x2C5F) {
                //格拉哥里字母
                //范围: 2C00—2C5F 字符总数: 96
                return 29;
            }

            if (c <= 0x2C7F) {
                //拉丁文扩展C
                //范围: 2C60—2C7F 字符总数: 32
                return DEF_EX_LATIN;
            }

            if (c <= 0x2CFF) {
                //科普特字母
                //范围: 2C80—2CFF 字符总数: 128
                return 30;
            }

            if (c <= 0x2DFF) {
                //格鲁吉亚字母补充
                //范围: 2D00—2D2F 字符总数: 48
                //提非纳文
                //范围: 2D30—2D7F 字符总数: 80
                //埃塞俄比亚语扩展
                //范围: 2D80—2DDF 字符总数: 96
                //西里尔字母扩展
                //范围: 2DE0—2DFF 字符总数: 32
                return 27;
            }

            if (c <= 0x2E7F) {
                //追加标点
                //范围: 2E00—2E7F 字符总数: 128
                return DEF_SPLIT;
            }
        }

        if (c <= 0xA4CF) {
            //彝文音节
            //范围: A000—A48F 字符总数: 1168
            //彝文字根
            //范围: A490—A4CF 字符总数: 64
            return 31;
        }

        if (c <= 0xA63F) {
            //Lisu
            //范围: A4D0—A4FF 字符总数: 48
            //老傈僳文
            //范围: A500—A63F 字符总数: 320
            return 31;
        }

        if (c <= 0xA69F) {
            //西里尔字母扩展B
            //范围: A640—A69F 字符总数: 96
            return 27;
        }

        if (c <= 0xA6FF) {
            //巴姆穆语
            //范围: A6A0—A6FF 字符总数: 96
            return 28;
        }

        if (c <= 0xA71F) {
            //声调修饰字母
            //范围: A700—A71F 字符总数: 32
            return DEF_SPLIT;
        }

        if (c <= 0xA7FF) {
            //拉丁文扩展D
            //范围: A720—A7FF 字符总数: 224
            return DEF_EX_LATIN;
        }

        if (c <= 0xA95F) {
            //锡尔赫特文
            //范围: A800—A82F 字符总数: 48
            //印第安数字
            //范围: A830—A83F 字符总数: 16
            //八思巴文
            //范围: A840—A87F 字符总数: 64
            //索拉什特拉
            //范围: A880—A8DF 字符总数: 96
            //天城文扩展
            //范围: A8E0—A8FF 字符总数: 32
            //克耶字母
            //范围: A900—A92F 字符总数: 48
            //勒姜语
            //范围: A930—A95F 字符总数: 48
            return 27;
        }

        if (c <= 0xA97F) {
            //谚文字母扩展A
            //范围: A960—A97F 字符总数: 32
            return DEF_CJK;
        }

        if (c <= 0xAA7F) {
            //爪哇语
            //范围: A980—A9DF 字符总数: 96
            //Myanmar Extended-B
            //范围: A9E0—A9FF 字符总数: 32
            //鞑靼文
            //范围: AA00—AA5F 字符总数: 96
            //缅甸语扩展
            //范围: AA60—AA7F 字符总数: 32
            return 31;
        }

        if (c <= 0xAADF) {
            //越南傣文
            //范围: AA80—AADF 字符总数: 96
            return 32;
        }

        if (c <= 0xAB2F) {
            //曼尼普尔文扩展
            //范围: AAE0—AAFF 字符总数: 32
            //埃塞俄比亚文
            //范围: AB00—AB2F 字符总数: 48
            return 33;
        }

        if (c <= 0xAB6F) {
            //Latin Extended-E
            //范围: AB30—AB6F 字符总数: 64
            return DEF_EX_LATIN;
        }

        if (c <= 0xABBF) {
            //Cherokee Supplement
            //范围: AB70—ABBF 字符总数: 80
            return DEF_SPLIT;
        }

        if (c <= 0xABFF) {
            //曼尼普尔文
            //范围: ABC0—ABFF 字符总数: 64
            return 34;
        }

        if (c <= 0xD7AF) {
            //谚文音节
            //范围: AC00—D7AF 字符总数: 11184
            return DEF_CJK;
        }

        if (c <= 0xD7FF) {
            //Hangul Jamo Extended-B
            //范围: D7B0—D7FF 字符总数: 80
            return 35;
        }

        if (c <= 0xF8FF) {
            //代理对高位字
            //范围: D800—DB7F 字符总数: 896
            //代理对私用区高位字
            //范围: DB80—DBFF 字符总数: 128
            //代理对低位字
            //范围: DC00—DFFF 字符总数: 1024
            //私用区
            //范围: E000—F8FF 字符总数: 6400
            return DEF_SPLIT;
        }

        if (c <= 0xfaff) {
            //中日韩兼容表意文字
            //范围: F900—FAFF 字符总数: 512
            return DEF_CJK;
        }

        if (c <= 0xfb4f) {
            //字母表达形式（拉丁字母连字、亚美尼亚字母连字、希伯来文表现形式）
            //范围: FB00—FB4F 字符总数: 80
            return 36;
        }

        if (c <= 0xFDFF) {
            //阿拉伯文表达形式A
            //范围: FB50—FDFF 字符总数: 688
            return 28;
        }

        //异体字选择符
        //范围: FE00—FE0F 字符总数: 16
        //竖排形式
        //范围: FE10—FE1F 字符总数: 16
        //组合用半符号
        //范围: FE20—FE2F 字符总数: 16
        //中日韩兼容形式
        //范围: FE30—FE4F 字符总数: 32
        //中日韩兼容形式
        //范围: FE30—FE4F 字符总数: 32
        //阿拉伯文表达形式B
        //范围: FE70—FEFF 字符总数: 144
        //半角及全角形式
        //范围: FF00—FFEF 字符总数: 240
        //特殊
        //范围: FFF0—FFFF 字符总数: 16
        return DEF_SPLIT;
    }

}


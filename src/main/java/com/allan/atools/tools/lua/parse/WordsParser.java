package com.allan.atools.tools.lua.parse;

final class WordsParser {
    public interface ICharsProvider {
        Character readNext();
        void markStart();
        void markEnd();
    }

    private final ICharsProvider mProvider;

    public WordsParser(ICharsProvider provider) {
        mProvider = provider;
    }

    private int leftYinhao = 0; //这个级别最高
    private int leftZhongKuohao = 0;
    private int leftBigKuohao = 0; //这个最低

    private boolean isEnd() {
        return leftYinhao == 0 && leftBigKuohao == 0 && leftZhongKuohao == 0;
    }

    private boolean isInYinhao() {
        return leftYinhao > 0;
    }

    private boolean isInLeftZhong() {
        return leftZhongKuohao > 0;
    }

    private boolean isInLeftBig() {
        return leftBigKuohao > 0;
    }

    public boolean parse() {
        mProvider.markStart();

        do{
            Character c = mProvider.readNext();
            if (c == null) {
                mProvider.markEnd();
                return false; //彻底结束
            }

            if (isEnd()) {
                if (c == ',') {
                    mProvider.markEnd();
                    return true; //一段结束
                }

                if (c == '\"') {
                    leftYinhao++;
                } else if (c == '{') {
                    leftBigKuohao++;
                } else if (c == '[') {
                    leftZhongKuohao++;
                }
            } else {
                if (c == '\\') {  //只要是转义，就跳过下一个字节
                    mProvider.readNext();
                    continue;
                }

                if(isInYinhao()) { //级别高的出现了以后，就优先在这里面做逻辑
                    if (c == '\"') {
                        leftYinhao--;
                    }
                    //其他的中括号，大括号在这种情况都算作key所以不用管
                } else if(isInLeftZhong()) {
                    if (c == ']') {
                        leftZhongKuohao--;
                    } if (c == '\"') {
                        leftYinhao++;
                    }
                } else if(isInLeftBig()) {
                    if (c == '}') {
                        leftBigKuohao--;
                    } if (c == '\"') {
                        leftYinhao++;
                    } if (c == '[') {
                        leftZhongKuohao++;
                    } if (c == '{') {
                        leftBigKuohao++;
                    }
                }
            }
        } while (true);
    }
}

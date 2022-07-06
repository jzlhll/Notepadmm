package com.allan.atools.text.normal;

import com.allan.atools.beans.ResultItem;
import com.allan.atools.beans.ResultItemWrap;
import com.allan.atools.utils.Log;
import com.allan.atools.bean.SearchParams;
import com.allan.atools.text.AbstractFinder;
import com.allan.atools.text.beans.CharDefineHelper;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.regex.Pattern;

public final class FinderRegexImpl extends AbstractFinder {
    private static final String TAG = FinderRegexImpl.class.getSimpleName();
    private static final boolean DEBUG = false;

    private final IWrap[] wraps;

    private final List<ResultItem> tempResultItems = new ArrayList<>(4); //todo 以后改造多线程加速下需要变动他；

    public FinderRegexImpl(String text, boolean lineNum, SearchParams[] searchParams, int[] totalFileLineCount) {
        super(text, lineNum, searchParams, totalFileLineCount);
        if(DEBUG) printWraps(searchParams);

        List<IWrap> wrapList = new ArrayList<>();
        for (var p : searchParams) {
            wrapList.addAll(List.of(toWrap(p)));
        }
        wraps = new IWrap[wrapList.size()];
        for (int i = 0; i < wraps.length; i++) {
            wraps[i] = wrapList.get(i);
        }
        wrapList.clear();
    }

    private void printWraps(SearchParams[] ps) {
        Log.d(TAG, "========start==========");
        for (var pa : ps) {
            Log.d(TAG, pa.toString());
        }
        Log.d(TAG, "wrap后，搜索参数如下:");
        for (var wrap : wraps) {
            Log.d(TAG, wrap.toString());
        }
        Log.d(TAG, "==========end==========");
    }

    private ResultItemWrap oneLineHandle(LineWrap lineWrap, int linenum, SearchParamsWrapRule wrap, ResultItemWrap lineItem) {
        var m = wrap.pattern.matcher(lineWrap.line); // 获取 matcher 对象

        do {
            if (!m.find()) {
                break;
            }

            int start = m.start();
            int end = m.end();

            if (lineItem == null) { //表示新的一行
                var item = new ResultItemWrap();
                item.setLine(isSystemUseLineNum ? String.format(mFormat, linenum, lineWrap.line) : lineWrap.line);
                item.setOrigLine(lineWrap.line);
                item.resultOffset = isSystemUseLineNum ? mFormatLineNumOffset : 0;
                item.lineNum = linenum;

                var resultItem = new ResultItem();
                resultItem.matchWord = lineWrap.line.substring(start, end);
                resultItem.range = new ResultItem.Range(start, end, lineWrap.offset + start);
                resultItem.searchParams = wrap.originParam;
                tempResultItems.add(resultItem);

                lineItem = item; //记录下，最后返回回去
                //todo 循环
            } else {
                var newItem = new ResultItem();
                //no need newItem.line = isSystemUseLineNum ? String.format(mFormat, linenum, lineWrap.line) : lineWrap.line;
                newItem.matchWord = lineWrap.line.substring(start, end);
                newItem.range = new ResultItem.Range(start, end, lineWrap.offset + start);
                newItem.searchParams = wrap.originParam;

                int insert = 0;
                for (var ti : tempResultItems) {
                    if (newItem.range.start < ti.range.start) {
                        break;
                    }
                    insert++;
                }
                tempResultItems.add(insert, newItem);
            }
        } while (true);

        return lineItem;
    }

    private ResultItemWrap oneLineHandle(LineWrap lineWrap, int linenum, SearchParamsWrapSimple wrap, ResultItemWrap lineItem) {
        int fromIndex = 0;
        int[] indexes;
        String cvtLine = wrap.originParam.useCaseMatch ? lineWrap.line : lineWrap.line.toLowerCase();
        int cvtLineLength = cvtLine.length();

        do {
            if (fromIndex >= cvtLineLength) { //二~N次循环跳出
                break;
            }

            boolean accept = true;
            if (wrap.splits.length == 1) { // 普通模式
                int index = cvtLine.indexOf(wrap.splits[0], fromIndex);
                if (index == -1) {
                    //不用赋值了；直接跳出
                    break;
                } else {
                    indexes = new int[] {index};
                    fromIndex = index + wrap.splits[0].length(); //递增当前char位置
                }
            } else { // .*模式
                indexes = new int[wrap.splits.length];
                int j = 0;
                for (var word : wrap.splits) {
                    int index = cvtLine.indexOf(word, fromIndex);
                    if (index == -1) { //.*的情况下，只要1个没匹配到就跳出不accept此行
                        accept = false;
                        break;
                    }
                    indexes[j++] = index;
                    fromIndex = index + word.length();//递增当前char位置
                }
            }

            if (!accept) {
                break;
            }

            if (wrap.originParam.useWholeWords) {
                //全词匹配模式下，我们就需要额外判断左侧，右侧的char是不是跟start, end同族

                //左侧-1的char判断是不是分隔符。如果是分隔符；就表明已经是whole了。
                if (wrap.startCharDefine > 0) {
                    int fistIndex = indexes[0];
                    if (fistIndex > 0) {
                        var c = cvtLine.charAt(fistIndex - 1);
                        if (CharDefineHelper.define(c) == wrap.startCharDefine) {
                            continue; //全词匹配下，左边-1的char与start是同族的情况下，我们就认为不是whole就跳出往下匹配
                        }
                    }
                }

                //右侧+1的char判断是不是分隔符
                if (wrap.endCharDefine > 0) {
                    int lastIndex = indexes[indexes.length - 1];
                    int l = wrap.last.length() + lastIndex;
                    if (l < cvtLineLength - 1) {
                        if (CharDefineHelper.define(cvtLine.charAt(l)) == wrap.endCharDefine) {
                            continue;
                        }
                    }
                }
            }

            if (lineItem == null) { //表示新的一行
                var item = new ResultItemWrap();
                item.setLine(isSystemUseLineNum ? String.format(mFormat, linenum, lineWrap.line) : lineWrap.line);
                item.setOrigLine(lineWrap.line);
                item.resultOffset = isSystemUseLineNum ? mFormatLineNumOffset : 0;
                item.lineNum = linenum;

                var resultItem = new ResultItem();
                resultItem.matchWord = lineWrap.line.substring(indexes[0], fromIndex);
                resultItem.range = new ResultItem.Range(indexes[0], fromIndex, lineWrap.offset + indexes[0]);
                resultItem.searchParams = wrap.originParam;
                tempResultItems.add(resultItem);

                lineItem = item; //记录下，最后返回回去
                //todo 循环
            } else {
                var newItem = new ResultItem();
                //no need newItem.line = isSystemUseLineNum ? String.format(mFormat, linenum, lineWrap.line) : lineWrap.line;
                newItem.matchWord = lineWrap.line.substring(indexes[0], fromIndex);
                newItem.range = new ResultItem.Range(indexes[0], fromIndex, lineWrap.offset + indexes[0]);
                newItem.searchParams = wrap.originParam;

                int insert = 0;
                for (var ti : tempResultItems) {
                    if (newItem.range.start < ti.range.start) {
                        break;
                    }
                    insert++;
                }
                tempResultItems.add(insert, newItem);
            }
        } while (true);

        return lineItem;
    }

    @Override
    public List<ResultItemWrap> find() {
        setIsStarted(true);

        int i = 0;
        if (DEBUG) {
            Log.d(TAG, "=======>>>>>> find start>>>>>>");
        }
        for (var origLine : mLines) {
            if (getIsStarted()) { //外部通知停止
                break;
            }

            tempResultItems.clear();
            ResultItemWrap item = null;
            for (var wrap : wraps) {
                if (tempResultItems.size() > 0) {
                    if (wrap instanceof SearchParamsWrapSimple s) {
                        oneLineHandle(origLine, i, s, item);
                    } else if(wrap instanceof SearchParamsWrapRule r) {
                        oneLineHandle(origLine, i, r, item);
                    }
                } else {
                    if (wrap instanceof SearchParamsWrapSimple s) {
                        item = oneLineHandle(origLine, i, s, null);
                    } else if(wrap instanceof SearchParamsWrapRule r) {
                        item = oneLineHandle(origLine, i, r, null);
                    }
                }
            }

            if (item != null) {
                if (tempResultItems.size() > 1) {
                    //先排序，后过滤。冲突的元素
                    if (DEBUG) {
                        for (var rItem : tempResultItems) {
                            Log.d(TAG, "before: " + item.lineNum + "行：" + rItem.range.start + ", " + rItem.range.end);
                        }
                    }

                    //先按照start排序；并且end大的放在前面。
                    tempResultItems.sort((a, b) -> {
                        int d = a.range.start - b.range.start;
                        if (d == 0) {
                            return b.range.end - a.range.end;
                        }

                        return d;
                    });

                    var last = tempResultItems.get(0);
                    if (DEBUG) {
                        for (var rItem : tempResultItems) {
                            Log.d(TAG, "center : " + item.lineNum + "行：" + rItem.range.start + ", " + rItem.range.end);
                        }
                    }
                    //去除有重叠的数据
                    for (int a = 1; a < tempResultItems.size(); a++) { //中间的判断；不得精简size()成为常量
                        var cur = tempResultItems.get(a);
                        if (cur.range.start < last.range.end) {
                            tempResultItems.remove(a);
                            a--;
                        } else {
                            last = cur;
                        }
                    }

                    if (DEBUG) {
                        for (var rItem : tempResultItems) {
                            Log.d(TAG, " after: " + item.lineNum + "行: " + rItem.range.start + ", " + rItem.range.end);
                        }
                    }
                }

                item.items = tempResultItems.toArray(new ResultItem[0]);
                mRetList.add(item);
            }

            i++;
        }

        if (getIsStarted()) {
            mRetList.clear();
        }

        setIsStarted(false);
        if (DEBUG) {
            Log.d(TAG, "======= <<<<<<< find end");
            Log.d(TAG, "======= <<<<<<< <<<<<");
        }
        return mRetList;
    }

    private abstract static class IWrap {
        final SearchParams originParam;

        IWrap (SearchParams origin) {
            originParam = origin;
        }
    }

    private static final class SearchParamsWrapRule extends IWrap{
        final Pattern pattern;
        SearchParamsWrapRule(String regex, SearchParams originParam) {
            super(originParam);
            pattern = Pattern.compile(regex);
        }
    }

    private static final class SearchParamsWrapSimple extends IWrap {
        String[] splits;
        int startCharDefine;
        int endCharDefine;
        String first;
        String last;

        SearchParamsWrapSimple(String[] splits,
                               SearchParams origin,
                               int startCharDefine, int endCharDefine,
                               String first, String last) {
            super(origin);
            this.splits = splits;
            this.startCharDefine = startCharDefine;
            this.endCharDefine = endCharDefine;
            this.first = first;
            this.last = last;
        }

        @Override
        public String toString() {
            return "SearchParamsWrap{" +
                    Arrays.toString(splits) +
                    ", first='" + first + '\'' +
                    ", last='" + last + '\'' +
                    '}';
        }
    }

    private static IWrap[] toWrap(SearchParams p) {
        if (p.type == SearchParams.Type.Normal) {
            int s, e;
            String cvtWord = p.useCaseMatch ? p.words : p.words.toLowerCase();
            s = CharDefineHelper.define(cvtWord.charAt(0));
            e = CharDefineHelper.define(cvtWord.charAt(cvtWord.length() - 1));
            return new IWrap[] {new SearchParamsWrapSimple(new String[] {cvtWord}, p, s, e, cvtWord, cvtWord)};
        } else if (p.type == SearchParams.Type.FakeRegex) {
            Log.d("fakeRegex search param convert: " + p);
            String cvtWord = p.useCaseMatch ? p.words : p.words.toLowerCase();
            String[] cvtWords = cvtWord.split("\\|");
            HashSet<IWrap> set = new HashSet<>(cvtWords.length);
            int s, e;
            for (String word : cvtWords) {
                int index = word.indexOf(".*");
                if (index < 0) {
                    s = CharDefineHelper.define(word.charAt(0));
                    e = CharDefineHelper.define(word.charAt(word.length() - 1));
                    var wrap = new SearchParamsWrapSimple(new String[]{word}, p, s, e, word, word);
                    set.add(wrap);
                } else {
                    String[] splitWords = word.split("\\.\\*");
                    String first = splitWords[0];
                    s = CharDefineHelper.define(first.charAt(0));
                    String last = splitWords[splitWords.length - 1];
                    e = CharDefineHelper.define(last.charAt(last.length() - 1));
                    var wrap = new SearchParamsWrapSimple(splitWords, p, s, e, first, last);
                    set.add(wrap);
                }
            }

            return set.toArray(new IWrap[0]);
        } else {
            Log.d("REGEX: search param convert: " + p);
            String regex = p.words;
            if (!p.useCaseMatch) {
                regex = "(?i)" + regex;
            }
            if (p.useWholeWords) {
                regex = "\\b" + regex + "\\b";
            }
            return new IWrap[] {new SearchParamsWrapRule(regex, p)};
        }
    }
}

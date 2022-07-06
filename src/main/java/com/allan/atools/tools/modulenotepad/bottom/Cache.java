package com.allan.atools.tools.modulenotepad.bottom;

import com.allan.atools.richtext.codearea.EditorAreaImpl;
import com.allan.atools.beans.ResultItemWrap;
import com.allan.atools.beans.ResultItem;
import com.allan.atools.text.beans.OneFileSearchResults;
import com.allan.atools.tools.modulenotepad.Highlight;

import java.util.List;

final class Cache {
    OneFileSearchResults cacheResult;

    /**
     * 通过二分法快速的定位到当前行，在items里面的index
     */
    private int fastLocationIndex(List<ResultItemWrap> items, int caretLineNum) {
        int low = 0, high = items.size() - 1;
        int mid, lastmid = -99; //随便放一个负数

        int n;
        while (true) {
            mid = (low + high) / 2;
            if (lastmid == mid) {
                break;
            }
            lastmid = mid;

            n =  items.get(mid).lineNum;
            if (caretLineNum > n) {
                low = mid;
            } else if (caretLineNum < n) {
                high = mid;
            } else {
                low = high = mid; //命中了当前行，则直接返回相同的index即可
                break;
            }
        }

        //通过上述二分操作，最后将index锁定在了相邻的2个index。或者相同的
        if (low == high) {
            return low;
        }

        if (items.get(low).lineNum == caretLineNum) {
            return low;
        } else {
            return high;
        }
    }

    static final class Out {
        public int lineNum = 0;
        public int totalResultSize = 0;
        public int resultIndex = -1;
    }

    public ResultItem getNextCachedLineNum(EditorAreaImpl area, boolean back, boolean cycleNext, Out out) {
        var resultItem = getNextCachedLineNum(area, back, cycleNext);
        if (resultItem == null) {
            return null;
        }
        int total = 0;
        var oneRes = cacheResult;
        for (var lineItem : oneRes.results) {
            if (lineItem.items != null) {
                for (var ri : lineItem.items) {
                    total++;
                    if (ri == resultItem) {
                        out.lineNum = lineItem.lineNum;
                        out.resultIndex = total;
                    }
                }
            }
        }
        out.totalResultSize = total;
        return resultItem;
    }

    private ResultItem getNextCachedLineNum(EditorAreaImpl area, boolean back, boolean cycleNext) {
        var oneRes = cacheResult;
        if (oneRes == null || oneRes.results == null || oneRes.results.size() == 0) {
            return null;
        }

        var posAndLineNum = Highlight.getCurrentCaretPosAndLineNum(area);
        int caretPosition = posAndLineNum[0];
        int caretLine = posAndLineNum[1];

        var curLineItemIndex = fastLocationIndex(oneRes.results, caretLine);
        int nextLineIndex = curLineItemIndex;
        {
            //首先我们从当前行，按照back或者forward方向找到next one。
            var curItem = oneRes.results.get(curLineItemIndex);

            if (back) {
                //当前行，找到了比现在的小的；就跳出
                if (curItem.items != null) {
                    for (int i = curItem.items.length - 1; i >= 0; i--) {
                        var sitem = curItem.items[i];
                        if (sitem.range.totalOffset + sitem.range.end - sitem.range.start < caretPosition) {
                            return sitem;
                        }
                    }
                }
                //没找到，我们就把index-1
                if(curLineItemIndex > 0) nextLineIndex = curLineItemIndex - 1;
            } else {
                //当前行，找到了比现在的大的；就跳出
                if (curItem.items != null) {
                    for (int i = 0, c = curItem.items.length; i < c; i++) {
                        var sitem = curItem.items[i];
                        if (sitem.range.totalOffset > caretPosition) {
                            return sitem;
                        }
                    }
                }
                //没有比现在更大的，则追加一个
                if (curLineItemIndex < oneRes.results.size() - 1) {
                   nextLineIndex = curLineItemIndex + 1;
                }
            }
        }

        if (cycleNext) {
            if (curLineItemIndex == 0 && nextLineIndex == 0 && back) { //同时为0，又没有在上面的该行中找到则跳到最后咯。
                nextLineIndex = oneRes.results.size() - 1;
            }

            if (curLineItemIndex == oneRes.results.size() - 1 && nextLineIndex == oneRes.results.size() - 1 && !back) { //都是最后一个匹配行，又走到这里就跳到最开始
                nextLineIndex = 0;
            }
        }

        //不相同，我们就不用判断了，只需要往上，往下，不过要注意往下是直接拿本尊；往上是拿最后一个second，拿不到再本尊
        if (curLineItemIndex != nextLineIndex) {
            if (back) {
                var item = oneRes.results.get(nextLineIndex);
                return item.items[item.items.length - 1];
            } else {
                var item = oneRes.results.get(nextLineIndex);
                return item.items[0];
            }
        }
        return null;
    }
}

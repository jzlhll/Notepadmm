package com.allan.atools.text.beans;

import com.allan.atools.beans.ResultItemWrap;
import com.allan.atools.utils.Locales;
import com.allan.atools.text.EditorBaseResultItemPair;
import javafx.scene.paint.Color;

import java.util.*;

/**
 * 指代Editor的泛型。=
 */
public class AllFilesSearchResults {
    public List<OneFileSearchResults> allResults;
    public String mask;

    public AllFilesSearchResults addAllResults(List<OneFileSearchResults> allResults) {
        this.allResults = allResults;
        return this;
    }

    public AllFilesSearchResults addMask(String mask) {
        this.mask = mask;
        return this;
    }

    public StringBuilder toMappingResults(int[] fileLineIndexes) {
        //这个其实就是searchPaneNumber显示第0行
        StringBuilder sb = new StringBuilder();
        int i = 0;
        int totalLines = 0;
        var hitFmt = Locales.str("result.hitTimes");
        for (OneFileSearchResults oneFileResults : allResults) {
            fileLineIndexes[i++] = totalLines;
            var head = new ResultItemWrap();
            head.lineMode = ResultItemWrap.LineMode.FilePath;
            var line = "  " + oneFileResults.file.getName()
                    + "  (" +  String.format(hitFmt, oneFileResults.results.size()) + ")";
            head.setLine(line);
            oneFileResults.results.add(0, head);

            for (ResultItemWrap item : oneFileResults.results) {
                //item.searchPaneNumber = line++;
                sb.append(item.getLine()).append(System.lineSeparator());
            }
            totalLines += 1 + oneFileResults.results.size();
        }

        return sb;
    }

    public static Color[] combineColorStrsToColors(String combine) {
        String[] ss = combine.split(";");
        return new Color[] {Color.valueOf(ss[0]), Color.valueOf(ss[1])};
    }

    @Override
    public String toString() {
        return "AllFilesSearchResults{" +
                "results=" + allResults;
    }

    public EditorBaseResultItemPair getByLineNum(int lineNum, int colIndex) {
        if (allResults.size() > 0) {
            var oneResults = allResults.get(0); //todo 多文件没有处理。这里直接拿到第一个来处理的

            ResultItemWrap resultItemWrap = oneResults.results.get(lineNum);
            colIndex = colIndex - resultItemWrap.resultOffset;

            //计算得到item所在的index
            int secondIndex = 0;
            if (resultItemWrap.items != null && resultItemWrap.items.length > 0) {
                for (var item : resultItemWrap.items) {
                    if (item.range.end > colIndex) {
                        break;
                    }
                    secondIndex++;
                }
                if (secondIndex == resultItemWrap.items.length) {
                    secondIndex--;
                }
            }

            return new EditorBaseResultItemPair(oneResults.area, resultItemWrap, secondIndex);
        }
        throw new RuntimeException("不可能找不到 OneFileSearchResults");
    }
}

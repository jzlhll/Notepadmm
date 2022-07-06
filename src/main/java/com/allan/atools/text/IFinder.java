package com.allan.atools.text;

import com.allan.atools.beans.ResultItemWrap;

import java.util.List;

public interface IFinder {
    List<ResultItemWrap> find();
    void cancel();
}

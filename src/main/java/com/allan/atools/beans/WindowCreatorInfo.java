package com.allan.atools.beans;

public class WindowCreatorInfo {
    public String title;
    public double width;
    public double height;
    public String iconPath;
    public boolean resizable;
    public boolean alwaysTop;
    public boolean isSystemWindow = true;
    /**
     * 如果没有此参数表明不做信息保存
     */
    public String sizeAndLocateCachePrefixName;
}

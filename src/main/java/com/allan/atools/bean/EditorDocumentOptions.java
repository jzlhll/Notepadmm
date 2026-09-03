package com.allan.atools.bean;

/** 单个编辑文档的底部状态配置。 */
public record EditorDocumentOptions(String file, String sessionId, boolean wrap,
                                    boolean readonly, boolean chinesePunctuation) {
}

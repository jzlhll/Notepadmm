package com.allan.atools.bean

/**
 * 用于测试 Gson 序列化压缩的数据载体，字段 mediaIds 承载原始 long 数组。
 */
@Suppress("ArrayInDataClass")
data class MediaIdsData(val mediaIds: LongArray)

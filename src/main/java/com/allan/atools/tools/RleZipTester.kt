package com.allan.atools.tools

import com.allan.atools.bean.MediaIdsData
import com.google.gson.Gson
import java.io.ByteArrayOutputStream
import java.util.Random
import java.util.zip.GZIPOutputStream

/**
 * 独立测试类：演示「Gson 序列化 -> 游程编码 -> zip 压缩」三层数据压缩。
 *
 * 第一步：把原始 long 数组包装成 MediaIdsData(mediaIds)，用 Gson 序列化为 json string；
 * 第二步：对原始数组做游程编码，转成连续区间字符串（如 1-3,6-7,9-11,13,15-16）；
 * 第三步：用 GZIP(deflate) 再次压缩第二步的字符串，对比各步产物的长度。
 * 每一步同时打印前 100 个字符（第三步为二进制，以十六进制展示前 100 字符）。
 * 结果通过标准输出打印，避免被 UIContext.DEBUG 开关屏蔽。
 */
object RleZipTester {

    private val GSON = Gson()

    /** 第一组：1..100000 全量连续数组。 */
    @JvmStatic
    fun runGroup1() = runGroup("第一组(1..100000 全量连续)", buildGroup1())

    /** 第二组：1..100000 随机丢约1%。 */
    @JvmStatic
    fun runGroup2() = runGroup("第二组(1..100000 随机丢约1%)", buildGroup2())

    /** 第三组：1..100000 隔一个丢一个。 */
    @JvmStatic
    fun runGroup3() = runGroup("第三组(1..100000 隔一个丢一个)", buildGroup3())

    /** 第四组：1..100000 随机丢约5%。 */
    @JvmStatic
    fun runGroup4() = runGroup("第四组(1..100000 随机丢约5%)", buildRandomDrop(5, 4242L))

    /** 第五组：1..100000 随机丢约10%。 */
    @JvmStatic
    fun runGroup5() = runGroup("第五组(1..100000 随机丢约10%)", buildRandomDrop(10, 424242L))

    private fun runGroup(name: String, data: LongArray) {
        // 各步耗时用 System.nanoTime 计量，差值换算为毫秒
        val t0 = System.nanoTime()
        val gsonStr = GSON.toJson(MediaIdsData(data))
        val gsonCost = ms(System.nanoTime() - t0)

        val t1 = System.nanoTime()
        val rle = encodeRle(data)
        val rleCost = ms(System.nanoTime() - t1)

        val t2 = System.nanoTime()
        val zipped = gzipBytes(rle)
        val zipCost = ms(System.nanoTime() - t2)

        // 游程编码还原：把 "1-3,6-7,9-11,13,15-16" 还原回升序数组
        val t3 = System.nanoTime()
        val decoded = decodeRle(rle)
        val decodeCost = ms(System.nanoTime() - t3)

        // 网络传输按 UTF-8 字节计算，1 KB = 1024 字节
        val gsonBytes = gsonStr.toByteArray(Charsets.UTF_8)
        val rleBytes = rle.toByteArray(Charsets.UTF_8)
        val sb = StringBuilder()
            .append(name).append('\n')
            .append("  原始数组长度             : ").append(data.size).append('\n')
            .append("  第一步 gson string 长度  : ").append(gsonStr.length)
            .append("  字节: ").append(gsonBytes.size)
            .append("  (").append(kb(gsonBytes.size)).append(")")
            .append("  耗时: ").append(gsonCost).append(" ms").append('\n')
            .append("    前500字符: ").append(prefix(gsonStr, 500)).append('\n')
            .append("  第二步 游程编码 string 长度: ").append(rle.length)
            .append("  字节: ").append(rleBytes.size)
            .append("  (").append(kb(rleBytes.size)).append(")")
            .append("  耗时: ").append(rleCost).append(" ms").append('\n')
            .append("    前500字符: ").append(prefix(rle, 500)).append('\n')
            .append("  游程编码还原后数组长度   : ").append(decoded.size)
            .append("  耗时: ").append(decodeCost).append(" ms").append('\n')
            .append("  第三步 gzip 压缩后长度    : ").append(zipped.size)
            .append("  字节: ").append(zipped.size)
            .append("  (").append(kb(zipped.size)).append(")")
            .append("  耗时: ").append(zipCost).append(" ms").append('\n')
            .append("    前500字符(十六进制): ").append(hexPrefix(zipped, 500))
        // 直接走标准输出，避免被 UIContext.DEBUG 开关屏蔽
        println("[$name]\n$sb")
    }

    /** 纳秒差值换算为毫秒字符串，保留 3 位小数。 */
    private fun ms(nanos: Long): String =
        String.format("%.3f", nanos / 1_000_000.0)

    /** 字节数换算为 KB，保留 1 位小数；不足 1 KB 显示为 <1KB。 */
    private fun kb(bytes: Int): String =
        if (bytes < 1024) "<1KB" else String.format("%.1f", bytes / 1024.0) + "KB"

    /** 取字符串前 n 个字符。 */
    private fun prefix(s: String, n: Int): String =
        if (s.length <= n) s else s.substring(0, n)

    /**
     * 第二层：游程编码。遍历升序数组，把连续段写作 a-b，孤立点写作 a，
     * 用逗号拼接。例如 [1,2,3,6,7,9,10,11,13,15,16] -> "1-3,6-7,9-11,13,15-16"。
     * 优化：预估容量一次性分配，避免 StringBuilder 多次扩容。
     */
    private fun encodeRle(data: LongArray): String {
        // 稀疏情况下每个数字平均约 6 字符 + 分隔符，预分配避免多次扩容
        val sb = StringBuilder(data.size * 7)
        var i = 0
        while (i < data.size) {
            var j = i
            while (j + 1 < data.size && data[j + 1] == data[j] + 1L) j++
            if (sb.isNotEmpty()) sb.append(',')
            if (j > i) {
                sb.append(data[i]).append('-').append(data[j])
            } else {
                sb.append(data[i])
            }
            i = j + 1
        }
        return sb.toString()
    }

    /**
     * 游程编码还原：把 "1-3,6-7,9-11,13,15-16" 还原回升序 long 数组。
     * 优化：两遍扫描，第一遍统计总长度后一次性分配 LongArray，避免装箱与扩容；
     * 数字解析手写，避免子串创建与 toLong() 开销。仅支持非负整数。
     */
    private fun decodeRle(rle: String): LongArray {
        if (rle.isEmpty()) return LongArray(0)
        val n = rle.length
        // 第一遍：统计还原后元素总数
        var count = 0
        var i = 0
        while (i < n) {
            var segEnd = i
            while (segEnd < n && rle[segEnd] != ',') segEnd++
            val dash = indexOfDash(rle, i, segEnd)
            if (dash > 0) {
                val from = parseLong(rle, i, dash)
                val to = parseLong(rle, dash + 1, segEnd)
                count += (to - from + 1L).toInt()
            } else {
                count++
            }
            i = segEnd + 1
        }
        // 第二遍：填充预分配数组
        val result = LongArray(count)
        var idx = 0
        i = 0
        while (i < n) {
            var segEnd = i
            while (segEnd < n && rle[segEnd] != ',') segEnd++
            val dash = indexOfDash(rle, i, segEnd)
            if (dash > 0) {
                val from = parseLong(rle, i, dash)
                val to = parseLong(rle, dash + 1, segEnd)
                var v = from
                while (v <= to) {
                    result[idx++] = v
                    v++
                }
            } else {
                result[idx++] = parseLong(rle, i, segEnd)
            }
            i = segEnd + 1
        }
        return result
    }

    /** 在 [start, end) 区间内查找 '-'，找不到返回 -1。 */
    private fun indexOfDash(s: String, start: Int, end: Int): Int {
        var k = start
        while (k < end) {
            if (s[k] == '-') return k
            k++
        }
        return -1
    }

    /** 手写数字解析，解析 s[start, end) 为 long，仅支持非负整数。 */
    private fun parseLong(s: String, start: Int, end: Int): Long {
        var v = 0L
        var k = start
        while (k < end) {
            v = v * 10L + (s[k].code - '0'.code)
            k++
        }
        return v
    }

    /** 第三层：使用 GZIP(deflate) 压缩字符串的 UTF-8 字节，返回压缩后字节数组。 */
    private fun gzipBytes(text: String): ByteArray {
        val bos = ByteArrayOutputStream()
        GZIPOutputStream(bos).use { it.write(text.toByteArray(Charsets.UTF_8)) }
        return bos.toByteArray()
    }

    /** 把字节数组转十六进制字符串并取前 n 个字符，用于展示二进制内容。 */
    private fun hexPrefix(bytes: ByteArray, n: Int): String {
        val sb = StringBuilder()
        for (b in bytes) {
            sb.append(String.format("%02x", b))
            if (sb.length >= n) break
        }
        return if (sb.length <= n) sb.toString() else sb.substring(0, n)
    }

    /**
     * 第一组测试数据：1..100000 全量连续数组。
     * 设计目的：建立无损压缩基线——游程编码后应得到单段 "1-100000"，
     * 用于验证连续数据下各步压缩各自的极限长度。
     */
    private fun buildGroup1(): LongArray {
        val arr = LongArray(100000)
        for (v in 1..100000) arr[v - 1] = v.toLong()
        return arr
    }

    /**
     * 随机丢弃测试数据：1..100000 中每个数以 dropPercent% 概率丢弃。
     * 设计目的：制造随机间断，模拟真实稀疏分布，考察压缩率随间断密度的变化。
     * 固定随机种子 seed 保证结果可复现。第二/四/五组分别对应 1%/5%/10%。
     */
    private fun buildRandomDrop(dropPercent: Int, seed: Long): LongArray {
        val list = ArrayList<Long>(100000 - 100000 * dropPercent / 100)
        val rnd = Random(seed)
        for (v in 1..100000) {
            if (rnd.nextInt(100) >= dropPercent) list.add(v.toLong())
        }
        return list.toLongArray()
    }

    /** 第二组：随机丢约1%，种子 42。 */
    private fun buildGroup2(): LongArray = buildRandomDrop(1, 42L)

    /**
     * 第三组测试数据：1..100000 隔一个丢一个（仅保留奇数 1,3,5,...,99999）。
     * 设计目的：制造均匀间断，全部为孤立点，对游程编码最不利，
     * 用于对比极端不连续场景下 gzip 的兜底压缩能力。
     */
    private fun buildGroup3(): LongArray {
        val arr = LongArray(50000)
        var idx = 0
        for (v in 1..100000) {
            if (v % 2 == 1) arr[idx++] = v.toLong()
        }
        return arr
    }
}

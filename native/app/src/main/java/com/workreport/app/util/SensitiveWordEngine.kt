package com.workreport.app.util

import com.workreport.app.domain.model.SensitiveWord

/**
 * 脱敏命中所在的内容区。
 *
 * 「本周已完成工作梳理」和「下周工作计划」是两个独立区，锚点带区号才不会互相串位。
 */
enum class MaskSection { DONE, PLAN }

/**
 * 一次脱敏命中的**稳定标识**。
 *
 * 关键设计：标识不再只是「词」，而是「词 + 它所在的前后文」。
 * 于是同一句话里出现两次「查询」时，两处是**两个不同的标识**，
 * 用户可以只放行其中一处，另一处照常脱敏。
 *
 * 稳定性说明：只要这一处的前后文和词没变，用户在别处增删内容都不会让豁免串位；
 * 只有真的改动了这一处周围，前后文变了、豁免自动失效（宁可失效，也不要错放行）。
 */
data class MaskAnchor(
    /** 所属内容区 */
    val section: MaskSection,
    /** 命中词之前的原文（截断后的上下文） */
    val before: String,
    /** 命中的敏感词 */
    val word: String,
    /** 命中词之后的原文（截断后的上下文） */
    val after: String,
    /** 同一组上下文里的第几个（从 0 开始），用于区分上下文完全相同的两处 */
    val nth: Int,
)

/**
 * 一次脱敏命中（按「处」记录，不按词聚合）。
 */
data class MaskHit(
    /** 稳定标识，豁免状态就挂在它身上 */
    val anchor: MaskAnchor,
    /** 被替换掉的原文词 */
    val word: String,
    /** 替换成的内容 */
    val replacement: String,
    /** 本次是否放行（true = 保持原文不替换） */
    val excluded: Boolean,
    /** 位于第几行（0 起） */
    val lineIndex: Int,
    /** 在该行原文中的起始位置 */
    val position: Int,
)

/**
 * 一次脱敏的命中汇总。
 */
data class MaskSummary(
    val hits: List<MaskHit> = emptyList(),
) {
    /** 本次一共命中几处 */
    val totalCount: Int get() = hits.size

    /** 其中真正被替换了几处 */
    val maskedCount: Int get() = hits.count { !it.excluded }

    /** 其中被放行、保持原文的有几处 */
    val excludedCount: Int get() = hits.count { it.excluded }

    val isEmpty: Boolean get() = hits.isEmpty()
}

/**
 * 敏感词替换引擎。
 *
 * 规则与 WebView 旧版完全一致：按词库顺序，把敏感词替换成它的小写拼音首字母。
 * 例如「纪委」→「jw」、「一体化」→「yth」。
 *
 * v1.4.0 起支持「按处豁免」：同一句里出现多次同一个词时，用户可以只放行其中一处。
 * 豁免只影响本次生成，不会改动词库。
 *
 * ⚠️ 兼容性红线：没有任何豁免时，输出与旧版**逐字节一致**，导出内容不受影响。
 */
class SensitiveWordEngine(words: List<SensitiveWord>) {

    /** 词库快照，构造后不再变化；需要更新请新建实例 */
    private val rules: List<Pair<String, String>> =
        words.filter { it.word.isNotBlank() }.map { it.word to it.pinyin }

    /**
     * 对文本做脱敏。
     *
     * 逐词 `replace`，等价于旧版的 `text.split(key).join(value)`。
     */
    fun replace(text: String): String =
        maskLine(text, MaskSection.DONE, emptySet(), 0, HashMap()).text

    /**
     * 对文本做脱敏，但跳过 [excluded] 命中的那些**处**。
     *
     * [excluded] 是「本次放行」的锚点集合：命中的那几处保持原文，其余照常替换。
     */
    fun replace(text: String, excluded: Set<MaskAnchor>): String =
        maskLine(text, MaskSection.DONE, excluded, 0, HashMap()).text

    /**
     * 统计这次会替换哪些词、各几处。
     *
     * 走的是与 [replace] 完全相同的链路（同一份 [maskLine]），
     * 所以统计结果和实际导出内容永远对得上，不会出现「预览说有、导出没有」。
     */
    fun analyze(text: String, excluded: Set<MaskAnchor> = emptySet()): MaskSummary =
        MaskSummary(maskLine(text, MaskSection.DONE, excluded, 0, HashMap()).hits)

    /**
     * 统计多行内容的脱敏命中。
     *
     * 与 [formatDoneWorks] 一样**逐行**统计，口径完全一致
     * （因为脱敏本来就是逐行做的，格式化时加入的序号、标点不参与）。
     */
    fun analyzeMultiline(
        text: String,
        excluded: Set<MaskAnchor> = emptySet(),
        section: MaskSection = MaskSection.DONE,
    ): MaskSummary {
        val lines = text.split('\n').filter { it.isNotBlank() }
        if (lines.isEmpty()) return MaskSummary()
        val counter = HashMap<String, Int>()
        val hits = ArrayList<MaskHit>()
        lines.forEachIndexed { index, line ->
            hits.addAll(maskLine(line.trim(), section, excluded, index, counter).hits)
        }
        return MaskSummary(hits)
    }

    /**
     * 核心替换链路：对一行文本做脱敏，同时给出每一处命中。
     *
     * 这里是唯一的事实来源：[replace] / [analyze] / [analyzeMultiline] /
     * [formatDoneWorks] / [formatPlanWorks] 全都调它，
     * 保证预览与导出永远一致。
     *
     * 实现要点：
     *  1. 逐词 `indexOf` 扫描，语义与 `String.replace` 完全一致（非重叠、从左到右）；
     *  2. 同时维护「工作文本 → 原文」的字符映射，所以每一处命中都能追回它在原文里的位置；
     *  3. 被放行的命中会把它覆盖的原文区间标成保护区，后续规则不再动这块，
     *     这样「放行 纪委」的结果就是原样的「纪委」，不会被后续规则拆开。
     */
    private fun maskLine(
        source: String,
        section: MaskSection,
        excluded: Set<MaskAnchor>,
        lineIndex: Int,
        counter: MutableMap<String, Int>,
    ): MaskLineResult {
        if (source.isEmpty() || rules.isEmpty()) return MaskLineResult(source, emptyList())

        var working = source
        // 工作文本里每个字符对应的原文区间
        var originStart = IntArray(source.length) { it }
        var originEnd = IntArray(source.length) { it + 1 }

        val protectedRanges = ArrayList<IntRange>()
        val hits = ArrayList<MaskHit>()

        for ((word, pinyin) in rules) {
            if (word.isEmpty() || !working.contains(word)) continue
            val sb = StringBuilder()
            val nextStart = ArrayList<Int>()
            val nextEnd = ArrayList<Int>()
            var i = 0
            while (i < working.length) {
                val idx = working.indexOf(word, i)
                if (idx < 0) {
                    for (k in i until working.length) {
                        sb.append(working[k]); nextStart.add(originStart[k]); nextEnd.add(originEnd[k])
                    }
                    break
                }
                for (k in i until idx) {
                    sb.append(working[k]); nextStart.add(originStart[k]); nextEnd.add(originEnd[k])
                }
                val srcStart = originStart[idx]
                val srcEnd = originEnd[idx + word.length - 1]

                // 落在保护区里（前面某处被放行）→ 原样保留，不参与替换
                if (protectedRanges.any { srcStart < it.last + 1 && it.first < srcEnd }) {
                    for (k in idx until idx + word.length) {
                        sb.append(working[k]); nextStart.add(originStart[k]); nextEnd.add(originEnd[k])
                    }
                    i = idx + word.length
                    continue
                }

                val before = contextBefore(source, srcStart)
                val after = contextAfter(source, srcEnd)
                val triple = before + "\u0001" + word + "\u0001" + after
                val nth = counter.getOrDefault(triple, 0)
                counter[triple] = nth + 1
                val anchor = MaskAnchor(section, before, word, after, nth)
                val isExcluded = excluded.contains(anchor)

                hits.add(
                    MaskHit(
                        anchor = anchor,
                        word = word,
                        replacement = pinyin,
                        excluded = isExcluded,
                        lineIndex = lineIndex,
                        position = srcStart,
                    ),
                )

                if (isExcluded) {
                    // 放行：原样保留，并把这段原文划为保护区
                    protectedRanges.add(srcStart until srcEnd)
                    for (k in idx until idx + word.length) {
                        sb.append(working[k]); nextStart.add(originStart[k]); nextEnd.add(originEnd[k])
                    }
                } else {
                    for (k in pinyin.indices) {
                        sb.append(pinyin[k]); nextStart.add(srcStart); nextEnd.add(srcEnd)
                    }
                }
                i = idx + word.length
            }
            working = sb.toString()
            originStart = nextStart.toIntArray()
            originEnd = nextEnd.toIntArray()
        }

        return MaskLineResult(working, hits.sortedBy { it.position })
    }

    private fun contextBefore(source: String, start: Int): String {
        val from = maxOf(0, start - CONTEXT_WINDOW)
        val prefix = if (from > 0) "…" else ""
        return prefix + source.substring(from, start)
    }

    private fun contextAfter(source: String, end: Int): String {
        val to = minOf(source.length, end + CONTEXT_WINDOW)
        val suffix = if (to < source.length) "…" else ""
        return source.substring(end, to) + suffix
    }

    /** 一行文本脱敏的结果 */
    private data class MaskLineResult(
        val text: String,
        val hits: List<MaskHit>,
    )

    companion object {
        /** 上下文窗口大小：前后各取这么多字用于识别与展示 */
        private const val CONTEXT_WINDOW = 8

        /** 格式化「本周已完成工作梳理」，与旧版 formatDoneWorks() 逐字一致 */
        fun formatDoneWorks(
            text: String,
            engine: SensitiveWordEngine,
            excluded: Set<MaskAnchor> = emptySet(),
        ): String {
            val lines = text.split('\n').filter { it.isNotBlank() }
            if (lines.isEmpty()) return ""
            val sb = StringBuilder()
            sb.append("完成共").append(lines.size).append("项工作内容：\n")
            val counter = HashMap<String, Int>()
            lines.forEachIndexed { index, line ->
                val endChar = if (index == lines.size - 1) "。" else "；"
                sb.append(index + 1).append("、")
                    .append(engine.maskLine(line.trim(), MaskSection.DONE, excluded, index, counter).text)
                    .append(endChar).append('\n')
            }
            return sb.toString().trim()
        }

        /** 格式化「下周工作计划」，与旧版 formatPlanWorks() 逐字一致 */
        fun formatPlanWorks(
            text: String,
            engine: SensitiveWordEngine,
            excluded: Set<MaskAnchor> = emptySet(),
        ): String {
            val lines = text.split('\n').filter { it.isNotBlank() }
            if (lines.isEmpty()) return ""
            val sb = StringBuilder()
            sb.append("下周预计共").append(lines.size).append("项工作任务：\n")
            val counter = HashMap<String, Int>()
            lines.forEachIndexed { index, line ->
                val endChar = if (index == lines.size - 1) "。" else "；"
                sb.append(index + 1).append("、")
                    .append(engine.maskLine(line.trim(), MaskSection.PLAN, excluded, index, counter).text)
                    .append(endChar).append('\n')
            }
            return sb.toString().trim()
        }
    }
}

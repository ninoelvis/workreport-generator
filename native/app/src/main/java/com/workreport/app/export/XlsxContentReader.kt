package com.workreport.app.export

import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory
import org.xml.sax.InputSource

/**
 * 从 xlsx 里读出「这份周报写了什么」。
 *
 * 只做预览需要的最小解析 —— 取 A1（标题）、A3（本周工作时间）、
 * B3（本周已完成工作梳理）、C3（下周工作计划）四个单元格。
 *
 * 为什么不写通用 xlsx 解析器：这份表是本 App 自己生成的，
 * 结构固定为 4 行 6 列，且不用共享字符串表（单元格是 `t="str"`，
 * 文字直接放在 `<v>` 里）。定向解析又快又不会出错。
 *
 * 但仍然兼容另外两种常见写法，免得文件来源变了就读不出来：
 * - `t="s"`    → 共享字符串索引，要查 `xl/sharedStrings.xml`
 * - `t="inlineStr"` → 文字在 `<is><t>` 里
 */
object XlsxContentReader {

    /** 解析出来的周报内容 */
    data class Content(
        val title: String = "",
        val timeRange: String = "",
        val doneWorks: String = "",
        val planWorks: String = "",
    ) {
        val isEmpty: Boolean
            get() = title.isEmpty() && timeRange.isEmpty() && doneWorks.isEmpty() && planWorks.isEmpty()
    }

    /** xlsx 是普通 zip（没有加密），直接用 JDK 的 ZipInputStream 解 */
    private const val SHEET_PATH = "xl/worksheets/sheet1.xml"
    private const val SHARED_STRINGS_PATH = "xl/sharedStrings.xml"

    /**
     * 解析 xlsx 字节流。解析失败返回 null（调用方负责降级提示）。
     */
    fun read(bytes: ByteArray): Content? = try {
        val parts = readParts(bytes)
        val shared = parts[SHARED_STRINGS_PATH]?.let { parseSharedStrings(it) } ?: emptyList()
        val sheet = parts[SHEET_PATH] ?: return null
        val cells = parseCells(sheet, shared)
        Content(
            title = cells["A1"].orEmpty(),
            timeRange = cells["A3"].orEmpty(),
            doneWorks = cells["B3"].orEmpty(),
            planWorks = cells["C3"].orEmpty(),
        )
    } catch (e: Exception) {
        null
    }

    /** 把 xlsx 拆成「路径 → XML 内容」 */
    private fun readParts(bytes: ByteArray): Map<String, ByteArray> {
        val out = HashMap<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zis ->
            while (true) {
                val entry = zis.nextEntry ?: break
                if (!entry.isDirectory) {
                    out[entry.name] = zis.readBytes()
                }
                zis.closeEntry()
            }
        }
        return out
    }

    /** 解析工作表，得到「单元格引用 → 文字」 */
    private fun parseCells(sheet: ByteArray, shared: List<String>): Map<String, String> {
        val cells = HashMap<String, String>()
        val doc = parseXml(sheet)
        val nodes = doc.getElementsByTagName("c")
        for (i in 0 until nodes.length) {
            val c = nodes.item(i) as? Element ?: continue
            val ref = c.getAttribute("r")
            if (ref.isEmpty()) continue
            val type = c.getAttribute("t")
            cells[ref] = when (type) {
                // 共享字符串：<v> 是 sharedStrings.xml 的下标</v>
                "s" -> {
                    val idx = textOf(c, "v").trim().toIntOrNull()
                    if (idx != null && idx in shared.indices) shared[idx] else ""
                }
                // 内联字符串：<is><t>文字</t></is>
                "inlineStr" -> {
                    val ts = c.getElementsByTagName("t")
                    if (ts.length > 0) ts.item(0).textContent ?: "" else ""
                }
                // t="str"（本 App 用的）和默认：文字直接在 <v> 里
                else -> textOf(c, "v")
            }
        }
        return cells
    }

    /** 解析 sharedStrings.xml，返回字符串数组 */
    private fun parseSharedStrings(xml: ByteArray): List<String> {
        val doc = parseXml(xml)
        val nodes = doc.getElementsByTagName("si")
        val out = ArrayList<String>(nodes.length)
        for (i in 0 until nodes.length) {
            val si = nodes.item(i) as? Element ?: continue
            // 一个 si 可能被拆成多个 <t>（富文本），要拼起来
            val ts = si.getElementsByTagName("t")
            val sb = StringBuilder()
            for (j in 0 until ts.length) {
                sb.append(ts.item(j).textContent ?: "")
            }
            out.add(sb.toString())
        }
        return out
    }

    /** 取某个标签下的文字 */
    private fun textOf(parent: Element, tag: String): String {
        val list = parent.getElementsByTagName(tag)
        if (list.length == 0) return ""
        return list.item(0).textContent ?: ""
    }

    /** 安全地解析 XML（禁用外部实体，防 XXE） */
    private fun parseXml(bytes: ByteArray): org.w3c.dom.Document {
        val factory = DocumentBuilderFactory.newInstance()
        factory.isNamespaceAware = false
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false)
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false)
        factory.isExpandEntityReferences = false
        val builder = factory.newDocumentBuilder()
        return builder.parse(InputSource(ByteArrayInputStream(bytes)))
    }
}

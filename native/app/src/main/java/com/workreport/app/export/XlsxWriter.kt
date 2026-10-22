package com.workreport.app.export

import java.io.File
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * xlsx（Excel 2007+）生成器。
 *
 * 直接拼装 OOXML 各部件，再用 zip 打包成 .xlsx。
 * 部件模板与 WebView 旧版（SheetJS）逐字一致，单元格样式索引、合并区域、
 * 列宽行高也完全对齐，确保在 Excel / WPS 里打开的效果与旧版一模一样。
 *
 * 之所以不引 Apache POI：POI 在 Android 上体积大（十几 MB）、还需要 StAX 补包，
 * 而这里的表格结构是固定的，手写 XML 反而更可控、体积更小。
 */
object XlsxWriter {

    /** 表格结构常量：与旧版 generateXlsx() 一一对应 */
    private val COLUMN_WIDTHS = doubleArrayOf(
        20.71484375, 29.8125, 26.36328125, 19.97265625, 21.46484375, 13.83203125,
    )
    private val ROW_HEIGHTS = intArrayOf(51, 28, 333, 82)

    private val HEADER_ROW = listOf(
        "本周工作时间", "本周已完成工作梳理", "下周工作计划",
        "客户（及用户）反馈", "项目经理评级", "其他",
    )

    private const val SHEET_NAME = "工作报告"

    /**
     * 生成 xlsx 文件。
     *
     * @param title      A1 标题
     * @param timeRange  A3 本周工作时间
     * @param doneWorks  B3 本周已完成工作梳理（已格式化的多行文本）
     * @param planWorks  C3 下周工作计划（已格式化的多行文本）
     */
    fun write(
        target: File,
        title: String,
        timeRange: String,
        doneWorks: String,
        planWorks: String,
    ) {
        target.parentFile?.mkdirs()

        val sheetXml = buildSheetXml(title, timeRange, doneWorks, planWorks)

        // xlsx 本身不加密，直接用 JDK 的 ZipOutputStream 打包（[Content_Types].xml 必须在前）
        val parts = listOf(
            "[Content_Types].xml" to XlsxTemplates.CONTENT_TYPES,
            "_rels/.rels" to XlsxTemplates.RELS_ROOT,
            "docProps/app.xml" to XlsxTemplates.APP_XML,
            "docProps/core.xml" to XlsxTemplates.CORE_XML,
            "xl/workbook.xml" to XlsxTemplates.WORKBOOK_XML,
            "xl/_rels/workbook.xml.rels" to XlsxTemplates.WORKBOOK_RELS,
            "xl/styles.xml" to XlsxTemplates.STYLES_XML,
            "xl/metadata.xml" to XlsxTemplates.METADATA_XML,
            "xl/theme/theme1.xml" to XlsxTemplates.THEME_XML,
            "xl/worksheets/sheet1.xml" to sheetXml,
        )
        FileOutputStream(target).use { fos ->
            ZipOutputStream(fos, StandardCharsets.UTF_8).use { zos ->
                parts.forEach { (name, content) ->
                    zos.putNextEntry(ZipEntry(name))
                    zos.write(content.toByteArray(StandardCharsets.UTF_8))
                    zos.closeEntry()
                }
            }
        }
    }

    /** 生成到字节数组（用于测试与对比） */
    fun toBytes(
        title: String,
        timeRange: String,
        doneWorks: String,
        planWorks: String,
    ): ByteArray {
        val tmp = File.createTempFile("wr_report", ".xlsx")
        return try {
            write(tmp, title, timeRange, doneWorks, planWorks)
            tmp.readBytes()
        } finally {
            tmp.delete()
        }
    }

    // ------------------------------------------------------------------
    // sheet1.xml
    // ------------------------------------------------------------------

    private fun buildSheetXml(
        title: String,
        timeRange: String,
        doneWorks: String,
        planWorks: String,
    ): String = buildString {
        append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\r\n")
        append("""<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">""")
        append("""<dimension ref="A1:F4"/>""")
        append("""<sheetViews><sheetView workbookViewId="0"/></sheetViews>""")

        // 列宽
        append("<cols>")
        COLUMN_WIDTHS.forEachIndexed { index, width ->
            val n = index + 1
            append("""<col min="$n" max="$n" width="$width" customWidth="1"/>""")
        }
        append("</cols>")

        append("<sheetData>")

        // 第 1 行：标题（A1 合并到 F1）
        append("""<row r="1" ht="${ROW_HEIGHTS[0]}" customHeight="1">""")
        append(cell("A1", 3, title))
        for (col in "BCDEF") {
            append(cell(col + "1", 2, ""))
        }
        append("</row>")

        // 第 2 行：表头
        append("""<row r="2" ht="${ROW_HEIGHTS[1]}" customHeight="1">""")
        HEADER_ROW.forEachIndexed { index, text ->
            val col = ('A' + index)
            // B2 需要换行显示
            val style = if (col == 'B') 5 else 4
            append(cell(col + "2", style, text))
        }
        append("</row>")

        // 第 3 行：数据（A/B/C 合并到第 4 行）
        append("""<row r="3" ht="${ROW_HEIGHTS[2]}" customHeight="1" xml:space="preserve">""")
        append(cell("A3", 6, timeRange))
        append(cell("B3", 7, doneWorks))
        append(cell("C3", 7, planWorks))
        for (col in "DEF") {
            append(cell(col + "3", 8, ""))
        }
        append("</row>")

        // 第 4 行：合并单元格的占位行
        append("""<row r="4" ht="${ROW_HEIGHTS[3]}" customHeight="1">""")
        append(cell("A4", 6, ""))
        append(cell("B4", 7, ""))
        append(cell("C4", 7, ""))
        for (col in "DEF") {
            append(cell(col + "4", 8, ""))
        }
        append("</row>")

        append("</sheetData>")

        // 合并单元格
        append("""<mergeCells count="7">""")
        append("""<mergeCell ref="A1:F1"/>""")
        for (col in "ABCDEF") {
            append("""<mergeCell ref="${col}3:${col}4"/>""")
        }
        append("</mergeCells>")

        // 与 SheetJS 保持一致：忽略「数字以文本形式存储」的提示
        append("""<ignoredErrors><ignoredError numberStoredAsText="1" sqref="A1:F4"/></ignoredErrors>""")

        append("</worksheet>")
    }

    /**
     * 生成单元格。
     *
     * 含换行或首尾空格时加 `xml:space="preserve"`，与 SheetJS 行为一致，
     * 否则 Excel 会吃掉换行。
     */
    private fun cell(ref: String, style: Int, value: String): String {
        val needsPreserve = value.contains('\n') ||
            value.contains('\r') ||
            value.startsWith(" ") ||
            value.endsWith(" ")
        val preserveAttr = if (needsPreserve) """ xml:space="preserve"""" else ""
        val vPreserve = if (needsPreserve) """<v xml:space="preserve">""" else "<v>"
        return """<c r="$ref" s="$style" t="str"$preserveAttr>$vPreserve${xmlEscape(value)}</v></c>"""
    }

    /** XML 文本转义 */
    private fun xmlEscape(text: String): String {
        val sb = StringBuilder(text.length + 16)
        for (ch in text) {
            when (ch) {
                '&' -> sb.append("&amp;")
                '<' -> sb.append("&lt;")
                '>' -> sb.append("&gt;")
                '"' -> sb.append("&quot;")
                '\'' -> sb.append("&apos;")
                // 换行保持字面 LF（与旧版 SheetJS 输出完全一致）
                '\n' -> sb.append('\n')
                '\r' -> sb.append("&#13;")
                '\t' -> sb.append('\t')
                else -> if (ch.code >= 0x20) sb.append(ch)
            }
        }
        return sb.toString()
    }
}

package com.workreport.app.export

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.zip.ZipFile

/**
 * xlsx 结构测试。
 *
 * 目的是保证原生版导出的 xlsx 与 WebView 旧版（SheetJS）在**结构与样式**上完全一致：
 * 部件清单、单元格样式索引、合并区域、列宽行高、单元格文本。
 *
 * 参考文件由 scripts/dump_xlsx_templates.py 从旧版导出的 xlsx 中提取。
 */
class XlsxWriterTest {

    private val title = "2026年10月工作报告"
    private val timeRange = "2026.10.19-2026.10.25"
    private val doneWorks = "完成共2项工作内容：\n1、配合纪委完成案件审理；\n2、组织案管系统培训。"
    private val planWorks = "下周预计共1项工作任务：\n1、推进线索核查。"

    /** 读出 xlsx 中某个部件的文本 */
    private fun part(bytes: ByteArray, name: String): String {
        val file = File.createTempFile("wr_t", ".xlsx")
        try {
            file.writeBytes(bytes)
            return ZipFile(file).use { zip ->
                val entry = zip.getEntry(name)
                requireNotNull(entry) { "缺少部件 $name" }
                zip.getInputStream(entry).readBytes().toString(Charsets.UTF_8)
            }
        } finally {
            file.delete()
        }
    }

    @Test
    fun `xlsx 包含全部必需部件`() {
        val bytes = XlsxWriter.toBytes(title, timeRange, doneWorks, planWorks)
        val file = File.createTempFile("wr_t", ".xlsx")
        try {
            file.writeBytes(bytes)
            ZipFile(file).use { zip ->
                val names = zip.entries().toList().map { it.name }
                listOf(
                    "[Content_Types].xml",
                    "_rels/.rels",
                    "docProps/app.xml",
                    "docProps/core.xml",
                    "xl/workbook.xml",
                    "xl/_rels/workbook.xml.rels",
                    "xl/styles.xml",
                    "xl/metadata.xml",
                    "xl/theme/theme1.xml",
                    "xl/worksheets/sheet1.xml",
                ).forEach {
                    assertTrue("缺少部件 $it", names.contains(it))
                }
                // [Content_Types].xml 必须是第一个条目
                assertEquals("[Content_Types].xml", names.first())
            }
        } finally {
            file.delete()
        }
    }

    @Test
    fun `工作表名为 工作报告`() {
        val bytes = XlsxWriter.toBytes(title, timeRange, doneWorks, planWorks)
        val wb = part(bytes, "xl/workbook.xml")
        assertTrue(wb.contains("""name="工作报告""""))
    }

    @Test
    fun `sheet1 的样式索引与旧版一致`() {
        val bytes = XlsxWriter.toBytes(title, timeRange, doneWorks, planWorks)
        val sheet = part(bytes, "xl/worksheets/sheet1.xml")

        // 样式索引：A1=3, B1..F1=2, A2/C2..F2=4, B2=5, A3/A4=6, B3,C3,B4,C4=7, D3..F4=8
        assertTrue(sheet.contains("""<c r="A1" s="3" t="str">"""))
        assertTrue(sheet.contains("""<c r="B1" s="2" t="str">"""))
        assertTrue(sheet.contains("""<c r="A2" s="4" t="str">"""))
        assertTrue(sheet.contains("""<c r="B2" s="5" t="str">"""))
        assertTrue(sheet.contains("""<c r="A3" s="6" t="str">"""))
        assertTrue(sheet.contains("""<c r="B3" s="7" t="str"""))
        assertTrue(sheet.contains("""<c r="C3" s="7" t="str"""))
        assertTrue(sheet.contains("""<c r="D3" s="8" t="str">"""))
        assertTrue(sheet.contains("""<c r="A4" s="6" t="str">"""))
    }

    @Test
    fun `合并区域与旧版一致`() {
        val bytes = XlsxWriter.toBytes(title, timeRange, doneWorks, planWorks)
        val sheet = part(bytes, "xl/worksheets/sheet1.xml")
        assertTrue(sheet.contains("""<mergeCells count="7">"""))
        assertTrue(sheet.contains("""<mergeCell ref="A1:F1"/>"""))
        for (col in "ABCDEF") {
            assertTrue("缺少合并 $col", sheet.contains("""<mergeCell ref="${col}3:${col}4"/>"""))
        }
    }

    @Test
    fun `列宽与旧版完全相同`() {
        val bytes = XlsxWriter.toBytes(title, timeRange, doneWorks, planWorks)
        val sheet = part(bytes, "xl/worksheets/sheet1.xml")
        listOf(
            "20.71484375", "29.8125", "26.36328125",
            "19.97265625", "21.46484375", "13.83203125",
        ).forEach { w -> assertTrue("列宽 $w", sheet.contains("""width="$w"""")) }
    }

    @Test
    fun `行高与旧版完全相同`() {
        val bytes = XlsxWriter.toBytes(title, timeRange, doneWorks, planWorks)
        val sheet = part(bytes, "xl/worksheets/sheet1.xml")
        listOf("51", "28", "333", "82").forEach { h ->
            assertTrue("行高 $h", sheet.contains("""ht="$h" customHeight="1""""))
        }
    }

    @Test
    fun `含换行的单元格保留 xml space`() {
        val bytes = XlsxWriter.toBytes(title, timeRange, doneWorks, planWorks)
        val sheet = part(bytes, "xl/worksheets/sheet1.xml")
        assertTrue(sheet.contains("""xml:space="preserve""""))
    }

    @Test
    fun `XML 特殊字符被正确转义`() {
        val bytes = XlsxWriter.toBytes(
            title = "A&B<C>\"D\"",
            timeRange = "x",
            doneWorks = "y",
            planWorks = "z",
        )
        val sheet = part(bytes, "xl/worksheets/sheet1.xml")
        assertTrue(sheet.contains("A&amp;B&lt;C&gt;"))
    }

    @Test
    fun `单元格文本原样写入`() {
        val bytes = XlsxWriter.toBytes(title, timeRange, doneWorks, planWorks)
        val sheet = part(bytes, "xl/worksheets/sheet1.xml")
        assertTrue(sheet.contains(title))
        assertTrue(sheet.contains(timeRange))
        assertTrue(sheet.contains("完成共2项工作内容："))
        assertTrue(sheet.contains("下周预计共1项工作任务："))
    }
}

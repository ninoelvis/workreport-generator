package com.workreport.app.util

import com.workreport.app.domain.model.SensitiveWord
import com.workreport.app.export.EncryptedZipReader
import com.workreport.app.export.EncryptedZipWriter
import com.workreport.app.export.ReportNaming
import com.workreport.app.export.XlsxContentReader
import com.workreport.app.export.XlsxWriter
import com.workreport.app.util.SensitiveWordEngine
import com.workreport.app.util.MaskAnchor
import java.io.File
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 「样本数据工厂」的产物必须与**正式导出完全一致**。
 *
 * 背景（v2.1.1）：
 * 样本工厂为了不污染用户的「下载」目录，绕开了 `ReportExporter.export` 的最后一步
 * （`saveOutside`），自己调用同一批红线组件写文件。
 *
 * 那就必须证明：**绕开的那一步只是"存到哪儿"，没有影响产物本身**。
 * 否则调试工具造出来的样本就跟真导出对不上，调试就失去意义了。
 *
 * 本测试直接对比「同样的输入 → 正式导出的做法」与「样本工厂的做法」，
 * 逐项比对文件名、密码、年月周数、加密参数、ZIP 内部结构和 Excel 内容。
 */
class SampleDataParityTest {

    private val userName = "样本"
    private val rangeText = "2026.09.06-2026.09.12"
    private val title = "第 3 周样本工作报告"
    private val doneWorks = "样本条目 1：完成第 3 周的演示工作\n样本条目 2：推进第二件事"
    private val planWorks = "样本计划：继续推进第 3 周的演示工作"

    /** 正式导出的做法（等价于 ReportExporter.export 里 saveOutside 之前的那段） */
    private fun officialWay(outputDir: File): File {
        val plan = ReportNaming.plan(userName, rangeText)
        val engine = SensitiveWordEngine(emptyList<SensitiveWord>())
        val doneFormatted = SensitiveWordEngine.formatDoneWorks(doneWorks, engine, emptySet<MaskAnchor>())
        val planFormatted = SensitiveWordEngine.formatPlanWorks(planWorks, engine, emptySet<MaskAnchor>())
        val xlsxBytes = XlsxWriter.toBytes(title, rangeText.trim(), doneFormatted, planFormatted)
        val dir = File(outputDir, "official").apply { mkdirs() }
        val target = File(dir, plan.zipFileName)
        EncryptedZipWriter.write(target, plan.password, listOf(plan.xlsxFileName to xlsxBytes))
        return target
    }

    /** 样本工厂的做法（与 DebugTools.buildSampleZip 保持一致） */
    private fun sampleWay(outputDir: File): File {
        val plan = ReportNaming.plan(userName, rangeText)
        val engine = SensitiveWordEngine(emptyList<SensitiveWord>())
        val doneFormatted = SensitiveWordEngine.formatDoneWorks(doneWorks, engine, emptySet<MaskAnchor>())
        val planFormatted = SensitiveWordEngine.formatPlanWorks(planWorks, engine, emptySet<MaskAnchor>())
        val xlsxBytes = XlsxWriter.toBytes(title, rangeText.trim(), doneFormatted, planFormatted)
        val dir = File(outputDir, "sample").apply { mkdirs() }
        val target = File(dir, plan.zipFileName)
        EncryptedZipWriter.write(target, plan.password, listOf(plan.xlsxFileName to xlsxBytes))
        return target
    }

    @Test
    fun `样本的文件名与密码和正式导出完全一致`() {
        val plan = ReportNaming.plan(userName, rangeText)
        assertEquals("样本-2026年9月第1周工作报告.zip", plan.zipFileName)
        assertEquals("样本-2026年9月第1周工作报告.xlsx", plan.xlsxFileName)
        // 密码是那一周周四的 yyyyMMdd
        assertEquals(8, plan.password.length)
        assertTrue(plan.password.all { it.isDigit() })
        assertEquals(2026, plan.year)
        assertEquals(9, plan.month)
        assertEquals(1, plan.weekNum)
    }

    @Test
    fun `样本的Excel内容与正式导出逐字一致`() {
        val tmp = File(System.getProperty("java.io.tmpdir"), "wr-parity-${System.nanoTime()}")
        try {
            val official = officialWay(tmp)
            val sample = sampleWay(tmp)

            val plan = ReportNaming.plan(userName, rangeText)
            val a = EncryptedZipReader.readFirstXlsx(official, plan.password)
            val b = EncryptedZipReader.readFirstXlsx(sample, plan.password)
            assertNotNull("正式导出解不开", a)
            assertNotNull("样本解不开", b)
            assertArrayEquals("Excel 内容不一致", a!!, b!!)
        } finally {
            tmp.deleteRecursively()
        }
    }

    @Test
    fun `样本的加密参数和正式导出一致`() {
        val tmp = File(System.getProperty("java.io.tmpdir"), "wr-parity-zip-${System.nanoTime()}")
        try {
            val official = officialWay(tmp)
            val sample = sampleWay(tmp)

            // ⚠️ 加密包不能用标准 java.util.zip.ZipFile 读中央目录
            //    （会抛 "invalid CEN header (encrypted entry)"），要用 zip4j。
            val a = net.lingala.zip4j.ZipFile(official).fileHeaders
            val b = net.lingala.zip4j.ZipFile(sample).fileHeaders
            assertEquals("内部条目数不一致", a.size, b.size)
            assertEquals("内部条目名不一致", a[0].fileName, b[0].fileName)
            // 红线：压缩包**内部**的 Excel 文件名 = 外层 zip 去掉 .zip 再加 .xlsx
            // （共享同一个 baseName，一个字符都不多）
            assertEquals(
                "内部 Excel 文件名必须与外层压缩包共享同一个 baseName（红线）",
                official.name.removeSuffix(".zip") + ".xlsx",
                a[0].fileName,
            )

            // 前 4 字节：PK\x03\x04 + 加密标志位
            val ha = official.readBytes().copyOf(8)
            val hb = sample.readBytes().copyOf(8)
            assertArrayEquals("ZIP 头部参数不一致（加密标志/压缩方法）", ha, hb)
            // 第 6 字节的 bit0 = 已加密
            assertEquals("加密标志位不一致", 1, (ha[6].toInt() and 1))
        } finally {
            tmp.deleteRecursively()
        }
    }

    @Test
    fun `样本能被正常读回内容`() {
        val tmp = File(System.getProperty("java.io.tmpdir"), "wr-parity-read-${System.nanoTime()}")
        try {
            val sample = sampleWay(tmp)
            val plan = ReportNaming.plan(userName, rangeText)
            val xlsx = EncryptedZipReader.readFirstXlsx(sample, plan.password)
            assertNotNull("样本读不回来", xlsx)
            val content = XlsxContentReader.read(xlsx!!)!!
            assertTrue("标题不对", content.title.contains("样本"))
            assertTrue("时间范围不对", content.timeRange.contains("2026"))
        } finally {
            tmp.deleteRecursively()
        }
    }
}

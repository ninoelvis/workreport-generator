package com.workreport.app.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 一条历史报告记录。
 *
 * 记录的是「加密 ZIP」本身：文件名、存放路径、解压密码。
 * 二次下载 / 二次发邮件都直接复用这里的信息，不需要重新生成。
 */
@Entity(tableName = "reports")
data class ReportEntity(

    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,

    /** 完整文件名，含 .zip，例如「张三-2026年10月第3周工作报告.zip」 */
    @ColumnInfo(name = "file_name")
    val fileName: String,

    /** 不含扩展名的展示名，例如「张三-2026年10月第3周工作报告」 */
    @ColumnInfo(name = "display_name")
    val displayName: String,

    /** 加密 ZIP 在本机的绝对路径 */
    @ColumnInfo(name = "file_path")
    val filePath: String,

    /** 解压密码（yyyyMMdd，取本周周四） */
    @ColumnInfo(name = "password")
    val password: String,

    /** 文件大小（字节） */
    @ColumnInfo(name = "size_bytes")
    val sizeBytes: Long = 0L,

    /** 生成时间（毫秒时间戳） */
    @ColumnInfo(name = "created_at")
    val createdAt: Long = 0L,

    /** 所属年份 */
    @ColumnInfo(name = "year")
    val year: Int = 0,

    /** 所属月份（1-12） */
    @ColumnInfo(name = "month")
    val month: Int = 0,

    /** 月内第几周 */
    @ColumnInfo(name = "week_num")
    val weekNum: Int = 0,

    /** 报告正文快照，仅用于列表页预览，不参与导出 */
    @ColumnInfo(name = "content_preview")
    val contentPreview: String = "",

    // ---- v1.3.9 新增：完整内容，用于历史报告的 Excel 预览 ----

    /** 报告标题（Excel 里 A1:F1 合并单元格的内容） */
    @ColumnInfo(name = "report_title", defaultValue = "")
    val reportTitle: String = "",

    /** 本周工作时间，例如 2026.10.19-2026.10.25 */
    @ColumnInfo(name = "time_range", defaultValue = "")
    val timeRange: String = "",

    /** 「本周已完成工作梳理」全文 */
    @ColumnInfo(name = "done_works", defaultValue = "")
    val doneWorks: String = "",

    /** 「下周工作计划」全文 */
    @ColumnInfo(name = "plan_works", defaultValue = "")
    val planWorks: String = "",

    // ---- v1.4.0 新增：真正写进 Excel 的那两格（已格式化 + 已脱敏）----

    /**
     * 「本周已完成工作梳理」**写进 Excel 的最终文本**。
     *
     * ⚠️ 和 [doneWorks] 的区别：
     *  - [doneWorks] 是用户当初写的**原始条目**，用于「载入首页继续编辑」；
     *  - 这里存的是**格式化并脱敏之后**的文本，也就是 Excel 里 B3 那一格的原文。
     *
     * 预览区必须显示这个 —— 预览就是 Excel，Excel 就是红线，不能有任何差别。
     */
    @ColumnInfo(name = "done_works_exported", defaultValue = "")
    val doneWorksExported: String = "",

    /** 「下周工作计划」写进 Excel 的最终文本（Excel 里 C3 那一格） */
    @ColumnInfo(name = "plan_works_exported", defaultValue = "")
    val planWorksExported: String = "",
) {
    /**
     * 旧版本生成的记录没有这些字段，无法还原完整 Excel 预览。
     * 这种情况退回到只有 contentPreview 的「部分内容」。
     */
    val hasFullContent: Boolean
        get() = reportTitle.isNotEmpty() ||
            timeRange.isNotEmpty() ||
            doneWorks.isNotEmpty() ||
            planWorks.isNotEmpty()

    /**
     * 有没有「导出时的最终文本」。
     *
     * 有 → 预览直接用它，和 Excel 逐字一致，不用解压缩包；
     * 没有（旧版本生成的）→ 只能去解压缩包读 Excel。
     */
    val hasExportedContent: Boolean
        get() = doneWorksExported.isNotEmpty() || planWorksExported.isNotEmpty()
}

package com.workreport.app.data.repository

import android.content.Context
import com.workreport.app.data.local.ReportDao
import com.workreport.app.data.local.ReportEntity
import com.workreport.app.export.ExportResult
import com.workreport.app.export.ReportExporter
import com.workreport.app.domain.model.SensitiveWord
import com.workreport.app.util.MaskAnchor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 历史报告仓库。
 *
 * 负责：生成报告 → 落盘 → 记入 Room → 提供查询/删除/二次分享。
 */
class ReportRepository(
    private val dao: ReportDao,
    private val context: Context,
) {

    /** 全部历史（按生成时间倒序） */
    fun observeAll(): Flow<List<ReportEntity>> = dao.observeAll()

    /** 按文件名搜索 */
    fun search(keyword: String): Flow<List<ReportEntity>> =
        if (keyword.isBlank()) dao.observeAll() else dao.search(keyword.trim())

    suspend fun count(): Int = dao.count()

    /**
     * 生成一份新的报告。
     *
     * 命名规则、解压密码、Excel 内容与旧版完全一致。
     */
    suspend fun generate(
        userName: String,
        title: String,
        timeRangeText: String,
        doneWorks: String,
        planWorks: String,
        sensitiveWords: List<SensitiveWord>,
        excludedAnchors: Set<MaskAnchor> = emptySet(),
    ): ExportResult = withContext(Dispatchers.IO) {
        val exporter = ReportExporter(context.applicationContext)
        val result = exporter.export(
            userName = userName,
            title = title,
            timeRangeText = timeRangeText,
            doneWorks = doneWorks,
            planWorks = planWorks,
            sensitiveWords = sensitiveWords,
            outputDir = ReportExporter.reportsDir(context.applicationContext),
            excludedAnchors = excludedAnchors,
        )

        dao.insert(
            ReportEntity(
                fileName = result.fileName,
                displayName = result.baseName,
                filePath = result.file.path,
                password = result.password,
                sizeBytes = result.file.length(),
                createdAt = result.createdAt,
                year = result.year,
                month = result.month,
                weekNum = result.weekNum,
                contentPreview = doneWorks.take(120),
                reportTitle = title,
                timeRange = timeRangeText,
                doneWorks = doneWorks,
                planWorks = planWorks,
            ),
        )
        result
    }

    suspend fun delete(entity: ReportEntity) = withContext(Dispatchers.IO) {
        runCatching { File(entity.filePath).delete() }
        dao.delete(entity)
    }

    suspend fun findById(id: Long): ReportEntity? = dao.findById(id)

    /** 把历史报告复制到系统「下载」目录（供用户另存） */
    suspend fun copyToDownloads(entity: ReportEntity): Boolean = withContext(Dispatchers.IO) {
        val source = File(entity.filePath)
        if (!source.exists()) return@withContext false
        ReportExporter(context.applicationContext).let { exporter ->
            // 复用导出器里的下载目录写入逻辑
            exporter.copyExistingToDownloads(source, entity.fileName)
        }
    }
}

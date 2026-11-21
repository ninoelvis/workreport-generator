package com.workreport.app.data.repository

import android.content.Context
import com.workreport.app.data.local.ReportDao
import com.workreport.app.data.local.ReportEntity
import com.workreport.app.export.ExportResult
import com.workreport.app.ServiceLocator
import com.workreport.app.export.ReportExporter
import com.workreport.app.domain.model.SensitiveWord
import com.workreport.app.util.LikeEscape
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

    /**
     * 搜索历史报告。
     *
     * @param scope 搜索范围：仅文件名，还是连正文一起搜（v2.0.0 起）
     *
     * ⚠️ 关键词在这里做 LIKE 转义（[LikeEscape]），
     * 用户输入的 `%` `_` 不会被当成通配符。
     */
    /**
     * 搜索历史报告（v2.0.1 起：文件名与正文**一次全搜**）。
     *
     * 之前分成「仅文件名 / 全部内容」两个模式（v2.0.0），
     * 要用户先想清楚搜哪一类，反而麻烦；现在一个关键词同时匹配两者。
     */
    fun search(keyword: String): Flow<List<ReportEntity>> {
        val trimmed = keyword.trim()
        if (trimmed.isBlank()) return dao.observeAll()
        return dao.search(LikeEscape.escape(trimmed))
    }

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
            // 用户选过存放目录就写那里，没选过就沿用系统「下载」目录
            storage = ServiceLocator.storageLocation,
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
                // 列表摘要也用导出文本，和展开后的预览保持一致
                contentPreview = result.doneWorksExported.take(120),
                reportTitle = title,
                timeRange = timeRangeText,
                // 原始条目：留给「载入首页继续编辑」
                doneWorks = doneWorks,
                planWorks = planWorks,
                // 导出文本：Excel 里那两格的原文，预览区显示的就是它
                doneWorksExported = result.doneWorksExported,
                planWorksExported = result.planWorksExported,
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
    /**
     * 「再下载」：把报告再落一份到用户看得见的地方。
     *
     *  - 用户选过存放目录 → 写进那个目录
     *  - 没选过 → 沿用系统「下载」目录
     *
     * ⚠️ 文件名原样使用（entity.fileName），不加分隔符、不加序号。
     */
    /** 所有已入库报告的文件名（用来和存放目录里的外部文件去重） */
    suspend fun allFileNames(): Set<String> = withContext(Dispatchers.IO) {
        dao.all().map { it.fileName }.toSet()
    }

    suspend fun copyToDownloads(entity: ReportEntity): Boolean = withContext(Dispatchers.IO) {
        val source = File(entity.filePath)
        if (!source.exists()) return@withContext false
        ReportExporter(context.applicationContext).let { exporter ->
            exporter.saveOutside(source, entity.fileName, ServiceLocator.storageLocation)
        }
    }
}

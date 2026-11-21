package com.workreport.app.ui.debug

import android.content.Context
import com.workreport.app.ServiceLocator
import com.workreport.app.data.local.ReportEntity
import com.workreport.app.util.SensitiveWordEngine
import com.workreport.app.export.EncryptedZipWriter
import com.workreport.app.export.ReportExporter
import com.workreport.app.export.ReportNaming
import com.workreport.app.export.XlsxWriter
import com.workreport.app.util.DateUtils
import java.io.File
import java.util.Calendar
import java.util.Date
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * 开发者调试工具（v2.0.4 起）。
 *
 * 这一版给「最近新增的功能」都配了**可调试入口**，
 * 方便开发/自测时不用手工造数据就能验证：
 *
 * | 新功能 | 调试入口 |
 * |---|---|
 * | 周报统计（v2.0.0 / v2.0.2） | 样本数据工厂：造跨月样本，图表立刻有东西看 |
 * | 搜索覆盖正文（v2.0.0 / v2.0.2） | 搜索命中自检：区分「命中文件名」和「只命中正文」 |
 * | 时间范围快捷（v2.0.0） | 周范围自检：打印本周/上周算出来的区间 |
 * | 主题护栏 + App 内切换（v2.0.0） | 主题即时切换：浅色 / 深色 / 跟随系统 |
 * | 统一动效（v2.0.1 / v2.0.4） | 动效开关 + 速度调节 |
 * | 图标改版（v2.0.3） | 图标资源自检：各密度资源与字标尺寸 |
 * | 隐私加固（v2.0.0） | 敏感词库自检：是否已加密落盘 |
 *
 * ⚠️ 这些**只在隐藏的调试页**里可见（我的 → 关于 → 连点版本号 7 次）。
 *
 * ⚠️ 样本数据只在应用私有目录里生成，**不会**写进用户的「下载」目录。
 */
object DebugTools {

    /** 样本报告的名字前缀 */
    const val SAMPLE_PREFIX = "样本"

    /**
     * 样本报告的标题标记。
     *
     * 清理样本时要**名字前缀 + 标题标记同时满足**才删，
     * 免得用户真名撞上前缀，把真实报告误删。
     */
    const val SAMPLE_TITLE_MARK = "周样本工作报告"

    // ---------------- 样本数据工厂 ----------------

    /**
     * 造一批**样本报告**。
     *
     * 每一份都是**真的导出**（真的加密 ZIP + 真的 Excel），
     * 所以预览、再次下载、再次发邮件都能正常试；
     * 只是时间被挪到了过去几个月 —— 这样统计页的「按月 / 按周」图表才有内容。
     *
     * 命名 / 密码 / 年月周数全部由 `timeRangeText` 推出来，
     * 所以样本的「文件名月份」和「统计月份」是**一致**的，不会自相矛盾。
     */
    suspend fun createSampleReports(
        context: Context,
        count: Int = 12,
    ): String = withContext(Dispatchers.IO) {
        val dao = ServiceLocator.database.reportDao()
        val outputDir = ReportExporter.reportsDir(context.applicationContext)
        // 本次运行的批次号：同一周重复点「造样本」也不会互相覆盖
        // （正式导出用生成时间戳做子目录名，这里同理）。
        val batchId = System.currentTimeMillis()
        // 基准日：每次循环都从它**重新算**，不能在上一次的结果上继续加 ——
        // 累加的话 12 份会横跨 12×11 = 132 周（约 3 年），完全不是「跨 12 周」。
        val base = Calendar.getInstance()
        val sb = StringBuilder()
        var ok = 0

        for (i in 0 until count) {
            // 往前推 count 周，每周造一份
            val c = (base.clone() as Calendar).apply {
                add(Calendar.WEEK_OF_YEAR, -(count - 1 - i))
            }
            // ⚠️ 必须用 DateUtils.getWeekRange，不能自己 set(DAY_OF_WEEK)：
            //    今天是周日时，set(DAY_OF_WEEK, SATURDAY) 会退到**上周六**，
            //    区间就变成「上周日 ~ 上周六」了。
            val (sunday, saturday) = DateUtils.getWeekRange(c.time)
            val rangeText = DateUtils.fmtDate(sunday) + "-" + DateUtils.fmtDate(saturday)

            val doneWorks = buildString {
                for (k in 1..(3 + i % 5)) {
                    if (isNotEmpty()) append('\n')
                    append("样本条目 ").append(k).append("：完成第 ").append(i + 1).append(" 周的演示工作")
                }
            }
            val planWorks = "样本计划：继续推进第 ${i + 1} 周的演示工作"

            val title = "第 ${i + 1} ${SAMPLE_TITLE_MARK}"
            val result = runCatching {
                buildSampleZip(
                    outputDir = outputDir,
                    batch = batchId,
                    userName = SAMPLE_PREFIX,
                    title = title,
                    timeRangeText = rangeText,
                    doneWorks = doneWorks,
                    planWorks = planWorks,
                    createdAt = sunday.time,
                )
            }.getOrNull() ?: continue

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
                    contentPreview = result.doneWorksExported.take(120),
                    reportTitle = title,
                    timeRange = rangeText,
                    doneWorks = doneWorks,
                    planWorks = planWorks,
                    doneWorksExported = result.doneWorksExported,
                    planWorksExported = result.planWorksExported,
                ),
            )
            ok++
        }

        sb.append("已生成 ").append(ok).append(" 份样本报告。\n")
        sb.append("文件名统一以「").append(SAMPLE_PREFIX).append("-」开头。\n")
        sb.append("只落在应用私有目录，没有写进你的「下载」目录。\n")
        sb.append("去「历史报告 → 统计」看图表，或者点开任意一份试预览。")
        sb.toString()
    }

    /**
     * 造一份**样本**压缩包。
     *
     * ⚠️ 为什么不直接调 `ReportExporter.export`：
     *   它的最后一步 `saveOutside(target, zipFileName, storage)` 在 `storage == null` 时
     *   **恰恰是写进系统「下载」目录**（不是"不外发"！）——
     *   每点一次就会往用户的下载目录里丢一批文件。
     *
     *   所以这里**绕开那一步**，直接调用同一批红线组件
     *   （ReportNaming / SensitiveWordEngine / XlsxWriter / EncryptedZipWriter），
     *   产出与正式导出**完全一致**的文件名、密码、加密参数、ZIP 结构和 Excel 内容，
     *   但**只落在应用私有目录**，一个字都不进用户的下载目录。
     *
     *   红线文件一个都没改 —— 只是换个调用顺序。
     */
    private fun buildSampleZip(
        outputDir: File,
        batch: Long,
        userName: String,
        title: String,
        timeRangeText: String,
        doneWorks: String,
        planWorks: String,
        createdAt: Long,
    ): SampleZip {
        // 命名与密码规则（红线组件，参数与正式导出完全一致）
        val plan = ReportNaming.plan(userName, timeRangeText)

        // 脱敏 + 格式化（样本不含敏感词，所以内容不会被改）
        val engine = SensitiveWordEngine(emptyList())
        val doneFormatted = SensitiveWordEngine.formatDoneWorks(doneWorks, engine, emptySet())
        val planFormatted = SensitiveWordEngine.formatPlanWorks(planWorks, engine, emptySet())

        // Excel（红线组件）
        val xlsxBytes = XlsxWriter.toBytes(
            title = title,
            timeRange = timeRangeText.trim(),
            doneWorks = doneFormatted,
            planWorks = planFormatted,
        )

        // 只写私有目录：子目录用批次号，同一周重复生成也不会互相覆盖
        val dir = File(outputDir, "sample-$batch").apply { mkdirs() }
        val target = File(dir, plan.zipFileName)

        // 加密 ZIP（红线组件：AE-1 = AES-256 + HMAC-SHA1）
        EncryptedZipWriter.write(
            target = target,
            password = plan.password,
            entries = listOf(plan.xlsxFileName to xlsxBytes),
        )

        return SampleZip(
            fileName = plan.zipFileName,
            baseName = plan.baseName,
            password = plan.password,
            file = target,
            year = plan.year,
            month = plan.month,
            weekNum = plan.weekNum,
            createdAt = createdAt,
            doneWorksExported = doneFormatted,
            planWorksExported = planFormatted,
        )
    }

    /** 样本压缩包的产出 */
    private data class SampleZip(
        val fileName: String,
        val baseName: String,
        val password: String,
        val file: File,
        val year: Int,
        val month: Int,
        val weekNum: Int,
        val createdAt: Long,
        val doneWorksExported: String,
        val planWorksExported: String,
    )

    /**
     * 删掉所有样本报告（连文件一起删）。
     *
     * ⚠️ 判定条件要**两个都满足**，不能只看名字前缀 ——
     *    用户真名如果以「样本」开头（比如本人姓「样本」），真实报告会被一起删掉，
     *    而且删了就找不回来了。
     *
     *    样本的标题是「第 N 周样本工作报告」，用户真报告的标题是用户自己填的，
     *    两个条件同时成立才认定为样本。
     */
    suspend fun clearSampleReports(): String = withContext(Dispatchers.IO) {
        val dao = ServiceLocator.database.reportDao()
        val samples = dao.all().filter {
            it.displayName.startsWith(SAMPLE_PREFIX) &&
                it.reportTitle.contains(SAMPLE_TITLE_MARK)
        }
        var n = 0
        val dirs = HashSet<File>()
        for (s in samples) {
            val f = File(s.filePath)
            runCatching { f.delete() }
            f.parentFile?.let { dirs.add(it) }
            dao.delete(s)
            n++
        }
        // 顺带清掉空的批次目录
        dirs.forEach { d -> runCatching { if (d.isDirectory && d.listFiles()?.isEmpty() == true) d.delete() } }
        "已删除 $n 份样本报告。"
    }

    // ---------------- 搜索自检 ----------------

    /**
     * 搜索命中自检。
     *
     * 走的是**真实的 DAO 搜索**，然后在内存里把命中拆成两类：
     *   · 命中文件名 / 标题 / 时间范围
     *   · **只**命中正文（文件名里根本没有那个词）
     *
     * 这正是 v2.0.0「搜索覆盖正文」和 v2.0.2「搜索整合成一个框」要验证的行为。
     */
    suspend fun probeSearch(keyword: String): String = withContext(Dispatchers.IO) {
        val kw = keyword.trim()
        if (kw.isEmpty()) return@withContext "请先输入一个关键词。"
        val dao = ServiceLocator.database.reportDao()
        val hits = dao.search(kw).first()
        val nameHits = hits.count { r ->
            r.fileName.contains(kw, true) ||
                r.displayName.contains(kw, true) ||
                r.reportTitle.contains(kw, true) ||
                r.timeRange.contains(kw, true)
        }
        buildString {
            append("关键词：").append(kw).append('\n')
            append("命中总数：").append(hits.size).append(" 条\n")
            append("　其中命中文件名/标题/时间：").append(nameHits).append(" 条\n")
            append("　只命中正文：").append(hits.size - nameHits).append(" 条\n\n")
            if (hits.isEmpty()) {
                append("（数据库里一共 ")
                append(dao.count())
                append(" 份报告）")
            } else {
                hits.take(5).forEach {
                    append("· ").append(it.displayName).append('\n')
                }
                if (hits.size > 5) append("（只列了前 5 条）")
            }
        }
    }

    // ---------------- 周范围自检 ----------------

    /** 本周 / 上周的时间区间自检（v2.0.0 的「本周 / 上周」快捷按钮） */
    fun probeWeekRanges(today: Date = Date()): String {
        val current = DateUtils.currentWeekRangeText(today)
        val previous = DateUtils.previousWeekRangeText(today)
        val sunday = DateUtils.parseDateRange(current)?.first
        val thursday = sunday?.let { DateUtils.getThursday(it) }
        return buildString {
            append("今天：").append(DateUtils.fmtDate(today)).append('\n')
            append("本周：").append(current).append('\n')
            append("上周：").append(previous).append('\n')
            append('\n')
            append("一周的定义是「周日 ~ 周六」。\n")
            append("解压密码取的是这一周的周四：")
                .append(thursday?.let { DateUtils.fmtDate8(it) } ?: "—")
                .append('\n')
        }
    }

    // ---------------- 图标资源自检 ----------------

    /**
     * 图标资源自检（v2.0.3 图标改版）。
     *
     * 检查各密度的桌面图标与 App 内字标资源是否齐全，
     * 并打印字标的实际像素尺寸（自适应图标前景是 108dp 画布）。
     */
    fun probeLauncherIcons(): String {
        val resRoot = File("/workspace/native/app/src/main/res")
        if (!resRoot.exists()) {
            return "找不到资源目录（本机才有效，源码包里也能跑）。"
        }
        val densities = listOf("mdpi", "hdpi", "xhdpi", "xxhdpi", "xxxhdpi")
        val sb = StringBuilder()
        sb.append("桌面图标（每种密度）：\n")
        for (d in densities) {
            val dir = File(resRoot, "mipmap-$d")
            val names = listOf(
                "ic_launcher.png",
                "ic_launcher_round.png",
                "ic_launcher_foreground.png",
                "ic_launcher_monochrome.png",
                "ic_launcher_splash.png",
            )
            val missing = names.filter { !File(dir, it).exists() }
            sb.append("  ").append(d).append("：")
                .append(if (missing.isEmpty()) "齐全" else "缺 " + missing.joinToString())
                .append('\n')
        }
        sb.append('\n')
        sb.append("App 内字标（Work / Report 两段）：\n")
        for (d in densities) {
            val dir = File(resRoot, "drawable-$d")
            val w = File(dir, "ic_wordmark_work.png")
            val r = File(dir, "ic_wordmark_report.png")
            if (!w.exists() || !r.exists()) {
                sb.append("  ").append(d).append("：缺文件\n")
                continue
            }
            val sw = pngSize(w)
            val sr = pngSize(r)
            sb.append("  ").append(d).append("：Work ").append(sw.first).append("x").append(sw.second)
                .append("，Report ").append(sr.first).append("x").append(sr.second)
                .append('\n')
        }
        sb.append('\n')
        val night = resRoot.listFiles { f -> f.isDirectory && f.name.contains("night") }
        sb.append("夜间资源目录：")
            .append(if (night.isNullOrEmpty()) "无（桌面图标不跟随系统深浅色）" else night.joinToString { it.name })
            .append('\n')
        return sb.toString()
    }

    /** 纯 java.io 读 PNG 头的宽高（Android 里没有 javax.imageio） */
    private fun pngSize(f: File): Pair<Int, Int> = runCatching {
        f.inputStream().use { ins ->
            val head = ByteArray(24)
            ins.read(head)
            fun be(o: Int): Int =
                ((head[o].toInt() and 0xFF) shl 24) or
                    ((head[o + 1].toInt() and 0xFF) shl 16) or
                    ((head[o + 2].toInt() and 0xFF) shl 8) or
                    (head[o + 3].toInt() and 0xFF)
            be(16) to be(20)
        }
    }.getOrDefault(0 to 0)
}

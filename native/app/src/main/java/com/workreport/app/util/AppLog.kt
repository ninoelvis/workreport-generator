package com.workreport.app.util

import android.util.Log
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 应用内日志缓冲。
 *
 * 两个用途：
 *  1. **异常留痕** —— App 里那些被 `runCatching` 吞掉的异常，
 *     平时谁也不知道发生过。这里记一份，调试页能看。
 *  2. **崩溃留痕** —— 装一个全局未捕获异常处理器，
 *     先把崩溃写进缓冲，再交给系统原来的处理器（不影响系统的崩溃弹窗）。
 *
 * ⚠️ 只在内存里，不落盘、不上传 —— 和 App 其它数据一样，只存在本机。
 */
object AppLog {

    /** 一条日志 */
    data class Entry(
        val time: String,
        val level: String,
        val tag: String,
        val message: String,
        val stack: String? = null,
    )

    private const val MAX_ENTRIES = 300
    private val timeFormat = SimpleDateFormat("MM-dd HH:mm:ss", Locale.getDefault())
    private val entries = ArrayDeque<Entry>()

    /** 记录一条日志 */
    fun record(level: String, tag: String, message: String, error: Throwable? = null) {
        val entry = Entry(
            time = timeFormat.format(Date()),
            level = level,
            tag = tag,
            message = message,
            stack = error?.stackTraceToString()?.take(2000),
        )
        synchronized(entries) {
            entries.addLast(entry)
            while (entries.size > MAX_ENTRIES) entries.removeFirst()
        }
    }

    /** 记录一个被捕获的异常 */
    fun error(tag: String, message: String, error: Throwable) {
        record("E", tag, message, error)
    }

    /** 最近的日志（新的在前） */
    fun recent(limit: Int = 200): List<Entry> =
        synchronized(entries) { entries.toList().asReversed().take(limit) }

    /** 清空缓冲 */
    fun clear() {
        synchronized(entries) { entries.clear() }
    }

    /**
     * 装上全局未捕获异常处理器。
     *
     * 只做「留痕」，**不改变系统行为**：
     * 记完之后立刻把异常交还给原来的处理器，系统的崩溃提示照旧。
     */
    fun installUncaughtHandler() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        if (previous is InstalledHandler) return
        Thread.setDefaultUncaughtExceptionHandler(InstalledHandler(previous))
    }

    private class InstalledHandler(
        private val previous: Thread.UncaughtExceptionHandler?,
    ) : Thread.UncaughtExceptionHandler {
        override fun uncaughtException(thread: Thread, error: Throwable) {
            try {
                error("Crash", "线程 ${thread.name} 未捕获异常", error)
            } catch (e: Throwable) {
                // 留痕失败也不能影响崩溃处理
                Log.e("AppLog", "记录崩溃日志失败", e)
            }
            // 交还给系统原来的处理器，行为保持不变
            previous?.uncaughtException(thread, error)
        }
    }
}

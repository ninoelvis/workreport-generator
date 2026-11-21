package com.workreport.app.util

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * v2.0.0「历史报告批量操作」的防回归测试。
 *
 * 要守住的三件事：
 *
 * 1. **只做适合批量的操作**：批量删除 + 批量再下载。
 *    「载入首页」「取回下周计划」是单选语义（会互相覆盖），
 *    「批量发邮件」的多附件 Intent 可能超限或被邮箱拒收 —— 都不能出现。
 * 2. **多选时底栏要让位**：批量操作条浮在底部，玻璃胶囊必须淡出，
 *    否则两个东西会叠在一起、底部内容被盖住。
 * 3. **删除要连文件一起删**：只删数据库记录的话，压缩包还在硬盘上，
 *    用户会以为没删成功。
 */
class BulkSelectionTest {

    private val srcRoot: String =
        File("src/main/java/com/workreport/app").absolutePath

    private fun read(path: String): String = File(srcRoot, path).readText()

    @Test
    fun `批量操作只有删除和再次下载`() {
        val vm = read("ui/history/HistoryViewModel.kt")
        assertTrue("应有批量删除", vm.contains("fun deleteSelected"))
        assertTrue("应有批量再次下载", vm.contains("fun downloadSelected"))

        // 不该出现的批量操作
        assertTrue(
            "「载入首页」是单选语义，不能做批量（会互相覆盖）",
            !vm.contains("fun loadSelectedIntoHome") && !vm.contains("fun mailSelected"),
        )
        assertTrue(
            "「取回下周计划」是单选语义，不能做批量",
            !vm.contains("fun takePlanSelected"),
        )
        assertTrue(
            "批量发邮件会因多附件 Intent 超限而出问题，不能做",
            !vm.contains("fun sendSelected"),
        )
    }

    @Test
    fun `批量删除必须连文件一起删`() {
        val vm = read("ui/history/HistoryViewModel.kt")
        assertTrue(
            "批量删除除了删数据库记录，还要删本机的压缩包文件",
            vm.contains("File(e.filePath).delete()") || vm.contains("filePath).delete()"),
        )
    }

    @Test
    fun `多选状态要能被界面读到`() {
        val vm = read("ui/history/HistoryViewModel.kt")
        assertTrue("UiState 应该有 selectionMode", vm.contains("val selectionMode: Boolean"))
        assertTrue("UiState 应该有 selectedIds", vm.contains("val selectedIds: Set<Long>"))
        assertTrue("应该有进入多选的方法", vm.contains("fun enterSelection"))
        assertTrue("应该有切换选中的方法", vm.contains("fun toggleSelection"))
        assertTrue("应该有全选的方法", vm.contains("fun toggleSelectAll"))
        assertTrue("应该有退出多选的方法", vm.contains("fun exitSelection"))
    }

    @Test
    fun `多选时底栏要让位给批量操作条`() {
        val appRoot = read("ui/AppRoot.kt")
        assertTrue(
            "AppRoot 必须监听多选状态",
            appRoot.contains("selectionMode"),
        )
        assertTrue(
            "多选时底栏要隐藏（visible 要考虑 selectionMode）",
            appRoot.contains("!selectionMode") || appRoot.contains("(!selectionMode)"),
        )

        val bar = read("ui/AnimatedBottomBar.kt")
        assertTrue(
            "批量操作条的占位高度常量要存在",
            bar.contains("BULK_BAR_SPACE"),
        )
    }

    @Test
    fun `多选时卡片要精简`() {
        val screen = read("ui/history/HistoryScreen.kt")
        assertTrue(
            "多选时要隐藏卡片的操作按钮区",
            screen.contains("if (!selectionMode)"),
        )
        assertTrue(
            "多选时要显示勾选框",
            screen.contains("contentDescription = \"已选中\""),
        )
        assertTrue(
            "多选时要支持长按进入",
            screen.contains("onLongClick") && screen.contains("combinedClickable"),
        )
        assertTrue(
            "底部要有批量操作条",
            screen.contains("fun BulkActionBar"),
        )
    }

    @Test
    fun `批量操作条要提供取消全选与删除下载`() {
        val screen = read("ui/history/HistoryScreen.kt")
        assertTrue("操作条要有取消", screen.contains("onCancel"))
        assertTrue("操作条要有全选", screen.contains("onToggleSelectAll"))
        assertTrue("操作条要有删除", screen.contains("onDelete"))
        assertTrue("操作条要有再次下载", screen.contains("onDownload"))
        // 单选语义的操作不该出现在操作条上
        val barBlock = screen.substringAfter("private fun BulkActionBar")
            .substringBefore("\n}\n")
        assertTrue(
            "操作条里不该有「载入首页」",
            !barBlock.contains("载入首页") && !barBlock.contains("取回下周计划"),
        )
    }
}

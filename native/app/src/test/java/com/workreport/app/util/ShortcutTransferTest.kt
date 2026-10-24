package com.workreport.app.util

import com.workreport.app.domain.model.ShortcutSlot
import com.workreport.app.domain.model.ShortcutTemplate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 快捷模板的复制 / 导入编解码。
 */
class ShortcutTransferTest {

    @Test
    fun `编解码往返后模板内容一致`() {
        val templates = listOf(
            ShortcutTemplate("a", "开发接口联调"),
            ShortcutTemplate("b", "处理线上问题"),
        )
        val encoded = ShortcutTransfer.encode(ShortcutSlot.WORK, templates)
        val decoded = ShortcutTransfer.decode(encoded)
        assertNotNull(decoded)
        assertEquals(ShortcutSlot.WORK, decoded!!.first)
        assertEquals(templates.map { it.text }, decoded.second.map { it.text })
    }

    @Test
    fun `导入后 id 会重新生成，不会和原来撞`() {
        val templates = listOf(ShortcutTemplate("same-id", "内容"))
        val encoded = ShortcutTransfer.encode(ShortcutSlot.PLAN, templates)
        val decoded = ShortcutTransfer.decode(encoded)!!
        assertTrue(decoded.second[0].id != "same-id")
    }

    @Test
    fun `两套分区的编码能区分开`() {
        val work = ShortcutTransfer.encode(ShortcutSlot.WORK, listOf(ShortcutTemplate("1", "a")))
        val plan = ShortcutTransfer.encode(ShortcutSlot.PLAN, listOf(ShortcutTemplate("2", "b")))
        assertEquals(ShortcutSlot.WORK, ShortcutTransfer.decode(work)!!.first)
        assertEquals(ShortcutSlot.PLAN, ShortcutTransfer.decode(plan)!!.first)
    }

    @Test
    fun `正文里有换行和分隔符也不会被弄坏`() {
        val templates = listOf(ShortcutTemplate("1", "第一行\n第二行|带竖线"))
        val decoded = ShortcutTransfer.decode(
            ShortcutTransfer.encode(ShortcutSlot.WORK, templates),
        )!!
        assertEquals("第一行\n第二行|带竖线", decoded.second[0].text)
    }

    @Test
    fun `不是模板文本就返回 null`() {
        assertNull(ShortcutTransfer.decode(null))
        assertNull(ShortcutTransfer.decode(""))
        assertNull(ShortcutTransfer.decode("今天天气不错"))
        assertNull(ShortcutTransfer.decode("WR-TPL:"))
    }
}

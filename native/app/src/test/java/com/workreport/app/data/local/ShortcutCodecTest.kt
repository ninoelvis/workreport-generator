package com.workreport.app.data.local

import com.workreport.app.domain.model.ShortcutSlot
import com.workreport.app.domain.model.ShortcutTemplate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 快捷记录模板存取往返。
 *
 * 模板是用户自己攒下来的重复内容，编解码必须原样保留，
 * 尤其要覆盖带换行、带中文、含分隔符这些边界。
 */
class ShortcutCodecTest {

    @Test
    fun `空值解出空列表`() {
        assertEquals(emptyList<ShortcutTemplate>(), ShortcutCodec.decode(null))
        assertEquals(emptyList<ShortcutTemplate>(), ShortcutCodec.decode(""))
        assertEquals(emptyList<ShortcutTemplate>(), ShortcutCodec.decode("   "))
    }

    @Test
    fun `脏数据不会抛异常`() {
        assertEquals(emptyList<ShortcutTemplate>(), ShortcutCodec.decode("这不是编码"))
        assertEquals(emptyList<ShortcutTemplate>(), ShortcutCodec.decode("!!!"))
        assertEquals(emptyList<ShortcutTemplate>(), ShortcutCodec.decode("aGVsbG8="))
    }

    @Test
    fun `普通模板往返后原样保留`() {
        val list = listOf(
            ShortcutTemplate("id-1", "参加部门例会"),
            ShortcutTemplate("id-2", "整理案件台账"),
        )
        assertEquals(list, ShortcutCodec.decode(ShortcutCodec.encode(list)))
    }

    @Test
    fun `带换行的模板不会被拆坏`() {
        val list = listOf(
            ShortcutTemplate("id-1", "第一行\n第二行\n第三行"),
            ShortcutTemplate("id-2", "普通内容"),
        )
        val restored = ShortcutCodec.decode(ShortcutCodec.encode(list))
        assertEquals(2, restored.size)
        assertEquals("第一行\n第二行\n第三行", restored[0].text)
        assertEquals("普通内容", restored[1].text)
    }

    @Test
    fun `含特殊字符与中文标点的模板完好`() {
        val list = listOf(
            ShortcutTemplate("id-1", "核对《民法典》条文（第 37 条）"),
            ShortcutTemplate("id-2", "a=b&c=d?e"),
            ShortcutTemplate("id-3", "带 emoji 的内容 🚀"),
        )
        assertEquals(list, ShortcutCodec.decode(ShortcutCodec.encode(list)))
    }

    @Test
    fun `空模板列表编码后仍是空列表`() {
        assertEquals(emptyList<ShortcutTemplate>(), ShortcutCodec.decode(ShortcutCodec.encode(emptyList())))
    }

    @Test
    fun `两套模板的存储键互不相同`() {
        assertTrue(ShortcutSlot.WORK.storageKey != ShortcutSlot.PLAN.storageKey)
        assertTrue(ShortcutSlot.WORK.storageKey.isNotEmpty())
        assertTrue(ShortcutSlot.PLAN.storageKey.isNotEmpty())
    }
}

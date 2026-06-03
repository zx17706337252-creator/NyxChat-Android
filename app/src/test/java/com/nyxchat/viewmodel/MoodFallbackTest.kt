package com.nyxchat.viewmodel

import org.junit.Assert.*
import org.junit.Test

/**
 * inferMoodFallback() 单元测试
 *
 * 验证情绪兜底推断的两个核心约束：
 *  1. 信号明确（某情绪关键词明显多于其他）→ 返回该情绪
 *  2. 信号模糊（多种情绪关键词混杂）→ 返回 null，保持当前情绪不变
 */
class MoodFallbackTest {

    @Test
    fun `returns null for empty text`() {
        assertNull(ChatViewModel.inferMoodFallback(""))
    }

    @Test
    fun `returns null for neutral greeting with no mood signals`() {
        assertNull(ChatViewModel.inferMoodFallback("嗯，好的，没什么。"))
    }

    @Test
    fun `detects happy mood from clear positive signals`() {
        val result = ChatViewModel.inferMoodFallback("哈哈哈，今天真的好开心，超级好玩！")
        assertEquals("happy", result)
    }

    @Test
    fun `detects sad mood from clear negative emotional signals`() {
        val result = ChatViewModel.inferMoodFallback("好难过，眼泪都快出来了，心里很委屈。")
        assertEquals("sad", result)
    }

    @Test
    fun `detects affectionate mood from intimacy signals`() {
        val result = ChatViewModel.inferMoodFallback("我真的很在乎你，想你，想靠近你。")
        assertEquals("affectionate", result)
    }

    @Test
    fun `detects angry mood from conflict signals`() {
        val result = ChatViewModel.inferMoodFallback("烦死了，讨厌，走开，不想理你！")
        assertEquals("angry", result)
    }

    @Test
    fun `returns null when signals are ambiguous mixed`() {
        // happy + sad 均有信号，差值 < 2，不置信
        val result = ChatViewModel.inferMoodFallback("开心又难过，哭着笑，委屈却高兴。")
        assertNull("混合信号应返回 null", result)
    }

    @Test
    fun `dominant mood wins when difference is at least 2`() {
        // 3 个 happy 信号，1 个 sad 信号，差值 = 2，应返回 happy
        val result = ChatViewModel.inferMoodFallback("开心开心好玩，有点难过。")
        assertEquals("happy", result)
    }
}

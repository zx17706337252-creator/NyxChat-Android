package com.nyxchat.pipeline

import com.nyxchat.data.*
import org.junit.Assert.*
import org.junit.Test

/**
 * buildHistory() 单元测试
 *
 * 覆盖 buildHistory 纯函数中历经多次 Bug fix 的三个关键路径：
 *  1. 知识隔离：群聊时角色只看自己的私聊 + 群聊消息 + 摘要
 *  2. 本轮去重：roundStartTimestamp 阻止本轮 assistant 消息双重注入
 *  3. 严格 API 兼容：连续同角色消息之间插入占位（user/user → 插入 assistant；
 *                    assistant/assistant → 插入 user）
 *
 * 所有测试均为纯 JVM 单元测试，无需 Android 运行时。
 */
class BuildHistoryTest {

    // ── 辅助构建函数 ─────────────────────────────────────────────────────────

    private fun makeChar(id: String) = NyxCharacter(
        id = id, name = "角色$id", initials = id.take(1), colorArgb = 0xFFFFFFFF,
        traits = "", style = "", background = ""
    )

    private fun msg(
        role: String,
        content: String,
        charId: String? = null,
        isGroup: Boolean = false,
        isSummary: Boolean = false,
        timestamp: Long = System.currentTimeMillis()
    ) = NyxMessage(
        id = java.util.UUID.randomUUID().toString(),
        role = role,
        charId = charId,
        content = content,
        timestamp = timestamp,
        isGroup = isGroup,
        isSummary = isSummary
    )

    private fun baseCtx(
        char: NyxCharacter,
        messages: List<NyxMessage>,
        knownCharIds: Set<String> = emptySet(),
        roundStartTimestamp: Long = 0L
    ) = PipelineContext(
        char = char,
        allChars = listOf(char),
        messages = messages,
        memories = emptyList(),
        worldBook = emptyList(),
        relationships = emptyList(),
        roundReplies = emptyList(),
        userInput = "",
        isGroupChat = knownCharIds.isNotEmpty(),
        knownCharIds = knownCharIds,
        roundStartTimestamp = roundStartTimestamp
    )

    // ── 测试 1：单聊模式——无知识隔离，历史全量返回 ─────────────────────────

    @Test
    fun `single chat returns all messages up to historyLimit`() {
        val char = makeChar("c1")
        val messages = (1..5).map { msg("user", "msg$it") } +
                       (1..5).map { msg("assistant", "reply$it", charId = "c1") }
        val ctx = baseCtx(char, messages).copy(historyLimit = 6)
        val history = buildHistory(ctx)
        // historyLimit=6，取最后 6 条
        assertEquals(6, history.size)
    }

    // ── 测试 2：知识隔离——群聊时角色只看自己 + 群聊消息 + 摘要 ──────────────

    @Test
    fun `group chat knowledge isolation filters out other char private messages`() {
        val charA = makeChar("cA")
        val charB = makeChar("cB")
        val messages = listOf(
            msg("user", "hello"),
            msg("assistant", "A reply", charId = "cA", isGroup = false),   // A 的私聊回复
            msg("assistant", "B reply", charId = "cB", isGroup = false),   // B 的私聊回复（A 不应看到）
            msg("assistant", "group reply", charId = "cA", isGroup = true) // 群聊消息（共享）
        )
        val ctx = baseCtx(charA, messages, knownCharIds = setOf("cA", "cB"))
        val history = buildHistory(ctx)
        val contents = history.map { it["content"] }
        // A 应看到：自己的私聊回复、群聊消息、用户消息；不应看到 B 的私聊回复
        assertTrue("A 应看到自己的私聊回复", contents.contains("A reply"))
        assertTrue("A 应看到群聊消息", contents.contains("group reply"))
        assertFalse("A 不应看到 B 的私聊回复", contents.contains("B reply"))
    }

    // ── 测试 3：摘要消息在群聊中对所有角色可见（Bug A fix） ────────────────

    @Test
    fun `summary messages are visible to all chars in group chat`() {
        val charA = makeChar("cA")
        val charB = makeChar("cB")
        val summaryMsg = msg("system", "本章小结内容", isSummary = true)
        val messages = listOf(
            summaryMsg,
            msg("user", "hello"),
            msg("assistant", "B private", charId = "cB", isGroup = false)
        )
        // 以 charA 视角，knownCharIds 包含两者
        val ctx = baseCtx(charA, messages, knownCharIds = setOf("cA", "cB"))
        val history = buildHistory(ctx)
        val contents = history.map { it["content"] }
        assertTrue("摘要应对所有角色可见", contents.contains("本章小结内容"))
    }

    // ── 测试 4：摘要 role 重映射为 "user"（API 兼容） ──────────────────────

    @Test
    fun `summary message role is remapped from system to user`() {
        val char = makeChar("c1")
        val summaryMsg = msg("system", "摘要", isSummary = true)
        val ctx = baseCtx(char, listOf(summaryMsg))
        val history = buildHistory(ctx)
        assertEquals("摘要消息必须重映射为 user role", "user", history.first()["role"])
    }

    // ── 测试 5：本轮去重——roundStartTimestamp 过滤本轮 assistant 消息（Bug 1 fix） ──

    @Test
    fun `round dedup filters current round assistant messages`() {
        val char = makeChar("c1")
        val t0 = 1000L
        val t1 = 2000L  // 本轮开始时间
        val messages = listOf(
            msg("user", "user msg", timestamp = t0),
            msg("assistant", "old reply", charId = "c1", timestamp = t0 + 100),  // 本轮前
            msg("assistant", "new reply", charId = "c1", timestamp = t1 + 100)   // 本轮后（应被过滤）
        )
        val ctx = baseCtx(char, messages, roundStartTimestamp = t1)
        val history = buildHistory(ctx)
        val contents = history.map { it["content"] }
        assertTrue("本轮前的 assistant 回复应保留", contents.contains("old reply"))
        assertFalse("本轮内的 assistant 回复应被过滤（已通过 group_context 注入）", contents.contains("new reply"))
    }

    // ── 测试 6：连续 user 消息之间插入 assistant 占位 ───────────────────────

    @Test
    fun `consecutive user messages get assistant placeholder inserted`() {
        val char = makeChar("c1")
        val messages = listOf(
            msg("user", "msg1"),
            msg("user", "msg2")   // 连续 user（摘要重映射后的典型场景）
        )
        val ctx = baseCtx(char, messages)
        val history = buildHistory(ctx)
        // 期望：user(msg1) → assistant(…) → user(msg2)
        assertEquals(3, history.size)
        assertEquals("user",      history[0]["role"])
        assertEquals("assistant", history[1]["role"])
        assertEquals("…",         history[1]["content"])
        assertEquals("user",      history[2]["role"])
    }

    // ── 测试 7：连续 assistant 消息之间插入 user 占位 ────────────────────────

    @Test
    fun `consecutive assistant messages get user placeholder inserted`() {
        val char = makeChar("c1")
        val messages = listOf(
            msg("user", "hi"),
            msg("assistant", "A says", charId = "cA"),
            msg("assistant", "B says", charId = "cB", isGroup = true)  // 群聊，两个助手连续
        )
        val ctx = baseCtx(char, messages)
        val history = buildHistory(ctx)
        // 期望：user(hi) → assistant(A says) → user(…) → assistant(B says)
        assertEquals(4, history.size)
        assertEquals("assistant", history[1]["role"])
        assertEquals("user",      history[2]["role"])
        assertEquals("…",         history[2]["content"])
        assertEquals("assistant", history[3]["role"])
    }

    // ── 测试 8：roundStartTimestamp == 0L 时用户和摘要不过滤 ────────────────

    @Test
    fun `non-group path with roundStartTimestamp zero does not filter any messages`() {
        val char = makeChar("c1")
        val t1 = 5000L
        val messages = listOf(
            msg("user", "user msg", timestamp = t1 + 10),
            msg("assistant", "reply", charId = "c1", timestamp = t1 + 20)
        )
        // roundStartTimestamp = 0L → 不过滤
        val ctx = baseCtx(char, messages, roundStartTimestamp = 0L)
        val history = buildHistory(ctx)
        assertEquals("非群聊路径所有消息应保留", 2, history.size)
    }
}

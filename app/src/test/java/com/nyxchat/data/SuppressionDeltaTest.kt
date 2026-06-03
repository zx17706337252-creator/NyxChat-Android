package com.nyxchat.data

import org.junit.Assert.*
import org.junit.Test

/**
 * RelationshipTracker 单元测试
 *
 * 覆盖改进后的 inferDelta / applyFriction 中新增的 suppression 自动步进逻辑：
 *  1. 释放信号（「想你」「在乎」等）→ suppressionDelta > 0（压抑松动）
 *  2. 压抑信号（「不想说」「随便」等）→ suppressionDelta < 0（压抑收紧）
 *  3. 无信号文本 → suppressionDelta ≈ 0
 *  4. 深层关系（stage=5）→ 步进幅度极小（摩擦 × 0.3 缩减）
 */
class SuppressionDeltaTest {

    private fun makeChar(id: String) = NyxCharacter(
        id = id, name = "角色$id", initials = id.take(1), colorArgb = 0xFFFFFFFF,
        traits = "", style = "", background = ""
    )

    private fun trackSingle(text: String, stage: Int = 0): Relationship? {
        val char = makeChar("c1")
        // 初始化一个指定 stage 的关系
        val existing = Relationship(
            id = "c1_user", fromCharId = "c1", toCharId = USER_PSEUDO_ID,
            trust = 0.5f, affection = 0.5f, stage = stage, suppression = 0.5f
        )
        val lines = listOf("用户：$text", "角色c1：好的")
        val result = RelationshipTracker.track(lines, listOf(char), listOf(existing))
        return result.find { it.fromCharId == "c1" && it.toCharId == USER_PSEUDO_ID }
    }

    @Test
    fun `release signals increase suppression`() {
        val rel = trackSingle("我真的很在乎你，想你，想靠近你")
        assertNotNull(rel)
        assertTrue(
            "释放信号应使 suppression 升高（压抑松动），实际值=${rel!!.suppression}",
            rel.suppression > 0.5f
        )
    }

    @Test
    fun `suppress signals decrease suppression`() {
        val rel = trackSingle("不想说，随便，算了，没事，别问了")
        assertNotNull(rel)
        assertTrue(
            "压抑信号应使 suppression 降低（压抑收紧），实际值=${rel!!.suppression}",
            rel.suppression < 0.5f
        )
    }

    @Test
    fun `neutral text produces near-zero suppression delta`() {
        val rel = trackSingle("今天天气不错。")
        assertNotNull(rel)
        // 无信号时 delta ≈ 0，suppression 几乎不变（允许浮点误差 0.005）
        assertEquals("无信号时 suppression 应接近不变", 0.5f, rel!!.suppression, 0.005f)
    }

    @Test
    fun `deep relationship stage 5 produces very small suppression delta`() {
        val relStage0 = trackSingle("我真的很在乎你，想你，靠近你，需要你", stage = 0)
        val relStage5 = trackSingle("我真的很在乎你，想你，靠近你，需要你", stage = 5)
        assertNotNull(relStage0)
        assertNotNull(relStage5)
        val delta0 = relStage0!!.suppression - 0.5f
        val delta5 = relStage5!!.suppression - 0.5f
        assertTrue(
            "stage=5 的 suppression 步进应远小于 stage=0（摩擦缩减），delta0=$delta0, delta5=$delta5",
            kotlin.math.abs(delta5) < kotlin.math.abs(delta0)
        )
    }
}

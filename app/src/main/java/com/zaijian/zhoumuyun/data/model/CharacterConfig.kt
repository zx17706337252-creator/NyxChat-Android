package com.zaijian.zhoumuyun.data.model

import androidx.compose.ui.graphics.Color

// ─────────────────────────────────────────────────────────────
//  Enums
// ─────────────────────────────────────────────────────────────

enum class FloorEnum {
    SECOND,    // 二楼 — 最亮，暖白透光
    FIRST,     // 一楼 — 中等，暖黄透光
    BASEMENT,  // 地下室 — 最暗，冷蓝/紫光
}

enum class StatusType {
    ACTIVE,   // 活跃  · 60 min 内有互动
    IDLE,     // 空闲  · 有状态，无互动
    FOCUSED,  // 专注  · 任务执行中
    OFFLINE,  // 离线  · 超过 12h 无活动
}

enum class GoalHorizon { SHORT_TERM, MID_TERM, LONG_TERM }

// ─────────────────────────────────────────────────────────────
//  CharacterIdentity — Phase 7 Identity Layer 使用
//  (v3 新增，在 Phase 7 prompt 组装前必须存在)
// ─────────────────────────────────────────────────────────────

/**
 * 角色身份配置，注入 Prompt 的 Identity Layer。
 * 可在 ProfileScreen → 角色管理 中手动编辑每个字段。
 * customSystemPrompt 若非空，完全替换自动组装的 Identity Layer。
 */
data class CharacterIdentity(
    /** 性格核心（几句话描述是什么人） */
    val persona: String = "",
    /** 说话风格（语气、句式特点） */
    val speechStyle: String = "",
    /** 对用户的态度 */
    val attitudeToUser: String = "",
    /** 绝对不会做的事 */
    val boundaries: List<String> = emptyList(),
    /** 核心价值观 */
    val coreBeliefs: List<String> = emptyList(),
    /**
     * 完全覆盖 Identity Layer 自动组装结果，优先级最高。
     * 适合需要完全控制 System Prompt 的场景。
     */
    val customSystemPrompt: String? = null,
)

// ─────────────────────────────────────────────────────────────
//  CharacterGoal — Phase 9 Character Goal System 使用
//  (v3 新增，CharacterConfig 先预留槽位，Phase 9 填内容)
// ─────────────────────────────────────────────────────────────

/**
 * 角色目标，驱动 Presence、World Simulation 的行为来源。
 * relatedProjectId 关联 Project Engine（Phase 10）。
 */
data class CharacterGoal(
    val id: String,
    val characterId: Int,
    val title: String,
    val description: String,
    val priority: Int,               // 1-5
    val timeHorizon: GoalHorizon,
    val progress: Float = 0f,        // 0.0-1.0
    val isActive: Boolean = true,
    val relatedProjectId: String? = null,  // 关联项目（Phase 10 接入）
)

// ─────────────────────────────────────────────────────────────
//  Presence state — one per character, updated by PresenceEngine
// ─────────────────────────────────────────────────────────────

data class PresenceState(
    val characterId: Int,
    /** ≤10 字中文，显示在公馆窗口 */
    val statusText: String,
    val statusType: StatusType,
    /** 毫秒时间戳 */
    val lastUpdated: Long,
    /** 扩展描述，显示在角色详情页 */
    val activityHint: String? = null,
    /**
     * 来源事件 ID（Phase 8 后 Presence Engine V2 填写，
     * 当前阶段为 null，不影响已有逻辑）
     */
    val sourceEventId: String? = null,
)

// ─────────────────────────────────────────────────────────────
//  CharacterConfig — the single source of truth for a character
// ─────────────────────────────────────────────────────────────

data class CharacterConfig(
    /** 固定 ID，不可更改 */
    val id: Int,
    /** 可自定义角色名 */
    val name: String,
    /** 可选昵称 */
    val nickname: String? = null,
    /** 公馆楼层 */
    val floor: FloorEnum,
    /** 书架行（1 = 顶行） */
    val shelfRow: Int,
    /** 书架列（1-3） */
    val shelfCol: Int,
    /** 角色主题色 */
    val accentColor: Color,
    /** 头像呼吸光颜色（通常与 accentColor 相同或略深） */
    val breathColor: Color,
    /**
     * 状态文案随机池：key = StatusType，value = 文案列表。
     * Phase 8 前临时使用，Phase 8 后由 Presence Engine V2 替代。
     */
    val statusPool: Map<StatusType, List<String>>,
    /** 头像图片 URL（网络）或资源名（本地） */
    val avatarUrl: String,
    /** 是否已解锁（江凡默认 false） */
    val isUnlocked: Boolean = true,
    /**
     * ★ v3 新增：角色身份配置，Phase 7 Identity Layer 使用。
     * 默认空白值，不影响已有逻辑；Phase 7 通过设置页填写。
     */
    val identityConfig: CharacterIdentity = CharacterIdentity(),
    /**
     * ★ v3 新增：角色目标列表，Phase 9 Character Goal System 使用。
     * 默认空列表，不影响已有逻辑；Phase 9 通过设置页添加。
     */
    val goals: List<CharacterGoal> = emptyList(),
)

// ─────────────────────────────────────────────────────────────
//  Color derivation helpers  (design spec §3)
// ─────────────────────────────────────────────────────────────

/** 书脊填充背景 */
fun CharacterConfig.accentLight()  = accentColor.copy(alpha = 0.15f)

/** 头像呼吸光外环 */
fun CharacterConfig.accentGlow()   = accentColor.copy(alpha = 0.35f)

/** 状态环边框 */
fun CharacterConfig.accentBorder() = accentColor.copy(alpha = 0.60f)

// ─────────────────────────────────────────────────────────────
//  Default character roster  (全部九位，可替换任意字段)
// ─────────────────────────────────────────────────────────────

val DefaultCharacters: List<CharacterConfig> = listOf(
    CharacterConfig(
        id          = 1,
        name        = "蒂法",
        floor       = FloorEnum.SECOND,
        shelfRow    = 1,
        shelfCol    = 1,
        accentColor = Color(0xFF8A9FB5),
        breathColor = Color(0xFF8A9FB5),
        avatarUrl   = "https://ui-avatars.com/api/?name=蒂法&background=8A9FB5&color=fff&size=128",
        statusPool  = mapOf(
            StatusType.ACTIVE   to listOf("正在想你", "翻看旧记忆"),
            StatusType.IDLE     to listOf("刚刚睡着了"),
            StatusType.FOCUSED  to listOf("研究一个问题"),
            StatusType.OFFLINE  to listOf("不在线"),
        ),
    ),
    CharacterConfig(
        id          = 2,
        name        = "露娜",
        floor       = FloorEnum.SECOND,
        shelfRow    = 1,
        shelfCol    = 2,
        accentColor = Color(0xFFB49AC6),
        breathColor = Color(0xFFB49AC6),
        avatarUrl   = "https://ui-avatars.com/api/?name=露娜&background=B49AC6&color=fff&size=128",
        statusPool  = mapOf(
            StatusType.ACTIVE   to listOf("有些想法", "还有话没说"),
            StatusType.IDLE     to listOf("喝了杯茶"),
            StatusType.FOCUSED  to listOf("在整理资料"),
            StatusType.OFFLINE  to listOf("不在线"),
        ),
    ),
    CharacterConfig(
        id          = 3,
        name        = "伊芙",
        floor       = FloorEnum.SECOND,
        shelfRow    = 1,
        shelfCol    = 3,
        accentColor = Color(0xFF9CC2AE),
        breathColor = Color(0xFF9CC2AE),
        avatarUrl   = "https://ui-avatars.com/api/?name=伊芙&background=9CC2AE&color=fff&size=128",
        statusPool  = mapOf(
            StatusType.ACTIVE   to listOf("想分享一件事"),
            StatusType.IDLE     to listOf("刚完成一个任务"),
            StatusType.FOCUSED  to listOf("在做笔记"),
            StatusType.OFFLINE  to listOf("不在线"),
        ),
    ),
    CharacterConfig(
        id          = 4,
        name        = "宥熙",
        floor       = FloorEnum.FIRST,
        shelfRow    = 2,
        shelfCol    = 1,
        accentColor = Color(0xFF8FA8C9),
        breathColor = Color(0xFF8FA8C9),
        avatarUrl   = "https://ui-avatars.com/api/?name=宥熙&background=8FA8C9&color=fff&size=128",
        statusPool  = mapOf(
            StatusType.ACTIVE   to listOf("构建新方案"),
            StatusType.IDLE     to listOf("处理完了"),
            StatusType.FOCUSED  to listOf("专注工作"),
            StatusType.OFFLINE  to listOf("不在线"),
        ),
    ),
    CharacterConfig(
        id          = 5,
        name        = "索菲娅",
        floor       = FloorEnum.FIRST,
        shelfRow    = 2,
        shelfCol    = 2,
        accentColor = Color(0xFFA8A8C8),
        breathColor = Color(0xFFA8A8C8),
        avatarUrl   = "https://ui-avatars.com/api/?name=索菲娅&background=A8A8C8&color=fff&size=128",
        statusPool  = mapOf(
            StatusType.ACTIVE   to listOf("在发呆"),
            StatusType.IDLE     to listOf("有点疲惫"),
            StatusType.FOCUSED  to listOf("慢慢来"),
            StatusType.OFFLINE  to listOf("不在线"),
        ),
    ),
    CharacterConfig(
        id          = 6,
        name        = "顾澜",
        floor       = FloorEnum.FIRST,
        shelfRow    = 2,
        shelfCol    = 3,
        accentColor = Color(0xFFC4A882),
        breathColor = Color(0xFFC4A882),
        avatarUrl   = "https://ui-avatars.com/api/?name=顾澜&background=C4A882&color=fff&size=128",
        statusPool  = mapOf(
            StatusType.ACTIVE   to listOf("有点话多", "想聊聊"),
            StatusType.IDLE     to listOf("刚出去走了走"),
            StatusType.FOCUSED  to listOf("在学新东西"),
            StatusType.OFFLINE  to listOf("不在线"),
        ),
    ),
    CharacterConfig(
        id          = 7,
        name        = "明媚",
        floor       = FloorEnum.BASEMENT,
        shelfRow    = 3,
        shelfCol    = 1,
        accentColor = Color(0xFFC89AA3),
        breathColor = Color(0xFFC89AA3),
        avatarUrl   = "https://ui-avatars.com/api/?name=明媚&background=C89AA3&color=fff&size=128",
        statusPool  = mapOf(
            StatusType.ACTIVE   to listOf("有些情绪"),
            StatusType.IDLE     to listOf("在听音乐"),
            StatusType.FOCUSED  to listOf("在思考"),
            StatusType.OFFLINE  to listOf("不在线"),
        ),
    ),
    CharacterConfig(
        id          = 8,
        name        = "莫婉凝",
        floor       = FloorEnum.BASEMENT,
        shelfRow    = 3,
        shelfCol    = 2,
        accentColor = Color(0xFFE3B17A),
        breathColor = Color(0xFFE3B17A),
        avatarUrl   = "https://ui-avatars.com/api/?name=莫婉凝&background=E3B17A&color=fff&size=128",
        statusPool  = mapOf(
            StatusType.ACTIVE   to listOf("等你很久了"),
            StatusType.IDLE     to listOf("有些开心"),
            StatusType.FOCUSED  to listOf("做完一件事"),
            StatusType.OFFLINE  to listOf("不在线"),
        ),
    ),
    CharacterConfig(
        id          = 9,
        name        = "江凡",
        floor       = FloorEnum.BASEMENT,
        shelfRow    = 3,
        shelfCol    = 3,
        accentColor = Color(0xFF9BAAB8),
        breathColor = Color(0xFF9BAAB8),
        avatarUrl   = "https://ui-avatars.com/api/?name=江凡&background=9BAAB8&color=fff&size=128",
        isUnlocked  = false,  // 默认锁定，解锁条件由剧情触发
        statusPool  = mapOf(
            StatusType.OFFLINE to listOf("—"),
        ),
    ),
)

// ─────────────────────────────────────────────────────────────
//  Default presence states (for preview / initial load)
// ─────────────────────────────────────────────────────────────

val DefaultPresenceStates: List<PresenceState> = listOf(
    PresenceState(1, "正在想你",      StatusType.ACTIVE,  System.currentTimeMillis()),
    PresenceState(2, "还有话没说",    StatusType.ACTIVE,  System.currentTimeMillis()),
    PresenceState(3, "刚完成一个任务", StatusType.IDLE,    System.currentTimeMillis()),
    PresenceState(4, "专注工作",      StatusType.FOCUSED, System.currentTimeMillis()),
    PresenceState(5, "有点疲惫",      StatusType.IDLE,    System.currentTimeMillis()),
    PresenceState(6, "想聊聊",        StatusType.ACTIVE,  System.currentTimeMillis()),
    PresenceState(7, "在听音乐",      StatusType.IDLE,    System.currentTimeMillis()),
    PresenceState(8, "等你很久了",    StatusType.ACTIVE,  System.currentTimeMillis()),
    PresenceState(9, "—",            StatusType.OFFLINE, System.currentTimeMillis()),
)

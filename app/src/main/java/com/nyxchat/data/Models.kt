package com.nyxchat.data

import androidx.compose.ui.graphics.Color

// ─── User Persona ──────────────────────────────────────────────────────────────
data class UserPersona(
    val name        : String = "",   // 角色用这个名字称呼你
    val description : String = ""    // 简短特征，注入 system prompt
)

// ─── Scene State ───────────────────────────────────────────────────────────────
data class SceneState(
    val enabled     : Boolean = false,
    val location    : String  = "",  // 地点，如「图书馆」「雨天阳台」
    val atmosphere  : String  = ""   // 氛围，如「安静」「深夜」「紧张」
)

// ─── Enums ───────────────────────────────────────────────────────────────────

enum class MatchMode { ANY, ALL, NOT, REGEX }

// ─── Reply Length ─────────────────────────────────────────────────────────────
// 角色级回复长度配置，同时控制 output_directive 字数指令和 max_tokens。
// Phase 2 会在角色编辑页加选择器；Phase 1 默认 Medium，行为与之前的 120字 硬编码接近。
enum class ReplyLength(
    val label: String,
    val charsLimit: String,    // 注入 prompt 的字数指令文本
    val maxTokens: Int         // 对应 streamAI 的 max_tokens 参数
) {
    Short ("短",   "不超过80字",   400),
    Medium("中",   "不超过150字",  700),
    Long  ("长",   "不超过300字",  1400),
    Free  ("自由", "篇幅自由发挥", 2000)
}

// ─── Core Models ─────────────────────────────────────────────────────────────

data class ProactiveConfig(
    val enabled: Boolean = false,
    val minIntervalMinutes: Int = 240,
    val maxIntervalMinutes: Int = 720,
    val activeStart: Int = 8,
    val activeEnd: Int = 22,
    val customPrompt: String = ""
)

data class NyxCharacter(
    val id: String,
    val name: String,
    val initials: String,
    val colorArgb: Long,
    // Visuals
    val avatarPath: String = "",
    val backgroundPath: String = "",
    // Basic info
    val age: String = "",
    val nationality: String = "",
    val occupation: String = "",
    val height: String = "",
    val weight: String = "",
    val appearance: String = "",
    // Personality
    val traits: String,
    val style: String,
    val background: String,
    val relationships: String = "",
    val likes: String = "",
    val dislikes: String = "",
    val secrets: String = "",
    // ── 三层角色结构：内核字段（Layer 2） ─────────────────────────────────
    // Layer 1（公开）：traits / style / speakingExamples 复用现有字段
    // Layer 2（内核）：以下新增，描述面具下的真实
    val coreWound       : String = "",  // 核心创伤：驱动公开面具的根本原因
    val coreDesire      : String = "",  // 核心渴望：她真正想要的，绝不轻易说出口
    val maskTrigger     : String = "",  // 触发条件：什么情况会让面具松动或崩碎
    val privatePersona  : String = "",  // 私下真实：面具碎裂后的样子（一句话）
    val privateStyle    : String = "",  // 私下说话方式：语气、节奏、温度的变化规则
    val privateExamples : String = "",  // 私下示例：破防/激活状态的Few-shot对话
    val situationRules  : String = "",  // 情境反应规则：不同触发情境对应的具体行为模式
    val deviationSignals: String = "",  // 有心事时的外显行为信号，区别于正常状态的细节
    val speakingExamples: String = "",
    // Constraints (Persona Constraint stage)
    val constraints: String = "",
    // Behavior
    val greeting: String = "",
    val temperature: Float = 0.85f,
    // Voice
    val voiceId: String = "zh-CN-XiaoxiaoNeural",
    // Proactive messaging
    val proactiveConfig: ProactiveConfig = ProactiveConfig(),
    // State
    val mood: String = "neutral",
    val targetMood: String = "",           // 步骤10a：AI 设定的目标情绪（空表示与 mood 相同）
    val emotionalStability: Float = 0.7f,  // 步骤10b：情绪稳定性 0=极不稳定,1=极稳定；CharactersScreen 可调
    val isActive: Boolean = true,
    val lastActiveAt: Long = 0L, // timestamp of last activity
    val birthday: String = "",        // MM-dd 格式，当天触发生日消息
    // Batch 4 Item 11: 与用户的关系类型标签（"同学"/"同事"/"主仆"/"恋人"/"家人"/"自定义文本"）
    val relationType: String = "",
    // Phase 1: 角色级回复长度，控制字数指令和 max_tokens；默认 Medium（不超过150字）
    val replyLength: ReplyLength = ReplyLength.Medium
) {
    val color: Color get() = Color(colorArgb)
    val hasAvatar: Boolean get() = avatarPath.isNotBlank()
    val hasBackground: Boolean get() = backgroundPath.isNotBlank()
}

data class NyxMessage(
    val id: String,
    val role: String,
    val charId: String? = null,
    val content: String,
    val timestamp: Long,
    val sessionId: String = "default",
    val isGroup: Boolean = false,        // false = private single-chat; true = group context shared with all chars
    val isSummary: Boolean = false       // true = 历史摘要（由自动压缩生成）
)

data class ChatSession(
    val id: String,
    val title: String,
    val characterIds: List<String> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
    val lastMessageAt: Long = System.currentTimeMillis()
)

// ─── 步骤13：记忆类型 ──────────────────────────────────────────────────────────
// Public          → 注入所有角色（向后兼容默认值）
// Private         → 仅注入归属角色（mem.charId == ctx.char.id）
// HiddenInference → 仅注入归属角色，内容前缀加"（我的猜测）"
// Sensitive       → 注入权重系数提高，衰减后仍优先保留
enum class MemoryType { Public, Private, HiddenInference, Sensitive }

data class NyxMemory(
    val id: String,
    val charId: String,
    val content: String,
    val importance: Int,
    val timestamp: Long,
    val type: String = "Public"   // MemoryType.name，存 String 方便 Room 序列化
)

/** 世界书条目从未被触发过时的哨兵值（区别于索引 0 表示"第0条消息触发"）*/
const val WB_ENTRY_NEVER_TRIGGERED = -999

data class WorldBookEntry(
    val id: String,
    val title: String,
    val content: String,
    val keywords: List<String>,
    val matchMode: MatchMode = MatchMode.ANY,
    val cooldownMsgs: Int = 0,
    val lastTriggeredMsgIndex: Int = WB_ENTRY_NEVER_TRIGGERED,
    val priority: Int = 0,
    val enabled: Boolean = true,
    val sticky: Boolean = false   // 常驻注入，不受关键词限制，不占 maxEntries 配额
)

// ─── API 厂商模型 ─────────────────────────────────────────────────────────────

data class ModelOption(
    val modelId: String,          // 实际传 API 的字符串
    val displayName: String,      // UI 显示名（如 "V4 Flash（性价比）"）
    val historyLimit: Int,        // 该模型保留历史条数
    val maxOutputTokens: Int = 4096  // 该模型单次输出 token 上限，默认 4096（保守值）
)

data class ApiProvider(
    val id: String,
    val displayName: String,
    val baseUrl: String,
    val models: List<ModelOption>,
    val isCustom: Boolean = false
)

/**
 * 安全 max_tokens：取 [requested] 与当前模型 [maxOutputTokens] 的较小值，
 * 防止请求值超出模型上限导致 API 400 错误。
 * 找不到模型配置时保守取 min(requested, 4096)。
 */
fun ApiConfig.safeMaxTokens(requested: Int): Int {
    val modelOpt = API_PROVIDERS
        .firstOrNull { it.id == providerName }
        ?.models?.firstOrNull { it.modelId == model }
    val ceiling = modelOpt?.maxOutputTokens ?: 4096
    return minOf(requested, ceiling)
}

val API_PROVIDERS = listOf(

    // ── DeepSeek 官方 ─────────────────────────────────────────────────
    // 官方 API 文档：api-docs.deepseek.com
    // 注：deepseek-chat / deepseek-reasoner 将于 2026-07-24 停用，
    //     现已分别指向 deepseek-v4-flash 的非思考/思考模式。
    ApiProvider(
        id = "deepseek", displayName = "DeepSeek",
        baseUrl = "https://api.deepseek.com/v1",
        models = listOf(
            ModelOption("deepseek-v4-flash", "V4 Flash（性价比·推荐）", 100, maxOutputTokens = 8192),
            ModelOption("deepseek-v4-pro",   "V4 Pro（旗舰·强推理）",   100, maxOutputTokens = 8192),
        )
    ),

    // ── 火山方舟 ──────────────────────────────────────────────────────
    // 火山方舟 2026-05-18 上架 DeepSeek-V4 系列。
    // DeepSeek 模型需在控制台「模型推理」→「接入点」创建后，
    // 将 ep-xxxxxxxx 格式 ID 填入模型名称框（下方 Flash/Pro 为参考名，需替换）。
    // Doubao 系列同理需创建接入点。
    ApiProvider(
        id = "ark", displayName = "火山方舟",
        baseUrl = "https://ark.cn-beijing.volces.com/api/v3",
        models = listOf(
            ModelOption("deepseek-v4-flash", "DeepSeek V4 Flash（需替换为 ep-ID）", 100, maxOutputTokens = 8192),
            ModelOption("deepseek-v4-pro",   "DeepSeek V4 Pro（需替换为 ep-ID）",   100, maxOutputTokens = 8192),
            ModelOption("doubao-1-5-pro-32k",  "Doubao 1.5 Pro 32k（需替换为 ep-ID）",  80, maxOutputTokens = 4096),
            ModelOption("doubao-1-5-pro-256k", "Doubao 1.5 Pro 256k（长文·需替换）",    120, maxOutputTokens = 4096),
        )
    ),

    // ── 腾讯云（lkeap）────────────────────────────────────────────────
    // 接口域名：api.lkeap.cloud.tencent.com/v1（OpenAI 兼容）
    // 控制台：cloud.tencent.com → 知识引擎原子能力
    ApiProvider(
        id = "tencent", displayName = "腾讯云",
        baseUrl = "https://api.lkeap.cloud.tencent.com/v1",
        models = listOf(
            ModelOption("deepseek-v4-flash", "DeepSeek V4 Flash（推荐）", 100, maxOutputTokens = 8192),
            ModelOption("deepseek-v4-pro",   "DeepSeek V4 Pro",           100, maxOutputTokens = 8192),
            ModelOption("deepseek-v3.2",     "DeepSeek V3.2",              80, maxOutputTokens = 8192),
        )
    ),

    // ── 阿里云百炼 ────────────────────────────────────────────────────
    // 官方文档：help.aliyun.com/zh/model-studio/deepseek-api
    // DeepSeek V4 系列与官网同价，支持思考/非思考双模式。
    ApiProvider(
        id = "bailian", displayName = "阿里云百炼",
        baseUrl = "https://dashscope.aliyuncs.com/compatible-mode/v1",
        models = listOf(
            ModelOption("deepseek-v4-flash", "DeepSeek V4 Flash（推荐）", 100, maxOutputTokens = 8192),
            ModelOption("deepseek-v4-pro",   "DeepSeek V4 Pro",           100, maxOutputTokens = 8192),
            ModelOption("qwen-max",          "Qwen Max（顶级）",            90, maxOutputTokens = 2000),
            ModelOption("qwen3-235b-a22b",   "Qwen3-235B（思考旗舰）",      90, maxOutputTokens = 16000),
            ModelOption("qwen3-32b",         "Qwen3-32B（思考均衡）",        80, maxOutputTokens = 16000),
            ModelOption("qwen-turbo",        "Qwen Turbo（性价比）",         60, maxOutputTokens = 1500),
            ModelOption("qwen-long",         "Qwen Long（超长文）",         120, maxOutputTokens = 2000),
        )
    ),

    // ── 魔塔 ModelScope ───────────────────────────────────────────────
    // 模型 ID 格式固定为「组织/模型名」，大小写敏感，需与 ModelScope 平台一致。
    ApiProvider(
        id = "modelscope", displayName = "魔塔",
        baseUrl = "https://api-inference.modelscope.cn/v1",
        models = listOf(
            ModelOption("deepseek-ai/DeepSeek-V4-Flash", "DeepSeek V4 Flash（推荐）", 100, maxOutputTokens = 8192),
            ModelOption("deepseek-ai/DeepSeek-V4-Pro",   "DeepSeek V4 Pro",           100, maxOutputTokens = 8192),
            ModelOption("Qwen/Qwen3-235B-A22B",          "Qwen3-235B（思考旗舰）",     90, maxOutputTokens = 16000),
            ModelOption("Qwen/Qwen3-32B",                "Qwen3-32B（思考均衡）",       80, maxOutputTokens = 16000),
            ModelOption("Qwen/Qwen2.5-72B-Instruct",     "Qwen2.5-72B",                80, maxOutputTokens = 2000),
        )
    ),

    // ── 自定义 ────────────────────────────────────────────────────────
    ApiProvider(
        id = "custom", displayName = "自定义",
        baseUrl = "",
        models = emptyList(),
        isCustom = true
    ),
)

data class ApiConfig(
    val apiKey: String = "",
    val baseUrl: String = "https://api.deepseek.com/v1",
    val model: String = "deepseek-v4-flash",
    val providerName: String = "deepseek",  // 当前选中厂商 id；空字符串 = 旧数据兼容（降级到自定义模式）
    val historyLimit: Int? = null    // null = 走字符串兜底（仅自定义厂商用）
)

// ─── TTS ─────────────────────────────────────────────────────────────────────

enum class TtsProvider { Azure, AlibabaCloud, Volcengine }

data class TtsConfig(
    val enabled:             Boolean     = false,
    val provider:            TtsProvider = TtsProvider.Azure,

    // Azure（原有字段保留）
    val subscriptionKey:     String  = "",
    val region:              String  = "eastasia",

    // 阿里云百炼 CosyVoice（新增）
    val alibabaDashscopeKey: String  = "",
    val alibabaVoiceId:      String  = "longxiaochun",

    // 火山方舟 TTS（新增）
    val volcengineAppId:     String  = "",
    val volcengineToken:     String  = "",
    val volcengineVoiceType: String  = "zh_female_wanwanxiaohe_moon_bigtts",

    // 通用
    val autoPlay:            Boolean = false,
    val autoReadProactive:   Boolean = false
)

// ─── Mood ────────────────────────────────────────────────────────────────────

data class MoodMeta(val colorArgb: Long, val label: String) {
    val color: Color get() = Color(colorArgb)
}

val MOOD_META = mapOf(
    "neutral"      to MoodMeta(0xFFB09EFF, "平静"),
    "happy"        to MoodMeta(0xFFFBBF24, "愉悦"),
    "sad"          to MoodMeta(0xFF7DD3FC, "落寞"),
    "curious"      to MoodMeta(0xFF34D399, "好奇"),
    "cold"         to MoodMeta(0xFF94A3B8, "疏离"),
    "affectionate" to MoodMeta(0xFFF472B6, "温情"),
    "angry"        to MoodMeta(0xFFF87171, "激动"),
)

fun moodColor(mood: String) = MOOD_META[mood]?.color ?: Color(0xFFB09EFF)
fun moodLabel(mood: String) = MOOD_META[mood]?.label ?: "平静"

// ─── 步骤12：关系事件日志条目 ─────────────────────────────────────────────────
data class RelationshipLogEntry(
    val ts:       Long   = System.currentTimeMillis(),  // 时间戳
    val charId:   String = "",                          // 被追踪角色（fromCharId）
    // Bug 4 fix: 新增 toCharId 字段，区分「用户↔角色」和「角色A↔角色B」两种日志。
    // 旧数据 toCharId 为空字符串，兼容处理：空字符串表示来源不明，不参与过滤。
    val toCharId: String = "",                          // 关系对端 ID（USER_PSEUDO_ID 或另一角色 ID）
    val dim:      String = "",                          // 变化维度名称（trust / affection 等）
    val delta:    Float  = 0f,                          // 实际变化量
    val summary:  String = ""                           // 简要描述，沿用维度名
)

// ─── Defaults ────────────────────────────────────────────────────────────────

val DEFAULT_CHARACTERS = listOf(
    NyxCharacter(
        id = "c1", name = "蒂法", initials = "T", colorArgb = 0xFFB09EFF,
        age = "", nationality = "", height = "", weight = "",
        appearance = "", traits = "", style = "", background = "",
        relationships = "", likes = "", dislikes = "", secrets = "",
        speakingExamples = "", constraints = "", greeting = "", voiceId = "zh-CN-XiaoxiaoNeural",
        temperature = 0.85f, mood = "neutral"
    ),
    NyxCharacter(
        id = "c2", name = "露娜", initials = "L", colorArgb = 0xFFF472B6,
        age = "", nationality = "", height = "", weight = "",
        appearance = "", traits = "", style = "", background = "",
        relationships = "", likes = "", dislikes = "", secrets = "",
        speakingExamples = "", constraints = "", greeting = "", voiceId = "zh-CN-XiaoxiaoNeural",
        temperature = 0.85f, mood = "neutral"
    ),
    NyxCharacter(
        id = "c3", name = "伊芙", initials = "E", colorArgb = 0xFF34D399,
        age = "", nationality = "", height = "", weight = "",
        appearance = "", traits = "", style = "", background = "",
        relationships = "", likes = "", dislikes = "", secrets = "",
        speakingExamples = "", constraints = "", greeting = "", voiceId = "zh-CN-XiaoxiaoNeural",
        temperature = 0.85f, mood = "neutral"
    ),
    NyxCharacter(
        id = "c4", name = "宥熙", initials = "Y", colorArgb = 0xFFFBBF24,
        age = "", nationality = "", height = "", weight = "",
        appearance = "", traits = "", style = "", background = "",
        relationships = "", likes = "", dislikes = "", secrets = "",
        speakingExamples = "", constraints = "", greeting = "", voiceId = "zh-CN-XiaoxiaoNeural",
        temperature = 0.85f, mood = "neutral"
    ),
    NyxCharacter(
        id = "c5", name = "索菲娅", initials = "S", colorArgb = 0xFF7DD3FC,
        age = "", nationality = "", height = "", weight = "",
        appearance = "", traits = "", style = "", background = "",
        relationships = "", likes = "", dislikes = "", secrets = "",
        speakingExamples = "", constraints = "", greeting = "", voiceId = "zh-CN-XiaoxiaoNeural",
        temperature = 0.85f, mood = "neutral"
    ),
    NyxCharacter(
        id = "c6", name = "顾澜", initials = "G", colorArgb = 0xFFF87171,
        age = "", nationality = "", height = "", weight = "",
        appearance = "", traits = "", style = "", background = "",
        relationships = "", likes = "", dislikes = "", secrets = "",
        speakingExamples = "", constraints = "", greeting = "", voiceId = "zh-CN-XiaoxiaoNeural",
        temperature = 0.85f, mood = "neutral"
    ),
    NyxCharacter(
        id = "c7", name = "明媚", initials = "M", colorArgb = 0xFFA78BFA,
        age = "", nationality = "", height = "", weight = "",
        appearance = "", traits = "", style = "", background = "",
        relationships = "", likes = "", dislikes = "", secrets = "",
        speakingExamples = "", constraints = "", greeting = "", voiceId = "zh-CN-XiaoxiaoNeural",
        temperature = 0.85f, mood = "neutral"
    ),
    NyxCharacter(
        id = "c8", name = "莫婉凝", initials = "W", colorArgb = 0xFF94A3B8,
        age = "", nationality = "", height = "", weight = "",
        appearance = "", traits = "", style = "", background = "",
        relationships = "", likes = "", dislikes = "", secrets = "",
        speakingExamples = "", constraints = "", greeting = "", voiceId = "zh-CN-XiaoxiaoNeural",
        temperature = 0.85f, mood = "neutral"
    ),
    NyxCharacter(
        id = "c9", name = "江凡", initials = "J", colorArgb = 0xFF2DD4BF,
        age = "", nationality = "", height = "", weight = "",
        appearance = "", traits = "", style = "", background = "",
        relationships = "", likes = "", dislikes = "", secrets = "",
        speakingExamples = "", constraints = "", greeting = "", voiceId = "zh-CN-XiaoxiaoNeural",
        temperature = 0.85f, mood = "neutral"
    )
)

val DEFAULT_WORLD_BOOK = listOf(
    WorldBookEntry(
        id = "wb1", title = "场景设定",
        content = "故事发生在一座永远入夜的都市。霓虹与阴影并存，规则在这里只是建议。",
        keywords = listOf("城市", "这里", "地方", "外面", "街上"),
        matchMode = MatchMode.ANY, priority = 10
    )
)

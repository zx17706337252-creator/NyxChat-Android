package com.zaijian.zhoumuyun.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.zaijian.zhoumuyun.data.db.AppDatabase
import com.zaijian.zhoumuyun.data.db.entity.EventType
import com.zaijian.zhoumuyun.data.model.CharacterConfig
import com.zaijian.zhoumuyun.data.model.DefaultCharacters
import com.zaijian.zhoumuyun.data.model.DefaultPresenceStates
import com.zaijian.zhoumuyun.data.model.PresenceState
import com.zaijian.zhoumuyun.data.model.StatusType
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.json.JSONObject

// ─────────────────────────────────────────────────────────────
//  WorldUiState
// ─────────────────────────────────────────────────────────────

data class WorldUiState(
    val characters: List<CharacterConfig> = DefaultCharacters,
    val presenceMap: Map<Int, PresenceState> = DefaultPresenceStates.associateBy { it.characterId },
    val previewCharacterId: Int? = null,
    val showOnboardingTooltip: Boolean = false,
)

// ─────────────────────────────────────────────────────────────
//  PresenceViewModel（Phase 7 升级）
//
//  Presence 更新策略（双轨并行）：
//
//  轨道 A — Event 驱动（真实数据）：
//    observeLatest() 监听 world_events 表，
//    遇到 PRESENCE_CHANGED 事件立即更新对应角色的 presenceMap。
//    状态文案来自「刚聊过天」等语义化文本，而非随机池。
//
//  轨道 B — 时间衰减（兜底逻辑）：
//    若某角色超过 IDLE_THRESHOLD_MS（30 分钟）没有 PRESENCE_CHANGED 事件，
//    自动降级到 IDLE，表现为"不知在做什么"。
//    每 5 分钟轮询一次（Demo 模式 30 秒）。
//
//  这样公馆窗口的状态文案会真正反映"最近聊过天"，而不是随机文字。
// ─────────────────────────────────────────────────────────────

private val ACTIVE_STATUS_TEXTS = listOf(
    "刚聊过天",
    "刚回了消息",
    "还在想刚才说的话",
)
private val IDLE_STATUS_TEXTS = listOf(
    "不知在做什么",
    "最近有点安静",
    "没什么动静",
)
private const val IDLE_THRESHOLD_MS = 30 * 60 * 1000L  // 30 分钟
private const val DECAY_CHECK_INTERVAL_MS = 30_000L     // Demo: 30 秒；生产改 5 分钟

class PresenceViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getInstance(application)
    private val eventDao = db.worldEventDao()

    private val _uiState = MutableStateFlow(WorldUiState())
    val uiState: StateFlow<WorldUiState> = _uiState.asStateFlow()

    // 记录每个角色最后一次 PRESENCE_CHANGED 事件的时间戳（内存缓存）
    private val lastEventTime = mutableMapOf<Int, Long>()

    private var hasSeenOnboarding = false

    init {
        observePresenceEvents()
        startDecayTimer()
    }

    // ── 轨道 A：监听 DB 事件流 ────────────────────────────────

    private fun observePresenceEvents() {
        viewModelScope.launch {
            // 观察最近 50 条事件，有新事件插入时重新 emit
            eventDao.observeLatest(50).collectLatest { events ->
                val presenceEvents = events.filter { it.type == EventType.PRESENCE_CHANGED.name }

                // 对每个有 PRESENCE_CHANGED 事件的角色，取最新一条更新 UI
                val eventUpdates = mutableMapOf<Int, PresenceState>()
                presenceEvents.forEach { event ->
                    val charId = event.actorId?.toIntOrNull() ?: return@forEach
                    // 只处理每个角色的最新一条（list 已按 createdAt DESC 排序）
                    if (charId in eventUpdates) return@forEach

                    val payload = runCatching { JSONObject(event.payload) }.getOrNull()
                    val statusTypeName = payload?.optString("statusType", StatusType.ACTIVE.name)
                        ?: StatusType.ACTIVE.name
                    val statusType = runCatching {
                        StatusType.valueOf(statusTypeName)
                    }.getOrDefault(StatusType.ACTIVE)

                    val statusText = when (statusType) {
                        StatusType.ACTIVE  -> ACTIVE_STATUS_TEXTS.random()
                        StatusType.IDLE    -> IDLE_STATUS_TEXTS.random()
                        StatusType.FOCUSED -> "在专注做事"
                        StatusType.OFFLINE -> "不在线"
                    }

                    eventUpdates[charId] = PresenceState(
                        characterId  = charId,
                        statusText   = statusText,
                        statusType   = statusType,
                        lastUpdated  = event.createdAt,
                        sourceEventId = event.id,
                    )
                    // 更新内存时间戳缓存
                    lastEventTime[charId] = event.createdAt
                }

                if (eventUpdates.isNotEmpty()) {
                    val current = _uiState.value
                    _uiState.value = current.copy(
                        presenceMap = current.presenceMap + eventUpdates
                    )
                }
            }
        }
    }

    // ── 轨道 B：时间衰减 ──────────────────────────────────────

    private fun startDecayTimer() {
        viewModelScope.launch {
            while (true) {
                delay(DECAY_CHECK_INTERVAL_MS)
                applyDecay()
            }
        }
    }

    private fun applyDecay() {
        val now = System.currentTimeMillis()
        val current = _uiState.value
        var changed = false

        val decayed = current.presenceMap.toMutableMap()
        current.characters.filter { it.isUnlocked }.forEach { char ->
            val lastActive = lastEventTime[char.id] ?: 0L
            val currentState = decayed[char.id] ?: return@forEach
            // 只对 ACTIVE 状态做衰减（FOCUSED / OFFLINE 不动）
            if (currentState.statusType == StatusType.ACTIVE &&
                (now - lastActive) > IDLE_THRESHOLD_MS
            ) {
                decayed[char.id] = currentState.copy(
                    statusText  = IDLE_STATUS_TEXTS.random(),
                    statusType  = StatusType.IDLE,
                    lastUpdated = now,
                )
                changed = true
            }
        }

        if (changed) {
            _uiState.value = current.copy(presenceMap = decayed)
        }
    }

    // ── Public actions ────────────────────────────────────────

    fun showPreview(characterId: Int) {
        _uiState.value = _uiState.value.copy(previewCharacterId = characterId)
    }

    fun dismissPreview() {
        _uiState.value = _uiState.value.copy(previewCharacterId = null)
    }

    fun markFirstInteraction() {
        if (hasSeenOnboarding) return
        viewModelScope.launch {
            delay(5_000L)
            if (!hasSeenOnboarding) {
                _uiState.value = _uiState.value.copy(showOnboardingTooltip = true)
            }
        }
    }

    fun dismissOnboarding() {
        hasSeenOnboarding = true
        _uiState.value = _uiState.value.copy(showOnboardingTooltip = false)
    }

    fun renameCharacter(id: Int, newName: String) {
        val updated = _uiState.value.characters.map { c ->
            if (c.id == id) c.copy(name = newName) else c
        }
        _uiState.value = _uiState.value.copy(characters = updated)
    }
}

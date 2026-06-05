package com.zaijian.zhoumuyun.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.zaijian.zhoumuyun.data.db.AppDatabase
import com.zaijian.zhoumuyun.data.db.entity.MemoryDomain
import com.zaijian.zhoumuyun.data.db.entity.MemoryEntity
import com.zaijian.zhoumuyun.data.repository.MemoryRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// ─────────────────────────────────────────────────────────────
//  UI State
// ─────────────────────────────────────────────────────────────

/**
 * 记忆 Tab 的展示模型（UI 层，从 MemoryEntity 映射而来）。
 *
 * 与旧的 sampleMemories 硬编码结构对齐，保持 UI 改动最小化：
 * - id: Long → 用 hashCode() 映射
 * - content: 记忆内容
 * - dateLabel: 格式化日期
 * - isImportant: importance >= 4
 * - aboutSelf: domain == PERSONAL（关于用户）
 * - isCore: isCore 字段透传
 */
data class MemoryUiItem(
    val id: String,
    val content: String,
    val dateLabel: String,
    val isImportant: Boolean,
    val isCore: Boolean,
    /** true = 关于用户（PERSONAL domain），false = 关于角色/世界 */
    val aboutSelf: Boolean,
    val domain: String,
    val importance: Int,
)

enum class MemoryFilter { ALL, IMPORTANT, ABOUT_ME, ABOUT_WORLD }

data class MemoryUiState(
    val items: List<MemoryUiItem> = emptyList(),
    val isLoading: Boolean = true,
    val filter: MemoryFilter = MemoryFilter.ALL,
    /** 操作结果提示（删除/标记等） */
    val snackbar: String? = null,
)

// ─────────────────────────────────────────────────────────────
//  ViewModel
// ─────────────────────────────────────────────────────────────

/**
 * MemoryViewModel（Phase 8）
 *
 * 为 CharacterDetailScreen 的「记忆」Tab 提供真实数据。
 * 替换掉 sampleMemories 硬编码。
 *
 * 职责：
 * 1. 按角色 ID 从 memories 表实时观察数据
 * 2. 支持四种过滤（全部 / 重要 / 关于我 / 关于他）
 * 3. 切换 isImportant（对应 importance 4/3 互切）
 * 4. 删除单条记忆
 * 5. 标记 Core（importance=5）
 */
class MemoryViewModel(application: Application) : AndroidViewModel(application) {

    private val db   = AppDatabase.getInstance(application)
    private val repo = MemoryRepository(db.memoryDao(), db.memoryCandidateDao())

    private val _characterId = MutableStateFlow(-1)
    private val _filter      = MutableStateFlow(MemoryFilter.ALL)
    private val _snackbar    = MutableStateFlow<String?>(null)

    // ─────────────────────────────────────────────────────────
    //  实时数据流：根据 filter 切换观察的 Flow
    // ─────────────────────────────────────────────────────────

    @OptIn(ExperimentalCoroutinesApi::class)
    private val _entities: StateFlow<List<MemoryEntity>> = _characterId
        .flatMapLatest { cid ->
            if (cid < 0) flowOf(emptyList())
            else repo.observeAll(cid)   // 总是观察全量，在 ViewModel 里过滤
        }
        .catch { emit(emptyList()) }
        .stateIn(
            scope            = viewModelScope,
            started          = SharingStarted.WhileSubscribed(5_000),
            initialValue     = emptyList(),
        )

    val uiState: StateFlow<MemoryUiState> = _entities
        .map { entities ->
            val allItems = entities.map { it.toUiItem() }
            MemoryUiState(
                items     = applyFilter(allItems, _filter.value),
                isLoading = false,
                filter    = _filter.value,
                snackbar  = _snackbar.value,
            )
        }
        .stateIn(
            scope        = viewModelScope,
            started      = SharingStarted.WhileSubscribed(5_000),
            initialValue = MemoryUiState(isLoading = true),
        )

    // ─────────────────────────────────────────────────────────
    //  初始化
    // ─────────────────────────────────────────────────────────

    fun init(characterId: Int) {
        if (_characterId.value == characterId) return
        _characterId.value = characterId
    }

    // ─────────────────────────────────────────────────────────
    //  过滤切换
    // ─────────────────────────────────────────────────────────

    fun setFilter(filter: MemoryFilter) {
        _filter.value = filter
    }

    // ─────────────────────────────────────────────────────────
    //  操作：切换 isImportant（星标）
    //  逻辑：importance >= 4 → 降到 3；否则 → 升到 4
    // ─────────────────────────────────────────────────────────

    fun toggleImportant(memoryId: String) {
        viewModelScope.launch {
            val entity = _entities.value.find { it.id == memoryId } ?: return@launch
            val newImportance = if (entity.importance >= 4) 3 else 4
            val updated = entity.copy(
                importance = newImportance,
                updatedAt  = System.currentTimeMillis(),
            )
            repo.update(updated)
        }
    }

    // ─────────────────────────────────────────────────────────
    //  操作：删除
    // ─────────────────────────────────────────────────────────

    fun delete(memoryId: String) {
        viewModelScope.launch {
            repo.deleteById(memoryId)
            _snackbar.value = "已删除"
        }
    }

    // ─────────────────────────────────────────────────────────
    //  操作：标记/取消 Core Memory
    // ─────────────────────────────────────────────────────────

    fun toggleCore(memoryId: String) {
        viewModelScope.launch {
            val entity = _entities.value.find { it.id == memoryId } ?: return@launch
            val updated = entity.copy(
                isCore     = !entity.isCore,
                importance = if (!entity.isCore) 5 else 4,
                updatedAt  = System.currentTimeMillis(),
            )
            repo.update(updated)
            val msg = if (updated.isCore) "已设为核心记忆" else "已取消核心记忆"
            _snackbar.value = msg
        }
    }

    fun clearSnackbar() {
        _snackbar.value = null
    }

    // ─────────────────────────────────────────────────────────
    //  内部工具
    // ─────────────────────────────────────────────────────────

    private fun applyFilter(items: List<MemoryUiItem>, filter: MemoryFilter): List<MemoryUiItem> =
        when (filter) {
            MemoryFilter.ALL         -> items
            MemoryFilter.IMPORTANT   -> items.filter { it.isImportant }
            MemoryFilter.ABOUT_ME    -> items.filter { it.aboutSelf }
            MemoryFilter.ABOUT_WORLD -> items.filter { !it.aboutSelf }
        }

    private fun MemoryEntity.toUiItem(): MemoryUiItem {
        val isImportant = importance >= 4 || isCore
        val aboutSelf   = domain == MemoryDomain.PERSONAL.name

        // 日期格式化
        val dateLabel = run {
            val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
            sdf.format(java.util.Date(createdAt))
        }

        return MemoryUiItem(
            id          = id,
            content     = content,
            dateLabel   = dateLabel,
            isImportant = isImportant,
            isCore      = isCore,
            aboutSelf   = aboutSelf,
            domain      = domain,
            importance  = importance,
        )
    }
}

// ─────────────────────────────────────────────────────────────
//  MemoryRepository 需要 update 方法，在此处扩展
// ─────────────────────────────────────────────────────────────

// update() 已在 MemoryRepository 中定义，此处无需重复。

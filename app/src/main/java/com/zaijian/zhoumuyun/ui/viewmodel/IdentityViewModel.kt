package com.zaijian.zhoumuyun.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.zaijian.zhoumuyun.data.db.AppDatabase
import com.zaijian.zhoumuyun.data.db.entity.CharacterIdentityEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// ─────────────────────────────────────────────────────────────
//  IdentityViewModel（Phase 7）
//
//  职责：
//  - 从 Room 读取 CharacterIdentityEntity（一个角色一条）
//  - 将用户的编辑保存回 DB
//  - 给 CharacterDetailScreen「人设」Tab 提供数据和动作
// ─────────────────────────────────────────────────────────────

data class IdentityUiState(
    val persona: String = "",
    val speechStyle: String = "",
    val attitudeToUser: String = "",
    val customSystemPrompt: String = "",
    val isLoading: Boolean = false,
    val isSaved: Boolean = false,   // 保存成功闪一下
)

class IdentityViewModel(application: Application) : AndroidViewModel(application) {

    private val identityDao = AppDatabase.getInstance(application).characterIdentityDao()

    private val _uiState = MutableStateFlow(IdentityUiState(isLoading = true))
    val uiState: StateFlow<IdentityUiState> = _uiState.asStateFlow()

    private var currentCharacterId: Int = -1

    // ── 初始化：读取当前角色的 Identity ──────────────────────

    fun init(characterId: Int) {
        if (currentCharacterId == characterId) return
        currentCharacterId = characterId
        viewModelScope.launch {
            val entity = identityDao.getById(characterId)
            _uiState.update {
                IdentityUiState(
                    persona             = entity?.persona ?: "",
                    speechStyle         = entity?.speechStyle ?: "",
                    attitudeToUser      = entity?.attitudeToUser ?: "",
                    customSystemPrompt  = entity?.customSystemPrompt ?: "",
                    isLoading           = false,
                )
            }
        }
    }

    // ── 字段更新（UI 层双向绑定）────────────────────────────

    fun onPersonaChange(v: String)            = _uiState.update { it.copy(persona = v, isSaved = false) }
    fun onSpeechStyleChange(v: String)        = _uiState.update { it.copy(speechStyle = v, isSaved = false) }
    fun onAttitudeToUserChange(v: String)     = _uiState.update { it.copy(attitudeToUser = v, isSaved = false) }
    fun onCustomSystemPromptChange(v: String) = _uiState.update { it.copy(customSystemPrompt = v, isSaved = false) }

    // ── 保存到 DB ────────────────────────────────────────────

    fun save() {
        if (currentCharacterId < 0) return
        viewModelScope.launch {
            val s = _uiState.value
            identityDao.upsert(
                CharacterIdentityEntity(
                    characterId        = currentCharacterId,
                    persona            = s.persona.trim(),
                    speechStyle        = s.speechStyle.trim(),
                    attitudeToUser     = s.attitudeToUser.trim(),
                    customSystemPrompt = s.customSystemPrompt.trim().ifEmpty { null },
                    updatedAt          = System.currentTimeMillis(),
                )
            )
            _uiState.update { it.copy(isSaved = true) }
        }
    }

    fun clearSavedFlag() = _uiState.update { it.copy(isSaved = false) }
}

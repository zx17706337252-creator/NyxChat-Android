package com.zaijian.zhoumuyun.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.zaijian.zhoumuyun.ui.theme.Spacing
import com.zaijian.zhoumuyun.ui.theme.ZaijianTheme

// ─────────────────────────────────────────────────────────────
//  Placeholder screens for phases not yet implemented.
//
//  Phase 3:  CharacterScreen           → CharacterScreen.kt      ✅
//  Phase 4:  ChatScreen                → ChatScreen.kt           ✅
//  Phase 4:  CharacterDetailScreen     → CharacterDetailScreen.kt✅
//  Phase 5:  TaskCenterScreen          → TaskCenterScreen.kt     ✅
//  Phase 5:  ProfileScreen             → ProfileScreen.kt        ✅
// ─────────────────────────────────────────────────────────────

// 所有主屏幕均已实现，此文件暂留作后续扩展占位使用。

// ─────────────────────────────────────────────────────────────
//  Internal helper（保留，供未来临时占位使用）
// ─────────────────────────────────────────────────────────────

@Composable
internal fun PlaceholderContent(
    emoji: String,
    label: String,
    note: String,
) {
    val colors = ZaijianTheme.colors
    val type   = ZaijianTheme.typography

    Box(
        modifier         = Modifier.fillMaxSize().background(colors.bgBase),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                emoji,
                style = type.titleBold.copy(
                    fontSize = androidx.compose.ui.unit.TextUnit(
                        40f, androidx.compose.ui.unit.TextUnitType.Sp,
                    ),
                ),
            )
            Spacer(Modifier.height(Spacing.sm))
            Text(label, style = type.titleBold, color = colors.textPrimary)
            Spacer(Modifier.height(Spacing.xs))
            Text(note, style = type.caption, color = colors.textSecondary)
        }
    }
}

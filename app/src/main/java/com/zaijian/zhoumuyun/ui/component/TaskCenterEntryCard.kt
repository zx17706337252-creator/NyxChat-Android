package com.zaijian.zhoumuyun.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowForwardIos
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.zaijian.zhoumuyun.ui.theme.Radius
import com.zaijian.zhoumuyun.ui.theme.Spacing
import com.zaijian.zhoumuyun.ui.theme.ZaijianTheme

// ─────────────────────────────────────────────────────────────
//  TaskCenterEntryCard  — 公馆底部任务中心入口
//  设计规范 §10 [任务中心卡片]
// ─────────────────────────────────────────────────────────────

@Composable
fun TaskCenterEntryCard(
    activeTaskCount: Int = 0,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = ZaijianTheme.colors
    val type   = ZaijianTheme.typography

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.sm))
            .background(colors.bgCard)
            .clickable(onClick = onClick)
            .padding(horizontal = Spacing.md, vertical = Spacing.sm),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Icon(
                imageVector        = Icons.Outlined.CheckCircle,
                contentDescription = null,
                tint               = colors.accent,
                modifier           = Modifier.size(20.dp),
            )
            androidx.compose.foundation.layout.Column {
                Text(
                    text  = "任务中心",
                    style = type.button,
                    color = colors.textPrimary,
                )
                if (activeTaskCount > 0) {
                    Text(
                        text  = "$activeTaskCount 个进行中",
                        style = type.label,
                        color = colors.textSecondary,
                    )
                } else {
                    Text(
                        text  = "暂无进行中的任务",
                        style = type.label,
                        color = colors.textSecondary,
                    )
                }
            }
        }

        Icon(
            imageVector        = Icons.Outlined.ArrowForwardIos,
            contentDescription = null,
            tint               = colors.textDisabled,
            modifier           = Modifier.size(14.dp),
        )
    }
}

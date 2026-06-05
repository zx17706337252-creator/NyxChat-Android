package com.zaijian.zhoumuyun.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.zaijian.zhoumuyun.ui.theme.GlassOpacity
import com.zaijian.zhoumuyun.ui.theme.Spacing
import com.zaijian.zhoumuyun.ui.theme.ZaijianTheme

// ─────────────────────────────────────────────────────────────
//  MansionHeader  — 公馆顶部毛玻璃 Header
//  设计规范 §10  [Header] 毛玻璃，56dp
// ─────────────────────────────────────────────────────────────

@Composable
fun MansionHeader(
    title: String = "永恒之家",
    subtitle: String = "他们都在这里，等你回来",
    hasNewNotification: Boolean = false,
    onNotificationClick: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val colors = ZaijianTheme.colors
    val type   = ZaijianTheme.typography

    val bgColor = if (colors.isDark)
        colors.bgBase.copy(alpha = GlassOpacity.topBarDark)
    else
        colors.bgBase.copy(alpha = GlassOpacity.topBarLight)

    val borderColor = if (colors.isDark)
        Color(0x1AFFFFFF)  // 10% 白边增加玻璃质感
    else
        colors.border.copy(alpha = 0.5f)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(bgColor)
            .border(
                width = 0.5.dp,
                color = borderColor,
                shape = androidx.compose.foundation.shape.RoundedCornerShape(0.dp),
            ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .height(56.dp)
                .padding(horizontal = Spacing.screenHorizontal),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            // ── 左侧占位（后续可放菜单/头像） ─────────────────
            Spacer(Modifier.size(40.dp))

            // ── 中间标题 ──────────────────────────────────────
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text  = title,
                    style = type.navTitle,
                    color = colors.textPrimary,
                )
                Text(
                    text  = subtitle,
                    style = type.label,
                    color = colors.textSecondary,
                )
            }

            // ── 右侧铃铛 ──────────────────────────────────────
            Box {
                IconButton(
                    onClick  = onNotificationClick,
                    modifier = Modifier.size(40.dp),
                ) {
                    Icon(
                        imageVector        = Icons.Outlined.Notifications,
                        contentDescription = "通知",
                        tint               = colors.textSecondary,
                        modifier           = Modifier.size(22.dp),
                    )
                }
                // 未读红点
                if (hasNewNotification) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .align(Alignment.TopEnd)
                            .padding(end = 2.dp, top = 2.dp)
                            .background(Color(0xFFF47067), CircleShape)
                    )
                }
            }
        }
    }
}

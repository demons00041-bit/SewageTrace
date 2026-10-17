package com.sewagetrace.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sewagetrace.app.R
import com.sewagetrace.app.model.SeverityLevel
import com.sewagetrace.app.ui.theme.Water300

@Composable
fun SectionTitle(text: String, trailing: String? = null, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text, style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = if (trailing != null) Modifier.weight(1f, fill = false) else Modifier
        )
        if (trailing != null) {
            Spacer(Modifier.width(10.dp))
            Text(
                trailing, style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
                textAlign = androidx.compose.ui.text.style.TextAlign.End,
                lineHeight = 15.sp,
                maxLines = 2
            )
        }
    }
}

@Composable
fun CardBox(
    modifier: Modifier = Modifier,
    container: Color = MaterialTheme.colorScheme.surface,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(container)
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f), RoundedCornerShape(18.dp))
    ) { content() }
}

/** 带标题的分组卡片：标题 + 内容槽。 */
@Composable
fun SectionCard(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    CardBox(modifier) {
        Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}

/** 等级徽标：文案由 level_res_* 资源决定，随语言切换 */
@Composable
fun SeverityChip(level: SeverityLevel, text: String? = null, small: Boolean = false) {
    val c = level.color
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(c.copy(alpha = 0.16f))
            .border(1.dp, c.copy(alpha = 0.55f), RoundedCornerShape(999.dp))
            .padding(horizontal = if (small) 8.dp else 12.dp, vertical = if (small) 2.dp else 5.dp)
    ) {
        Text(
            text ?: (level.code + " " + levelName(level)),
            color = c,
            fontSize = if (small) 10.5.sp else 12.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
fun levelName(level: SeverityLevel): String = stringResource(
    when (level) {
        SeverityLevel.R4 -> R.string.level_r4
        SeverityLevel.R3 -> R.string.level_r3
        SeverityLevel.R2 -> R.string.level_r2
        SeverityLevel.R1 -> R.string.level_r1
        SeverityLevel.R0 -> R.string.level_r0
    }
)

@Composable
fun ProgressBar(
    percent: Int,
    color: Color = MaterialTheme.colorScheme.primary,
    height: Int = 10,
    showLabel: Boolean = true
) {
    val anim by animateFloatAsState(
        targetValue = percent.coerceIn(0, 100) / 100f,
        animationSpec = tween(700), label = "progress"
    )
    Column(Modifier.fillMaxWidth()) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(height.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Box(
                Modifier
                    .fillMaxWidth(anim)
                    .height(height.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(Brush.horizontalGradient(listOf(color.copy(alpha = 0.75f), color)))
            )
        }
        if (showLabel) {
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    stringResource(R.string.progress_formula), fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text("$percent%", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = color)
            }
        }
    }
}

@Composable
fun StatPill(label: String, value: String, accent: Color = MaterialTheme.colorScheme.primary, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
            .padding(horizontal = 8.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(value, fontSize = 19.sp, fontWeight = FontWeight.Bold, color = accent)
        Text(
            label, fontSize = 9.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center, lineHeight = 12.sp
        )
    }
}

@Composable
fun Dot(color: Color, size: Int = 8) {
    Box(Modifier.size(size.dp).clip(CircleShape).background(color))
}

@Composable
fun KV(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(label, Modifier.width(96.dp), fontSize = 11.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(6.dp))
        Text(value, fontSize = 12.sp, color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.weight(1f))
    }
}

@Composable
fun Divider2() {
    Box(
        Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
    )
}

/** 通用返回条 */
@Composable
fun TopBar(title: String, onBack: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        androidx.compose.material3.TextButton(onClick = onBack) {
            Text("‹ " + stringResource(R.string.back), fontSize = 13.sp, color = Water300)
        }
        Spacer(Modifier.width(4.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
    }
}

/** 轻量"空状态"卡片 */
@Composable
fun EmptyHint(text: String, sub: String? = null) {
    CardBox {
        Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            if (sub != null) {
                Spacer(Modifier.height(6.dp))
                Text(
                    sub, fontSize = 10.5.sp, lineHeight = 16.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center
                )
            }
        }
    }
}

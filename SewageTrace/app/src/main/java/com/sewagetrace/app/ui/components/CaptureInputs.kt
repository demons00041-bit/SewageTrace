package com.sewagetrace.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sewagetrace.app.R
import com.sewagetrace.app.model.SeverityLevel

/** 场景描述上限，对应需求 CAP-01 */
const val SCENE_DESC_MAX = 500

/**
 * 场景描述输入框（需求 CAP-01）。
 *
 * - 多行，3 行起，最多 6 行后内部滚动
 * - 上限 500 字符，实时计数，超限直接拦住而不是静默截断
 * - 非必填，未填不阻断识别与提交
 */
@Composable
fun SceneDescriptionField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                stringResource(R.string.scene_desc_label),
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.width(6.dp))
            Text(
                stringResource(R.string.optional_tag),
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.weight(1f))
            if (value.isNotEmpty()) {
                Row(
                    Modifier
                        .clip(RoundedCornerShape(99.dp))
                        .clickable { onValueChange("") }
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Filled.Close, null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(Modifier.width(3.dp))
                    Text(
                        stringResource(R.string.clear),
                        fontSize = 10.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        OutlinedTextField(
            value = value,
            onValueChange = { v ->
                // 超限不写入，而不是截断后悄悄丢掉用户刚敲的字
                if (v.length <= SCENE_DESC_MAX) onValueChange(v)
            },
            placeholder = {
                Text(
                    stringResource(R.string.scene_desc_hint),
                    fontSize = 11.5.sp,
                    lineHeight = 17.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            },
            minLines = 3,
            maxLines = 6,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        )
        Row(Modifier.fillMaxWidth().padding(top = 3.dp), horizontalArrangement = Arrangement.End) {
            Text(
                stringResource(R.string.scene_desc_counter, value.length, SCENE_DESC_MAX),
                fontSize = 10.sp,
                color = if (value.length >= SCENE_DESC_MAX) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * 人工污水等级选择器（需求 CAP-02）。
 *
 * - 5 档由低到高（R0 → R4），颜色绿 → 红
 * - 不预选任何一档，避免默认值污染数据
 * - 不只靠颜色区分：编号 + 文字 + 选中勾选图标 + 加粗边框
 * - 若已有 AI 判定，只给「AI 建议」提示，不自动勾选
 */
@Composable
fun ManualLevelPicker(
    selected: SeverityLevel?,
    onSelect: (SeverityLevel) -> Unit,
    aiSuggestion: SeverityLevel?,
    modifier: Modifier = Modifier
) {
    Column(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.manual_level_label),
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.width(4.dp))
            Text("*", fontSize = 13.sp, color = MaterialTheme.colorScheme.error)
            Spacer(Modifier.weight(1f))
            if (aiSuggestion != null) {
                Text(
                    stringResource(R.string.ai_suggestion, aiSuggestion.code),
                    fontSize = 10.sp,
                    color = aiSuggestion.color.copy(alpha = 0.85f)
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            SeverityLevel.ascending().forEach { level ->
                LevelCard(
                    level = level,
                    selected = selected == level,
                    modifier = Modifier.weight(1f),
                    onClick = { onSelect(level) }
                )
            }
        }
        if (selected == null) {
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(R.string.manual_level_required),
                fontSize = 10.5.sp,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}

@Composable
private fun LevelCard(
    level: SeverityLevel,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val c = level.color
    Column(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) c.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
            .border(
                width = if (selected) 1.5.dp else 1.dp,
                color = if (selected) c else Color.Transparent,
                shape = RoundedCornerShape(12.dp)
            )
            .clickable(onClick = onClick)
            .padding(vertical = 9.dp, horizontal = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier
                .width(18.dp)
                .height(3.dp)
                .clip(RoundedCornerShape(99.dp))
                .background(if (selected) c else c.copy(alpha = 0.55f))
        )
        Spacer(Modifier.height(6.dp))
        Text(
            level.code,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = if (selected) c else MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(1.dp))
        Text(
            stringResource(shortNameRes(level)),
            fontSize = 9.5.sp,
            lineHeight = 12.sp,
            maxLines = 2,
            textAlign = TextAlign.Center,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) c else MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(5.dp))
        Box(
            Modifier
                .size(15.dp)
                .clip(CircleShape)
                .background(if (selected) c else Color.Transparent),
            contentAlignment = Alignment.Center
        ) {
            if (selected) {
                Icon(Icons.Filled.Check, null, tint = Color.White, modifier = Modifier.size(10.dp))
            }
        }
    }
}

private fun shortNameRes(level: SeverityLevel): Int = when (level) {
    SeverityLevel.R0 -> R.string.mlv_r0
    SeverityLevel.R1 -> R.string.mlv_r1
    SeverityLevel.R2 -> R.string.mlv_r2
    SeverityLevel.R3 -> R.string.mlv_r3
    SeverityLevel.R4 -> R.string.mlv_r4
}

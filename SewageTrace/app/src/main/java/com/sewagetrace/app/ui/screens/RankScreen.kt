package com.sewagetrace.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sewagetrace.app.R
import com.sewagetrace.app.model.LeaderRow
import com.sewagetrace.app.model.Ticket
import com.sewagetrace.app.ui.AppViewModel
import com.sewagetrace.app.ui.Screen
import com.sewagetrace.app.ui.components.CardBox
import com.sewagetrace.app.ui.components.ProgressBar
import com.sewagetrace.app.ui.components.SectionTitle
import com.sewagetrace.app.ui.components.StatPill
import com.sewagetrace.app.ui.theme.Water300
import com.sewagetrace.app.ui.theme.Water700

/**
 * 第三栏：排行榜 + 任务反馈。
 * 排行榜口径：只统计「有效发现」（R2 及以上、通过审核、同水体 24h 去重），
 * 不以发帖量排名，防止刷单冲榜。
 */
@Composable
fun RankScreen(vm: AppViewModel) {
    vm.tick
    var sub by remember { mutableStateOf(0) } // 0 排行 1 反馈
    val stats = vm.stats()

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                stringResource(R.string.rank_page_title),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.weight(1f)
            )
            Box(
                Modifier.size(30.dp).clip(CircleShape).background(Water700.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Text((vm.user?.nickname ?: "?").take(1), fontSize = 13.sp, color = Water300)
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SubTab(stringResource(R.string.rank_tab_board), sub == 0, Modifier.weight(1f)) { sub = 0 }
            SubTab(stringResource(R.string.rank_tab_feedback), sub == 1, Modifier.weight(1f)) { sub = 1 }
        }
        Spacer(Modifier.height(12.dp))

        if (sub == 0) Leaderboard(vm) else Feedback(vm, stats)
    }
}

@Composable
private fun SubTab(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (selected) Water700.copy(alpha = 0.22f)
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
            )
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label, fontSize = 12.5.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun Leaderboard(vm: AppViewModel) {
    val rows: List<LeaderRow> = vm.leaderboard()
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SubTab(stringResource(R.string.rank_city), vm.rankScope == "city", Modifier.weight(1f)) {
                vm.rankScope = "city"; vm.refresh()
            }
            SubTab(stringResource(R.string.rank_nation), vm.rankScope == "nation", Modifier.weight(1f)) {
                vm.rankScope = "nation"; vm.refresh()
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val opts = listOf(
                7 to stringResource(R.string.rank_7d),
                30 to stringResource(R.string.rank_30d),
                0 to stringResource(R.string.rank_all)
            )
            opts.forEach { (d, l) ->
                SubTab(l, vm.rankWindow == d, Modifier.weight(1f)) { vm.rankWindow = d; vm.refresh() }
            }
        }
        Spacer(Modifier.height(12.dp))

        CardBox(container = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)) {
            Column(Modifier.padding(12.dp)) {
                Text(
                    stringResource(R.string.rank_rule_text),
                    fontSize = 10.sp, lineHeight = 15.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(Modifier.height(12.dp))

        if (rows.isEmpty()) {
            CardBox {
                Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        stringResource(R.string.rank_empty_title),
                        fontSize = 13.sp, fontWeight = FontWeight.Medium
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        stringResource(R.string.rank_empty_sub),
                        fontSize = 10.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            rows.forEach { r ->
                LeaderItem(r) {}
                Spacer(Modifier.height(8.dp))
            }
        }

        Spacer(Modifier.height(6.dp))
        Text(
            stringResource(R.string.rank_footer),
            fontSize = 9.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun LeaderItem(r: LeaderRow, onClick: () -> Unit) {
    val medal = when (r.rank) {
        1 -> Color(0xFFFBBF24)
        2 -> Color(0xFFCBD5E1)
        3 -> Color(0xFFD97706)
        else -> null
    }
    CardBox(
        container = if (r.isMe) Water700.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surface
    ) {
        Row(
            Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(30.dp).clip(CircleShape)
                    .background(
                        (medal ?: MaterialTheme.colorScheme.surfaceVariant)
                            .copy(alpha = if (medal != null) 0.28f else 1f)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "${r.rank}", fontSize = 13.sp, fontWeight = FontWeight.Bold,
                    color = medal ?: MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        r.displayName + if (r.isMe) " " + stringResource(R.string.leaderboard_you) else "",
                        fontSize = 13.5.sp, fontWeight = FontWeight.Medium
                    )
                    r.badgeRes?.let { b ->
                        Spacer(Modifier.width(6.dp))
                        Box(
                            Modifier.clip(RoundedCornerShape(6.dp))
                                .background(Water300.copy(alpha = 0.14f))
                                .padding(horizontal = 5.dp, vertical = 1.dp)
                        ) { Text(stringResource(b), fontSize = 8.5.sp, color = Water300) }
                    }
                }
                Text(
                    stringResource(R.string.rank_row_detail, r.city, r.validFindings, r.accepted, r.resolved),
                    fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                "${r.validFindings}", fontSize = 18.sp,
                fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun Feedback(vm: AppViewModel, stats: com.sewagetrace.app.data.StatsService.Aggregate) {
    val myTickets = vm.tickets()
    val g = vm.globalStats()
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            StatPill(stringResource(R.string.my_reports), "${stats.tickets}", Water300, Modifier.weight(1f))
            StatPill(stringResource(R.string.in_progress), "${stats.inProgress}", Color(0xFFF59E0B), Modifier.weight(1f))
            StatPill(stringResource(R.string.closed), "${stats.closed}", Color(0xFF22C55E), Modifier.weight(1f))
        }
        Spacer(Modifier.height(10.dp))
        CardBox {
            Column(Modifier.padding(14.dp)) {
                Text(
                    stringResource(R.string.feedback_my_progress),
                    fontSize = 13.sp, fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(8.dp))
                if (myTickets.isEmpty()) {
                    Text(
                        stringResource(R.string.feedback_no_ticket),
                        fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    ProgressBar(
                        stats.avgProgress,
                        color = if (stats.avgProgress >= 70) Color(0xFF22C55E) else Color(0xFFF59E0B)
                    )
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    StatPill(
                        stringResource(R.string.avg_response),
                        if (stats.avgResponseHours > 0) "%.1fh".format(stats.avgResponseHours) else "—",
                        Water300, Modifier.weight(1f)
                    )
                    StatPill(stringResource(R.string.feedback_escalated), "${stats.escalated}", Color(0xFFF59E0B), Modifier.weight(1f))
                    StatPill(stringResource(R.string.feedback_disputed), "${stats.disputed}", MaterialTheme.colorScheme.error, Modifier.weight(1f))
                }
            }
        }

        Spacer(Modifier.height(14.dp))
        SectionTitle(stringResource(R.string.feedback_dynamic), stringResource(R.string.feedback_dynamic_sub))
        Spacer(Modifier.height(8.dp))
        vm.allTickets().take(6).forEach { t ->
            TicketRow(t, vm) { vm.go(Screen.TicketDetail(t.id)) }
            Spacer(Modifier.height(8.dp))
        }

        Spacer(Modifier.height(10.dp))
        CardBox(container = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)) {
            Column(Modifier.padding(14.dp)) {
                Text(
                    stringResource(R.string.feedback_transparency),
                    fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    stringResource(R.string.feedback_global, g.tickets, g.closed, g.avgProgress, g.escalated),
                    fontSize = 10.5.sp, lineHeight = 16.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    stringResource(R.string.feedback_progress_rule),
                    fontSize = 9.5.sp, lineHeight = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(Modifier.height(14.dp))
        SectionTitle(stringResource(R.string.feedback_actions))
        Spacer(Modifier.height(8.dp))
        val actions = listOf(
            R.string.fb_done_title to R.string.fb_done_desc,
            R.string.fb_rejected_title to R.string.fb_rejected_desc,
            R.string.fb_rank_title to R.string.fb_rank_desc,
            R.string.fb_other_title to R.string.fb_other_desc
        )
        actions.forEach { (titleRes, descRes) ->
            CardBox {
                Row(
                    Modifier.fillMaxWidth().padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(titleRes), fontSize = 12.5.sp, fontWeight = FontWeight.Medium)
                        Text(
                            stringResource(descRes),
                            fontSize = 10.sp, lineHeight = 15.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text("›", fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(8.dp))
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
fun TicketRow(t: Ticket, vm: AppViewModel, onClick: () -> Unit) {
    CardBox {
        Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(13.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    t.waterbodyName, fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f)
                )
                if (t.escalated) {
                    Box(
                        Modifier.clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFFDC2626).copy(alpha = 0.16f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) { Text(stringResource(R.string.ticket_escalated_badge), fontSize = 8.5.sp, color = Color(0xFFF87171)) }
                    Spacer(Modifier.width(6.dp))
                }
                Text(
                    "${t.progress}%", fontSize = 13.sp, fontWeight = FontWeight.Bold,
                    color = if (t.progress >= 70) Color(0xFF22C55E) else Color(0xFFF59E0B)
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(t.stage.labelRes) + "  ·  " + t.agencyName,
                fontSize = 10.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            ProgressBar(
                t.progress, height = 6, showLabel = false,
                color = if (t.progress >= 70) Color(0xFF22C55E) else Color(0xFFF59E0B)
            )
        }
    }
}

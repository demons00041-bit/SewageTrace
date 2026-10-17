package com.sewagetrace.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sewagetrace.app.R
import com.sewagetrace.app.data.DemoRepo
import com.sewagetrace.app.model.Confidence
import com.sewagetrace.app.model.ReportStatus
import com.sewagetrace.app.model.TicketStage
import com.sewagetrace.app.ui.AppViewModel
import com.sewagetrace.app.ui.Screen
import com.sewagetrace.app.ui.components.CardBox
import com.sewagetrace.app.ui.components.Divider2
import com.sewagetrace.app.ui.components.KV
import com.sewagetrace.app.ui.components.ProgressBar
import com.sewagetrace.app.ui.components.SectionTitle
import com.sewagetrace.app.ui.theme.Water300
import com.sewagetrace.app.ui.theme.Water700

/** 单条上报详情：AI 结论 + 证据 + 水印照片 + 工单入口 */
@Composable
fun ReportResultScreen(vm: AppViewModel, reportId: String) {
    vm.tick
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val r = vm.report(reportId)
    if (r == null) {
        Text(stringResource(R.string.not_found), Modifier.padding(20.dp))
        return
    }
    val ticket = r.ticketId?.let { vm.ticket(it) }

    Column(Modifier.fillMaxSize()) {
        TopBar(stringResource(R.string.report_detail)) { vm.back() }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            Box(
                Modifier.fillMaxWidth().aspectRatio(4f / 3f).clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF16262E))
            ) {
                val bmp = remember(r.photoPath) {
                    runCatching {
                        if (r.photoPath.isBlank()) null
                        else android.graphics.BitmapFactory.decodeFile(
                            r.photoPath, android.graphics.BitmapFactory.Options().apply { inSampleSize = 2 }
                        )
                    }.getOrNull()
                }
                if (bmp != null) {
                    androidx.compose.foundation.Image(
                        bitmap = bmp.asImageBitmap(), contentDescription = null,
                        modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop
                    )
                }
                Box(
                    Modifier.align(Alignment.BottomStart).padding(10.dp)
                        .clip(RoundedCornerShape(7.dp)).background(Color.Black.copy(alpha = 0.55f))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        DemoRepo.fmtTime(r.capturedAt) + "  ·  " +
                            "%.4fN %.4fE".format(r.lat, r.lng) + " · " +
                            stringResource(R.string.accuracy_short, r.precision),
                        fontSize = 8.5.sp, color = Color.White.copy(alpha = 0.9f)
                    )
                }
            }
            Spacer(Modifier.height(12.dp))

            ResultCard(
                r.index, r.level, r.confidence, stringResource(r.suspectedType.labelRes),
                if (r.engine == com.sewagetrace.app.model.Report.ENGINE_CLOUD)
                    vm.cloudEngineName else vm.localEngineName
            )
            Spacer(Modifier.height(12.dp))

            CardBox {
                Column(Modifier.padding(16.dp)) {
                    Text(stringResource(R.string.evidence_breakdown), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(8.dp))
                    r.evidence.forEach { e ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
                            Box(
                                Modifier.size(26.dp).clip(CircleShape).background(r.level.color.copy(alpha = 0.16f)),
                                contentAlignment = Alignment.Center
                            ) { Text("${e.weight}", fontSize = 9.sp, color = r.level.color) }
                            Spacer(Modifier.width(9.dp))
                            Column {
                                Text(
                                    stringResource(e.dim.labelRes),
                                    fontSize = 11.5.sp, fontWeight = FontWeight.Medium
                                )
                                val note = e.sampleKey?.let { vm.sampleText(it) } ?: e.noteResId?.let { stringResource(it) } ?: ""
                                if (note.isNotBlank()) {
                                    Text(
                                        note, fontSize = 10.sp, lineHeight = 15.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                    Divider2()
                    Spacer(Modifier.height(8.dp))
                    KV(stringResource(R.string.waterbody), r.waterbodyName)
                    KV(stringResource(R.string.report_status), stringResource(r.status.labelRes))
                    KV(
                        stringResource(R.string.confidence_label),
                        stringResource(r.confidence.labelRes) + " · " + stringResource(r.confidence.descRes)
                    )
                    if (r.note.isNotBlank()) KV(stringResource(R.string.extra_note), r.note)
                }
            }

            if (r.confidence == Confidence.LOW) {
                Spacer(Modifier.height(10.dp))
                CardBox(container = Color(0xFFF59E0B).copy(alpha = 0.10f)) {
                    Text(
                        stringResource(R.string.low_conf_note),
                        Modifier.padding(13.dp), fontSize = 10.5.sp, lineHeight = 16.sp, color = Color(0xFFF59E0B)
                    )
                }
            }

            if (r.status == ReportStatus.DRAFT) {
                Spacer(Modifier.height(10.dp))
                Button(
                    onClick = { vm.manualSubmit(r.id) },
                    modifier = Modifier.fillMaxWidth().height(46.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Water700)
                ) { Text(stringResource(R.string.manual_submit)) }
            }

            if (ticket != null) {
                Spacer(Modifier.height(12.dp))
                CardBox {
                    Column(Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                stringResource(R.string.linked_ticket),
                                fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f)
                            )
                            Text("${ticket.progress}%", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Water300)
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            stringResource(ticket.stage.labelRes) + " · " + ticket.agencyName,
                            fontSize = 10.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(8.dp))
                        ProgressBar(ticket.progress, color = Water300, showLabel = false)
                        Spacer(Modifier.height(10.dp))
                        Button(
                            onClick = { vm.go(Screen.TicketDetail(ticket.id)) },
                            modifier = Modifier.fillMaxWidth().height(44.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Water700)
                        ) { Text(stringResource(R.string.view_task_log), fontSize = 12.5.sp) }
                    }
                }
                Spacer(Modifier.height(12.dp))
                CalibrationCard(r.index)
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

/** 视觉结论 vs 机构实测：本产品的核心差异化数据 */
@Composable
private fun CalibrationCard(aiIndex: Int) {
    val measured = com.sewagetrace.app.ai.DemoPhotoFactory.measuredSample(aiIndex)
    CardBox(container = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)) {
        Column(Modifier.padding(14.dp)) {
            Text(
                stringResource(R.string.calibration_title),
                fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(R.string.calibration_desc),
                fontSize = 10.sp, lineHeight = 15.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            measured.forEach { (k, v) ->
                Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                    Text(k, Modifier.weight(1f), fontSize = 10.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(v, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(R.string.calibration_demo),
                fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
    }
}

/** 工单详情：阶段进度 + 任务日志时间线 + 反馈入口 */
@Composable
fun TicketDetailScreen(vm: AppViewModel, ticketId: String) {
    vm.tick
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val t = vm.ticket(ticketId)
    if (t == null) {
        Text(stringResource(R.string.not_found), Modifier.padding(20.dp))
        return
    }
    val logs = vm.logs(ticketId)
    var disputeOpen by remember { mutableStateOf(false) }
    var disputeText by remember { mutableStateOf("") }
    var resolving by remember { mutableStateOf(false) }
    val resolveResult = vm.resolveResult

    Column(Modifier.fillMaxSize()) {
        TopBar(stringResource(R.string.ticket_title)) { vm.back() }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            CardBox {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            t.waterbodyName, fontSize = 16.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f)
                        )
                        if (t.escalated) {
                            Box(
                                Modifier.clip(RoundedCornerShape(6.dp)).background(Color(0xFFDC2626).copy(alpha = 0.16f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) { Text(stringResource(R.string.escalated), fontSize = 8.5.sp, color = Color(0xFFF87171)) }
                        }
                    }
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "${t.id} · ${t.region} · ${t.agencyName}",
                        fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text("${t.progress}", fontSize = 40.sp, fontWeight = FontWeight.Bold, color = Water300)
                        Text(
                            "%", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        Spacer(Modifier.weight(1f))
                        Text(stringResource(t.stage.labelRes), fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                    }
                    Spacer(Modifier.height(6.dp))
                    ProgressBar(t.progress, color = if (t.progress >= 70) Color(0xFF22C55E) else Water300, showLabel = false)
                    Spacer(Modifier.height(10.dp))
                    KV(stringResource(R.string.created_at), DemoRepo.fmtTime(t.createdAt))
                    KV(stringResource(R.string.sla_deadline), DemoRepo.fmtTime(t.slaDeadline))
                    KV(stringResource(R.string.joint_reports), stringResource(R.string.people_count, t.reportCount))
                    if (t.urged) KV(stringResource(R.string.urged_label), stringResource(R.string.urged_value))
                    if (t.isOverdue) Text(
                        stringResource(R.string.overdue_tip, t.overdueDays),
                        fontSize = 10.5.sp, color = MaterialTheme.colorScheme.error
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
            SectionTitle(stringResource(R.string.stage_progress), stringResource(R.string.server_computed))
            Spacer(Modifier.height(8.dp))
            CardBox {
                Column(Modifier.padding(14.dp)) {
                    val cur = TicketStage.entries.indexOf(t.stage)
                    TicketStage.entries.forEachIndexed { idx, s ->
                        val done = idx < cur
                        val active = idx == cur
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                Modifier.size(18.dp).clip(CircleShape)
                                    .background(
                                        when {
                                            done -> Color(0xFF22C55E).copy(alpha = 0.25f)
                                            active -> Water300.copy(alpha = 0.3f)
                                            else -> MaterialTheme.colorScheme.surfaceVariant
                                        }
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    if (done) "✓" else "${idx + 1}", fontSize = 8.5.sp,
                                    color = if (done) Color(0xFF22C55E)
                                    else if (active) Water300 else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(Modifier.width(10.dp))
                            Text(
                                stringResource(s.labelRes), fontSize = 11.5.sp,
                                fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                                color = if (done) MaterialTheme.colorScheme.onSurfaceVariant
                                else MaterialTheme.colorScheme.onBackground
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            SectionTitle(stringResource(R.string.task_log), stringResource(R.string.entry_count, logs.size))
            Spacer(Modifier.height(8.dp))
            logs.sortedByDescending { it.at }.forEach { log ->
                CardBox(
                    container = if (log.isDispute) MaterialTheme.colorScheme.error.copy(alpha = 0.08f)
                    else MaterialTheme.colorScheme.surface
                ) {
                    Column(Modifier.padding(13.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                log.actorName ?: stringResource(log.actorRoleRes),
                                fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold
                            )
                            Spacer(Modifier.width(6.dp))
                            Box(
                                Modifier.clip(RoundedCornerShape(5.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .padding(horizontal = 5.dp, vertical = 1.dp)
                            ) {
                                Text(
                                    stringResource(log.actorRoleRes),
                                    fontSize = 8.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(Modifier.weight(1f))
                            Text("${log.progressAfter}%", fontSize = 10.sp, color = Water300, fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.height(5.dp))
                        val args = log.formatArgs.map { it }.toTypedArray()
                        Text(
                            stringResource(log.textRes, *args),
                            fontSize = 11.sp, lineHeight = 16.sp, color = MaterialTheme.colorScheme.onBackground
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            DemoRepo.fmtTime(log.at) + " · " + stringResource(log.stage.labelRes),
                            fontSize = 8.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(Modifier.height(7.dp))
            }

            Spacer(Modifier.height(8.dp))
            SectionTitle(stringResource(R.string.my_feedback))
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = { disputeOpen = true },
                    modifier = Modifier.weight(1f).height(44.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.85f)
                    )
                ) { Text(stringResource(R.string.dispute_btn), fontSize = 12.sp) }
                Button(
                    onClick = { vm.thankTicket(t.id) },
                    modifier = Modifier.weight(1f).height(44.dp),
                    shape = RoundedCornerShape(12.dp),
                    enabled = !t.thanked,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF22C55E))
                ) { Text(stringResource(if (t.thanked) R.string.thanked else R.string.thank_btn), fontSize = 12.sp) }
            }

            // ── 本机视觉复核（判断工单是否真的处理完） ──
            Spacer(Modifier.height(14.dp))
            SectionTitle(stringResource(R.string.verify_title), stringResource(R.string.verify_sub))
            Spacer(Modifier.height(8.dp))
            CardBox {
                Column(Modifier.padding(14.dp)) {
                    Text(
                        stringResource(R.string.verify_desc),
                        fontSize = 10.5.sp, lineHeight = 16.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(10.dp))
                    Button(
                        onClick = {
                            resolving = true
                            vm.resolveTicketByPhoto(t.id) { resolving = false }
                        },
                        modifier = Modifier.fillMaxWidth().height(46.dp),
                        shape = RoundedCornerShape(12.dp),
                        enabled = !resolving,
                        colors = ButtonDefaults.buttonColors(containerColor = Water700)
                    ) {
                        if (resolving) {
                            CircularProgressIndicator(
                                Modifier.size(16.dp), strokeWidth = 2.dp, color = Color.White
                            )
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(
                            stringResource(if (resolving) R.string.verify_running else R.string.verify_btn),
                            fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold
                        )
                    }
                    resolveResult?.let { res ->
                        Spacer(Modifier.height(10.dp))
                        Divider2()
                        Spacer(Modifier.height(10.dp))
                        val ok = res.cleared
                        Box(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                                .background(
                                    (if (ok) Color(0xFF22C55E) else Color(0xFFF59E0B)).copy(alpha = 0.12f)
                                ).padding(10.dp)
                        ) {
                            Column {
                                Text(
                                    stringResource(
                                        if (ok) R.string.verify_result_cleared else R.string.verify_result_pending
                                    ),
                                    fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold,
                                    color = if (ok) Color(0xFF22C55E) else Color(0xFFF59E0B)
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    stringResource(R.string.verify_result_detail, res.index, res.grade, res.confidencePct),
                                    fontSize = 10.sp, lineHeight = 15.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    stringResource(R.string.verify_result_engine, res.engine),
                                    fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(10.dp))
            Button(
                onClick = { vm.advanceTicket(t.id) },
                modifier = Modifier.fillMaxWidth().height(42.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Water700.copy(alpha = 0.85f))
            ) { Text(stringResource(R.string.simulate_advance), fontSize = 12.sp) }

            Spacer(Modifier.height(24.dp))
        }
    }

    if (disputeOpen) {
        AlertDialog(
            onDismissRequest = { disputeOpen = false },
            title = { Text(stringResource(R.string.dispute_dialog_title)) },
            text = {
                Column {
                    Text(
                        stringResource(R.string.dispute_dialog_desc),
                        fontSize = 11.sp, lineHeight = 16.sp
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = disputeText,
                        onValueChange = { disputeText = it.take(120) },
                        placeholder = { Text(stringResource(R.string.dispute_hint)) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.disputeTicket(t.id, disputeText.ifBlank { ctx.getString(R.string.dispute_default) })
                    disputeText = ""
                    disputeOpen = false
                }) { Text(stringResource(R.string.submit)) }
            },
            dismissButton = { TextButton(onClick = { disputeOpen = false }) { Text(stringResource(R.string.cancel)) } }
        )
    }
}

@Composable
fun StandardsScreen(vm: AppViewModel) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    Column(Modifier.fillMaxSize()) {
        TopBar(stringResource(R.string.about_standards)) { vm.back() }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            CardBox(container = Color(0xFFF59E0B).copy(alpha = 0.10f)) {
                Text(
                    stringResource(R.string.standards_warning),
                    Modifier.padding(14.dp), fontSize = 10.5.sp, lineHeight = 16.sp, color = Color(0xFFF59E0B)
                )
            }
            Spacer(Modifier.height(12.dp))
            listOf(
                Triple("MSMA / EQA 1974", "Environmental Quality Act 1974 (Malaysia)", R.string.std_msma_desc),
                Triple("INWQS", "Interim National Water Quality Standards", R.string.std_inwqs_desc),
                Triple("Std A / B", "Sewage & Industrial Effluent Regulations 2009", R.string.std_sewage_desc),
                Triple("DOE", "Department of Environment, Malaysia", R.string.std_doe_desc)
            ).forEach { (code, name, desc) ->
                CardBox {
                    Column(Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(code, fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.width(8.dp))
                            Box(
                                Modifier.clip(RoundedCornerShape(5.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .padding(horizontal = 5.dp, vertical = 1.dp)
                            ) {
                                Text(
                                    stringResource(R.string.limit_pending),
                                    fontSize = 8.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Text(name, fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            ctx.getString(desc), fontSize = 10.sp, lineHeight = 15.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
            Spacer(Modifier.height(8.dp))
            CardBox(container = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)) {
                Column(Modifier.padding(14.dp)) {
                    Text(
                        stringResource(R.string.why_no_ratio_title),
                        fontSize = 12.sp, fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        stringResource(R.string.why_no_ratio_desc),
                        fontSize = 10.sp, lineHeight = 15.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
fun AboutScreen(vm: AppViewModel) {
    Column(Modifier.fillMaxSize()) {
        TopBar(stringResource(R.string.about_title)) { vm.back() }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            CardBox {
                Column(Modifier.padding(16.dp)) {
                    Text(stringResource(R.string.app_name) + " v1.0-demo", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        stringResource(R.string.about_engine_line, vm.engineLine),
                        fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(12.dp))
                    Divider2()
                    Spacer(Modifier.height(12.dp))
                    Text(
                        stringResource(R.string.disclaimer_title),
                        fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        stringResource(R.string.disclaimer_body),
                        fontSize = 10.5.sp, lineHeight = 17.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            CardBox {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        stringResource(R.string.data_handling_title),
                        fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(6.dp))
                    listOf(
                        R.string.data_1, R.string.data_2, R.string.data_3,
                        R.string.data_4, R.string.data_5
                    ).forEach { id ->
                        Row(Modifier.padding(vertical = 3.dp)) {
                            Text("· ", fontSize = 11.sp, color = Water300)
                            Text(
                                stringResource(id), fontSize = 10.5.sp, lineHeight = 16.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = {
                    DemoRepo.logout()
                    vm.logout()
                },
                modifier = Modifier.fillMaxWidth().height(44.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.85f)
                )
            ) { Text(stringResource(R.string.logout_clear), fontSize = 12.5.sp) }
            Spacer(Modifier.height(24.dp))
        }
    }
}

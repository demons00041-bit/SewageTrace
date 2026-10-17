package com.sewagetrace.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sewagetrace.app.R
import com.sewagetrace.app.ai.DemoScene
import com.sewagetrace.app.data.AnalysisMode
import com.sewagetrace.app.data.SubmitOutcome
import com.sewagetrace.app.data.engineLabel
import com.sewagetrace.app.model.Confidence
import com.sewagetrace.app.model.Report
import com.sewagetrace.app.model.SeverityLevel
import com.sewagetrace.app.ui.AppViewModel
import com.sewagetrace.app.ui.DetectPhase
import com.sewagetrace.app.ui.components.CardBox
import com.sewagetrace.app.ui.components.Divider2
import com.sewagetrace.app.ui.components.ProgressBar
import com.sewagetrace.app.ui.components.SectionTitle
import com.sewagetrace.app.ui.components.SeverityChip
import com.sewagetrace.app.ui.theme.Water300
import com.sewagetrace.app.ui.theme.Water700
import kotlinx.coroutines.delay

@Composable
fun DetectScreen(vm: AppViewModel) {
    vm.tick

    if (vm.phase == DetectPhase.IDLE) {
        DetectIdle(vm)
        return
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)
    ) {
        Spacer(Modifier.height(14.dp))
        Row(
            Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.detect_result_title), style = MaterialTheme.typography.headlineSmall)
                Text(
                    stringResource(R.string.detect_result_sub),
                    fontSize = 10.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            TextButton(onClick = { vm.resetDetect() }) {
                Text(stringResource(R.string.retake), fontSize = 13.sp)
            }
        }
        Spacer(Modifier.height(10.dp))

        PhotoPreview(vm)

        Spacer(Modifier.height(14.dp))

        if (vm.phase == DetectPhase.ANALYZING) {
            CardBox {
                Column(Modifier.padding(18.dp)) {
                    Text(stringResource(R.string.analyzing), style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(10.dp))
                    val p = remember { mutableStateOf(0f) }
                    LaunchedEffect(Unit) {
                        repeat(24) {
                            delay(60)
                            p.value = (p.value + 0.040f).coerceAtMost(1f)
                        }
                        vm.runAnalysis()
                    }
                    LinearProgressIndicator(
                        progress = { p.value },
                        modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(99.dp))
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        stringResource(R.string.analyzing_steps),
                        fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            return@Column
        }

        val a = vm.analysis
        if (a == null) {
            CardBox {
                Column(Modifier.padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Button(
                        onClick = { vm.runAnalysis() },
                        colors = ButtonDefaults.buttonColors(containerColor = Water700),
                        modifier = Modifier.fillMaxWidth().height(48.dp)
                    ) { Text(stringResource(R.string.start_detect)) }
                }
            }
            return@Column
        }

        ResultCard(
            index = a.index, level = a.level, confidence = a.confidence,
            typeText = stringResource(a.suspectedType.labelRes), model = a.engineLabel()
        )

        vm.fallback?.let { fb ->
            Spacer(Modifier.height(10.dp))
            CardBox(container = Color(0xFFF59E0B).copy(alpha = 0.10f)) {
                Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
                    Text("!", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFFB45309))
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.fallback_title),
                            fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFFB45309)
                        )
                        Spacer(Modifier.height(3.dp))
                        Text(
                            if (fb.args.isEmpty()) stringResource(fb.textRes)
                            else stringResource(fb.textRes, *fb.args.toTypedArray()),
                            fontSize = 11.sp, lineHeight = 17.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(14.dp))
        CardBox {
            Column(Modifier.padding(16.dp)) {
                Text(stringResource(R.string.report_params), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.auto_report_gov), fontSize = 13.5.sp)
                        Text(
                            stringResource(R.string.auto_report_gov_desc),
                            fontSize = 10.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(checked = vm.autoReport, onCheckedChange = { vm.updateAutoReport(it) })
                }
                Divider2()
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.loc_precision), fontSize = 13.sp)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(top = 6.dp)
                ) {
                    listOf(
                        1000 to stringResource(R.string.prec_1km),
                        500 to stringResource(R.string.prec_500m),
                        50 to stringResource(R.string.prec_50m)
                    ).forEach { (v, label) ->
                        val sel = vm.locationPrecision == v
                        Box(
                            Modifier.clip(RoundedCornerShape(10.dp))
                                .background(
                                    if (sel) Water700.copy(alpha = 0.22f)
                                    else MaterialTheme.colorScheme.surfaceVariant
                                )
                                .border(1.dp, if (sel) Water700 else Color.Transparent, RoundedCornerShape(10.dp))
                                .clickable { vm.updateLocationPrecision(v) }
                                .padding(horizontal = 10.dp, vertical = 7.dp)
                        ) {
                            Text(
                                label, fontSize = 11.sp,
                                color = if (sel) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                OutlinedButton(
                    onClick = { vm.submit() },
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text(
                        stringResource(
                            if (vm.autoReport) R.string.confirm_and_report else R.string.confirm_save_local
                        ),
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        vm.outcome?.let { o ->
            Spacer(Modifier.height(14.dp))
            OutcomeCard(vm, o)
        }

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun PhotoPreview(vm: AppViewModel) {
    val path = vm.photoPath
    Box(
        Modifier.fillMaxWidth().aspectRatio(4f / 3f).clip(RoundedCornerShape(18.dp))
            .background(Color(0xFF1A2B33))
    ) {
        if (path != null) {
            val bmp = remember(path) {
                runCatching {
                    android.graphics.BitmapFactory.decodeFile(
                        path, android.graphics.BitmapFactory.Options().apply { inSampleSize = 2 }
                    )
                }.getOrNull()
            }
            if (bmp != null) {
                androidx.compose.foundation.Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = stringResource(R.string.captured_frame),
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }
        }
        Box(
            Modifier.fillMaxSize().padding(18.dp)
                .border(1.dp, Color.White.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
        )
        Box(
            Modifier.align(Alignment.TopStart).padding(14.dp)
                .clip(RoundedCornerShape(8.dp)).background(Color.Black.copy(alpha = 0.55f))
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            val sceneTitle = vm.scene?.let { stringResource(it.titleRes) } ?: ""
            Text(
                stringResource(R.string.scene_sample_tag, sceneTitle),
                fontSize = 10.sp, color = Color.White
            )
        }
        Box(
            Modifier.align(Alignment.BottomEnd).padding(14.dp)
                .clip(RoundedCornerShape(8.dp)).background(Color.Black.copy(alpha = 0.55f))
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Text(
                stringResource(R.string.watermark_hint, vm.locationPrecision),
                fontSize = 9.5.sp, color = Color(0xFFCFE3E9)
            )
        }
    }
}

@Composable
fun ResultCard(
    index: Int,
    level: SeverityLevel,
    confidence: Confidence,
    typeText: String,
    model: String
) {
    CardBox {
        Column(Modifier.padding(18.dp)) {
            Row(
                Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column {
                    Text(
                        stringResource(R.string.pollution_index),
                        fontSize = 11.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text("$index", fontSize = 46.sp, fontWeight = FontWeight.Bold, color = level.color)
                        Text(
                            "/100", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 9.dp)
                        )
                    }
                }
                SeverityChip(level)
            }
            Spacer(Modifier.height(8.dp))
            ProgressBar(index, color = level.color, height = 8, showLabel = false)
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SmallFact(
                    stringResource(R.string.confidence_label),
                    stringResource(confidence.labelRes) + " · " + stringResource(confidence.descRes),
                    Modifier.weight(1f)
                )
                SmallFact(stringResource(R.string.suspected_type), typeText, Modifier.weight(1f))
            }
            Spacer(Modifier.height(10.dp))
            Text(
                stringResource(R.string.index_explainer),
                fontSize = 10.sp, lineHeight = 15.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.model_line, model),
                fontSize = 9.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
fun SmallFact(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier.clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        Text(label, fontSize = 9.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontSize = 12.sp, lineHeight = 17.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun OutcomeCard(vm: AppViewModel, o: SubmitOutcome) {
    val ctx = LocalContext.current
    val title: String
    val body: String
    val color: Color
    when (o) {
        is SubmitOutcome.Reported -> {
            title = ctx.getString(if (o.escalated) R.string.outcome_escalated else R.string.outcome_reported)
            val agency = o.ticket?.agencyName ?: ctx.getString(R.string.platform_pool)
            body = ctx.getString(R.string.outcome_reported_body, o.ticket?.id ?: "-", agency) +
                if (o.escalated) ctx.getString(R.string.outcome_escalated_tail) else ""
            color = Color(0xFF22C55E)
        }
        is SubmitOutcome.Blocked -> {
            title = ctx.getString(R.string.outcome_blocked)
            body = ctx.getString(o.reasonRes)
            color = Color(0xFFF59E0B)
        }
        is SubmitOutcome.DraftOnly -> {
            title = ctx.getString(R.string.outcome_draft)
            body = ctx.getString(o.reasonRes)
            color = Color(0xFFF59E0B)
        }
        is SubmitOutcome.Failed -> {
            title = ctx.getString(R.string.outcome_failed)
            body = o.detail
            color = MaterialTheme.colorScheme.error
        }
    }
    CardBox(container = color.copy(alpha = 0.10f)) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(7.dp).clip(CircleShape).background(color))
                Spacer(Modifier.width(8.dp))
                Text(title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = color)
            }
            Spacer(Modifier.height(6.dp))
            Text(body, fontSize = 12.sp, lineHeight = 18.sp, color = MaterialTheme.colorScheme.onBackground)
            if (o is SubmitOutcome.Reported) {
                Spacer(Modifier.height(10.dp))
                Button(
                    onClick = { vm.openSubmitted() },
                    colors = ButtonDefaults.buttonColors(containerColor = color.copy(alpha = 0.9f)),
                    modifier = Modifier.fillMaxWidth().height(44.dp),
                    shape = RoundedCornerShape(12.dp)
                ) { Text(stringResource(R.string.view_report_and_ticket), fontSize = 13.sp) }
            }
            if (o is SubmitOutcome.Blocked || o is SubmitOutcome.DraftOnly) {
                Spacer(Modifier.height(10.dp))
                TextButton(onClick = { vm.resetDetect() }) {
                    Text(stringResource(R.string.back_to_retake), fontSize = 12.5.sp)
                }
            }
        }
    }
}

@Composable
private fun DetectIdle(vm: AppViewModel) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)
    ) {
        Spacer(Modifier.height(16.dp))
        Row(
            Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.tab_detect), style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(2.dp))
                Text(
                    stringResource(R.string.detect_idle_sub),
                    fontSize = 11.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                vm.user?.nickname ?: "",
                fontSize = 11.sp, color = MaterialTheme.colorScheme.primary
            )
        }

        Spacer(Modifier.height(16.dp))

        Box(
            Modifier.fillMaxWidth().height(210.dp).clip(RoundedCornerShape(22.dp))
                .background(Brush.linearGradient(listOf(Water700, Color(0xFF0E5A6B))))
                .clickable { vm.shootRandom() }
        ) {
            Row(Modifier.fillMaxSize().padding(22.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.detect_cta), fontSize = 22.sp,
                        fontWeight = FontWeight.Bold, color = Color.White
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.detect_cta_sub),
                        fontSize = 12.sp, lineHeight = 19.sp, color = Color(0xFFCDEAF1)
                    )
                }
                Box(
                    Modifier.size(92.dp).clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.16f))
                        .border(2.dp, Color.White.copy(alpha = 0.7f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.PhotoCamera, null, tint = Color.White,
                        modifier = Modifier.size(40.dp)
                    )
                }
            }
        }

        // 识别引擎状态条
        Spacer(Modifier.height(12.dp))
        CardBox(
            container = if (vm.cloudReady) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            else Color(0xFFF59E0B).copy(alpha = 0.10f)
        ) {
            Column(Modifier.padding(13.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(7.dp).clip(CircleShape)
                            .background(if (vm.cloudReady) Color(0xFF22C55E) else Color(0xFFF59E0B))
                    )
                    Spacer(Modifier.width(7.dp))
                    Text(
                        stringResource(vm.modeRes), fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f)
                    )
                    if (vm.analyzing && vm.phase == DetectPhase.IDLE) {
                        CircularProgressIndicator(Modifier.size(13.dp), strokeWidth = 1.8.dp)
                    }
                }
                Spacer(Modifier.height(3.dp))
                Text(
                    stringResource(vm.engineHintRes),
                    fontSize = 10.sp, lineHeight = 15.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (vm.mode == AnalysisMode.CLOUD && !vm.cloudReady) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { vm.go(com.sewagetrace.app.ui.Screen.ApiKey) },
                        modifier = Modifier.fillMaxWidth().height(40.dp),
                        shape = RoundedCornerShape(10.dp)
                    ) { Text(stringResource(R.string.cloud_add_key), fontSize = 12.sp) }
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        SectionTitle(stringResource(R.string.pick_scene), stringResource(R.string.pick_scene_sub))
        Spacer(Modifier.height(8.dp))
        DemoScene.entries.forEach { s ->
            SceneRow(s) { vm.shoot(s) }
            Spacer(Modifier.height(8.dp))
        }

        Spacer(Modifier.height(8.dp))
        CardBox(container = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)) {
            Column(Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.WaterDrop, null, tint = Water300, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.did_you_know), fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    stringResource(R.string.did_you_know_body),
                    fontSize = 11.sp, lineHeight = 17.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun SceneRow(scene: DemoScene, onClick: () -> Unit) {
    val isBad = scene.prior.qualityIssue >= 0.5f
    CardBox {
        Row(
            Modifier.fillMaxWidth().clickable(onClick = onClick).padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(42.dp).clip(RoundedCornerShape(12.dp))
                    .background(
                        if (isBad) Color(0xFFF59E0B).copy(alpha = 0.18f)
                        else Water700.copy(alpha = 0.18f)
                    ),
                contentAlignment = Alignment.Center
            ) { Text(if (isBad) "⚠" else "💧", fontSize = 18.sp) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(scene.titleRes), fontSize = 13.5.sp, fontWeight = FontWeight.Medium)
                Text(
                    stringResource(scene.subtitleRes), fontSize = 10.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(stringResource(R.string.shoot_arrow), fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
        }
    }
}

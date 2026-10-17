package com.sewagetrace.app.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sewagetrace.app.R
import com.sewagetrace.app.data.DemoRepo
import com.sewagetrace.app.data.StatsService
import com.sewagetrace.app.model.Agency
import com.sewagetrace.app.model.AgencyType
import com.sewagetrace.app.model.Report
import com.sewagetrace.app.model.ReportStatus
import com.sewagetrace.app.ui.AppViewModel
import com.sewagetrace.app.ui.Screen
import com.sewagetrace.app.ui.components.CardBox
import com.sewagetrace.app.ui.theme.Water300
import com.sewagetrace.app.ui.theme.Water700
import kotlin.math.cos
import kotlin.math.sin

/**
 * 地图页：离线矢量示意地图。
 *
 * 演示版不接入在线瓦片（零网络 / 免密钥），但保留真实地图所需的交互契约：
 * 手势缩放、POI 分类图层、点击命中、位置隐私圈、来源署名。
 * 正式版把 MapCanvas 内部替换为 MapLibre 即可，外部契约不变。
 */
@Composable
fun MapScreen(vm: AppViewModel) {
    vm.tick
    var zoom by remember { mutableStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    val centerLat = 3.1500
    val centerLng = 101.7000

    val agencies = StatsService.agencies(vm.agencyFilter, centerLat, centerLng)
    val reports = DemoRepo.reports.filter { it.status != ReportStatus.DRAFT }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilterChipMini(stringResource(R.string.filter_all), vm.agencyFilter == null) { vm.agencyFilter = null }
            FilterChipMini(stringResource(R.string.filter_gov), vm.agencyFilter == AgencyType.GOVERNMENT) { vm.agencyFilter = AgencyType.GOVERNMENT }
            FilterChipMini(stringResource(R.string.filter_lab), vm.agencyFilter == AgencyType.LAB) { vm.agencyFilter = AgencyType.LAB }
            FilterChipMini(stringResource(R.string.filter_wwtp), vm.agencyFilter == AgencyType.WWTP) { vm.agencyFilter = AgencyType.WWTP }
            FilterChipMini(stringResource(R.string.filter_station), vm.agencyFilter == AgencyType.STATION) { vm.agencyFilter = AgencyType.STATION }
        }

        Box(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 12.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(Color(0xFF0D1E26))
        ) {
            // 投影：世界坐标 -> 画布像素
            val spanDeg = 0.16f / zoom
            fun project(lat: Double, lng: Double): Offset {
                val x = ((lng - centerLng) / spanDeg + 0.5).toFloat()
                val y = (0.5 - (lat - centerLat) / spanDeg / 1.6).toFloat()
                return Offset(x, y)
            }

            var boxSize by remember { mutableStateOf(Offset(1f, 1f)) }

            Canvas(
                Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTransformGestures { _, p, z, _ ->
                            zoom = (zoom * z).coerceIn(0.8f, 6f)
                            pan += p
                        }
                    }
                    .pointerInput(zoom, pan) {
                        detectTapGestures { pos ->
                            val px = pos.x / size.width.toFloat()
                            val py = pos.y / size.height.toFloat()
                            val cands = agencies.map { it.first.id to project(it.first.lat, it.first.lng) } +
                                if (vm.showReportsOnMap) reports.map { it.id to project(it.lat, it.lng) } else emptyList()
                            val hit = cands.minByOrNull { (_, o) ->
                                val dx = (o.x + pan.x / size.width.toFloat()) - px
                                val dy = (o.y + pan.y / size.height.toFloat()) - py
                                dx * dx + dy * dy
                            }?.takeIf { (_, o) ->
                                val dx = (o.x + pan.x / size.width.toFloat()) - px
                                val dy = (o.y + pan.y / size.height.toFloat()) - py
                                dx * dx + dy * dy < 0.004f
                            }?.first
                            if (hit != null && DemoRepo.agencies.any { it.id == hit }) {
                                vm.go(Screen.AgencyDetail(hit))
                            }
                        }
                    }
            ) {
                boxSize = Offset(size.width, size.height)
                drawRect(Brush.verticalGradient(listOf(Color(0xFF0D1E26), Color(0xFF102A33))))

                val rnd = java.util.Random(7)
                repeat(28) {
                    drawRect(
                        color = Color(0xFF16303A).copy(alpha = 0.7f),
                        topLeft = Offset(rnd.nextFloat() * size.width, rnd.nextFloat() * size.height),
                        size = Size(28f + rnd.nextFloat() * 70f, 22f + rnd.nextFloat() * 60f)
                    )
                }

                val river = Path().apply {
                    moveTo(-20f, size.height * 0.42f)
                    cubicTo(
                        size.width * 0.25f, size.height * 0.30f,
                        size.width * 0.45f, size.height * 0.62f,
                        size.width * 0.72f, size.height * 0.50f
                    )
                    cubicTo(
                        size.width * 0.85f, size.height * 0.44f,
                        size.width * 0.95f, size.height * 0.56f,
                        size.width + 20f, size.height * 0.48f
                    )
                }
                drawPath(river, Color(0xFF10495C), style = Stroke(width = 26f * zoom.coerceAtMost(2.2f)))
                drawPath(river, Color(0xFF1A6376), style = Stroke(width = 3f))

                for (i in 1..6) {
                    val gx = size.width * i / 7f
                    val gy = size.height * i / 7f
                    drawLine(Color(0x14FFFFFF), Offset(gx, 0f), Offset(gx, size.height), 1f)
                    drawLine(Color(0x14FFFFFF), Offset(0f, gy), Offset(size.width, gy), 1f)
                }

                // 我的位置 + 精度圈（精度越大圈越大，直观说明隐私降级）
                val me = project(3.1578, 101.7118)
                val mePos = Offset(me.x * size.width + pan.x, me.y * size.height + pan.y)
                val circleR = (vm.locationPrecision / 50f) * 26f
                drawCircle(Water300.copy(alpha = 0.10f), circleR, mePos)
                drawCircle(Water300.copy(alpha = 0.45f), circleR, mePos, style = Stroke(1.5f))
                drawCircle(Color.White, 5f, mePos)
                drawCircle(Water300, 5f, mePos, style = Stroke(2f))

                if (vm.showReportsOnMap) {
                    reports.forEach { r ->
                        val p = project(r.lat, r.lng)
                        val o = Offset(p.x * size.width + pan.x, p.y * size.height + pan.y)
                        drawCircle(r.level.color.copy(alpha = 0.25f), 16f, o)
                        drawCircle(r.level.color, 8f, o)
                    }
                }

                agencies.forEach { (a, _) ->
                    val p = project(a.lat, a.lng)
                    val o = Offset(p.x * size.width + pan.x, p.y * size.height + pan.y)
                    val c = typeColor(a.type)
                    drawCircle(c.copy(alpha = 0.18f), 20f, o)
                    drawCircle(c, 10f, o)
                    drawCircle(Color(0xFF0D1E26), 5f, o)
                }
            }

            Column(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(10.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.Black.copy(alpha = 0.55f))
                    .padding(10.dp)
            ) {
                LegendRow(Color(0xFF60A5FA), stringResource(R.string.legend_gov))
                LegendRow(Color(0xFF34D399), stringResource(R.string.legend_lab))
                LegendRow(Color(0xFFA78BFA), stringResource(R.string.legend_wwtp))
                LegendRow(Color(0xFFFBBF24), stringResource(R.string.legend_station))
                LegendRow(Color(0xFFDC2626), stringResource(R.string.legend_reports))
            }

            Column(
                Modifier.align(Alignment.BottomEnd).padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                MapBtn(Icons.Filled.Add) { zoom = (zoom * 1.4f).coerceAtMost(6f) }
                MapBtn(Icons.Filled.Remove) { zoom = (zoom / 1.4f).coerceAtLeast(0.8f) }
            }

            Box(
                Modifier.align(Alignment.BottomStart).padding(10.dp)
                    .clip(RoundedCornerShape(8.dp)).background(Color.Black.copy(alpha = 0.45f))
                    .clickable { vm.showReportsOnMap = !vm.showReportsOnMap }
                    .padding(horizontal = 8.dp, vertical = 5.dp)
            ) {
                Text(
                    if (vm.showReportsOnMap) stringResource(R.string.map_hide_reports) else stringResource(R.string.map_show_reports),
                    fontSize = 9.5.sp, color = Water300
                )
            }

            Text(
                stringResource(R.string.map_basemap_note),
                Modifier.align(Alignment.TopStart).padding(10.dp),
                fontSize = 8.sp,
                color = Color.White.copy(alpha = 0.45f)
            )
        }

        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(stringResource(R.string.map_nearby), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Text(stringResource(R.string.map_sorted, agencies.size), fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Column(Modifier.fillMaxWidth().weight(1.15f).padding(horizontal = 16.dp)) {
            androidx.compose.foundation.lazy.LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(agencies.size) { idx ->
                    val (a, dist) = agencies[idx]
                    AgencyRow(a, dist) { vm.go(Screen.AgencyDetail(a.id)) }
                }
            }
        }
    }
}

private fun typeColor(t: AgencyType): Color = when (t) {
    AgencyType.GOVERNMENT -> Color(0xFF60A5FA)
    AgencyType.LAB -> Color(0xFF34D399)
    AgencyType.WWTP -> Color(0xFFA78BFA)
    AgencyType.STATION -> Color(0xFFFBBF24)
}

@Composable
private fun LegendRow(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 1.dp)) {
        Box(Modifier.size(7.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(6.dp))
        Text(label, fontSize = 9.sp, color = Color.White.copy(alpha = 0.8f))
    }
}

@Composable
private fun MapBtn(icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Box(
        Modifier
            .size(34.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Color.Black.copy(alpha = 0.5f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) { Icon(icon, null, tint = Color.White, modifier = Modifier.size(18.dp)) }
}

@Composable
fun FilterChipMini(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(if (selected) Water700.copy(alpha = 0.3f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .border(1.dp, if (selected) Water700 else Color.Transparent, RoundedCornerShape(999.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 11.dp, vertical = 6.dp)
    ) {
        Text(
            label, fontSize = 11.sp,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}

@Composable
fun AgencyRow(a: Agency, distMeters: Int, onClick: () -> Unit) {
    CardBox {
        Row(
            Modifier.fillMaxWidth().clickable(onClick = onClick).padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(typeColor(a.type).copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) { Text(a.type.icon, fontSize = 17.sp) }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(a.name, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1)
                Text(
                    stringResource(a.type.labelRes) + " · " + fmtDist(distMeters),
                    fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (a.type == AgencyType.LAB) {
                Box(
                    Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF34D399).copy(alpha = 0.15f))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) { Text(stringResource(R.string.agency_booking_title), fontSize = 9.5.sp, color = Color(0xFF34D399)) }
            } else {
                Text("›", fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private fun fmtDist(m: Int): String = if (m >= 1000) "%.1f km".format(m / 1000.0) else "$m m"

package com.sewagetrace.app.ui.screens

import android.content.Intent
import android.graphics.RectF
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.ZoomOutMap
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.sewagetrace.app.R
import com.sewagetrace.app.data.DemoRepo
import com.sewagetrace.app.data.StatsService
import com.sewagetrace.app.data.TicketService
import com.sewagetrace.app.map.MapConfig
import com.sewagetrace.app.model.Agency
import com.sewagetrace.app.model.AgencyType
import com.sewagetrace.app.model.Report
import com.sewagetrace.app.model.ReportStatus
import com.sewagetrace.app.ui.AppViewModel
import com.sewagetrace.app.ui.Screen
import com.sewagetrace.app.ui.components.CardBox
import com.sewagetrace.app.ui.components.levelName
import com.sewagetrace.app.ui.theme.Water700
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapLibreMapOptions
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point
import org.maplibre.geojson.Polygon
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * 地图页：MapLibre 真实矢量地图 + 底部面板（布局参考 Jalan Pulang）。
 *
 * 分工（和 地图模块实现方案.md 的思路一致）：
 *   MapLibre 只负责画 —— 底图、3D 楼块、点位（GeoJSON 图层，不是一个个 Marker 对象）
 *   Compose 负责壳 —— 顶部筛选、右侧控制、底部面板、选中卡片
 *   两边只通过 [MapPin] 和几个 GeoJSON source 同步。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(vm: AppViewModel) {
    vm.tick
    val context = LocalContext.current
    val density = LocalDensity.current

    val me = LatLng(AppViewModel.DEMO_LAT, AppViewModel.DEMO_LNG)
    val agencies = StatsService.agencies(vm.agencyFilter, me.latitude, me.longitude)
    val reports = if (vm.showReportsOnMap) DemoRepo.reports.filter { it.status != ReportStatus.DRAFT } else emptyList()

    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    var styleReady by remember { mutableStateOf(false) }
    var loadFailed by remember { mutableStateOf(false) }
    var is3d by remember { mutableStateOf(true) }
    var selected by remember { mutableStateOf<MapPin?>(null) }
    var didInitialFit by remember { mutableStateOf(false) }

    val peek = 230.dp
    val peekPx = with(density) { peek.toPx() }.toDouble()
    val sheetState = rememberBottomSheetScaffoldState()

    // ---- 相机操作（都把底部面板高度算进 padding，避免点位被面板挡住）----
    fun flyTo(target: LatLng, zoom: Double) {
        val m = map ?: return
        val pos = CameraPosition.Builder()
            .target(target)
            .zoom(zoom)
            .tilt(if (is3d) MapConfig.TILT_3D else 0.0)
            .bearing(if (is3d) MapConfig.BEARING_3D else 0.0)
            .padding(0.0, 0.0, 0.0, peekPx)
            .build()
        m.animateCamera(CameraUpdateFactory.newCameraPosition(pos), MapConfig.ANIM_MS)
    }

    fun fitAll() {
        val m = map ?: return
        val pts = agencies.map { LatLng(it.first.lat, it.first.lng) } +
            reports.map { LatLng(it.lat, it.lng) } + me
        if (pts.size < 2) { flyTo(me, MapConfig.DEFAULT_ZOOM); return }
        val bounds = LatLngBounds.Builder().includes(pts).build()
        val pad = with(density) { 56.dp.toPx() }.toInt()
        val cam = m.getCameraForLatLngBounds(
            bounds,
            intArrayOf(pad, pad * 2, pad, peekPx.toInt() + pad),
            if (is3d) MapConfig.BEARING_3D else 0.0,
            if (is3d) MapConfig.TILT_3D else 0.0
        ) ?: return
        m.animateCamera(CameraUpdateFactory.newCameraPosition(cam), MapConfig.ANIM_MS)
    }

    fun select(pin: MapPin?) {
        selected = pin
        when (pin) {
            is MapPin.AgencyPin -> DemoRepo.agencies.firstOrNull { it.id == pin.id }
                ?.let { flyTo(LatLng(it.lat, it.lng), MapConfig.FOCUS_ZOOM) }
            is MapPin.ReportPin -> DemoRepo.reports.firstOrNull { it.id == pin.id }
                ?.let { flyTo(LatLng(it.lat, it.lng), MapConfig.FOCUS_ZOOM) }
            null -> Unit
        }
    }

    val onPinClick by rememberUpdatedState<(MapPin?) -> Unit> { select(it) }

    // ---- 数据 -> GeoJSON：每次数据变化只 setGeoJson，不重建图层 ----
    LaunchedEffect(styleReady, agencies, reports, vm.locationPrecision, selected) {
        val style = map?.style ?: return@LaunchedEffect
        if (!styleReady) return@LaunchedEffect
        style.getSourceAs<GeoJsonSource>(SRC_PINS)?.setGeoJson(pinsToGeoJson(agencies.map { it.first }, reports))
        style.getSourceAs<GeoJsonSource>(SRC_ME)?.setGeoJson(
            FeatureCollection.fromFeatures(listOf(Feature.fromGeometry(Point.fromLngLat(me.longitude, me.latitude))))
        )
        style.getSourceAs<GeoJsonSource>(SRC_ACCURACY)?.setGeoJson(
            Feature.fromGeometry(circlePolygon(me, vm.locationPrecision.toDouble()))
        )
        val sel = selected?.let { pin -> pinLatLng(pin) }
        style.getSourceAs<GeoJsonSource>(SRC_SELECTED)?.setGeoJson(
            FeatureCollection.fromFeatures(
                listOfNotNull(sel?.let { Feature.fromGeometry(Point.fromLngLat(it.longitude, it.latitude)) })
            )
        )
        if (!didInitialFit) { didInitialFit = true; fitAll() }
    }

    // 切 2D/3D 时只改倾斜和朝向，保留当前中心与缩放
    LaunchedEffect(is3d) {
        val m = map ?: return@LaunchedEffect
        val cur = m.cameraPosition
        val pos = CameraPosition.Builder(cur)
            .tilt(if (is3d) MapConfig.TILT_3D else 0.0)
            .bearing(if (is3d) MapConfig.BEARING_3D else 0.0)
            .build()
        m.animateCamera(CameraUpdateFactory.newCameraPosition(pos), MapConfig.ANIM_MS)
    }

    val mapView = rememberMapViewWithLifecycle()

    BottomSheetScaffold(
        scaffoldState = sheetState,
        sheetPeekHeight = peek,
        sheetContainerColor = MaterialTheme.colorScheme.surface,
        sheetShadowElevation = 12.dp,
        sheetContent = {
            MapSheet(
                vm = vm,
                agencies = agencies,
                reports = reports,
                selected = selected,
                onClearSelection = { selected = null },
                onAgencyRow = { select(MapPin.AgencyPin(it.id)) }
            )
        }
    ) {
        Box(Modifier.fillMaxSize()) {
            AndroidView(
                factory = {
                    mapView.apply {
                        addOnDidFailLoadingMapListener { loadFailed = true }
                        getMapAsync { m ->
                            map = m
                            m.setMinZoomPreference(MapConfig.MIN_ZOOM)
                            m.setMaxZoomPreference(MapConfig.MAX_ZOOM)
                            m.uiSettings.isAttributionEnabled = false
                            m.uiSettings.isLogoEnabled = false
                            m.uiSettings.setCompassMargins(0, with(density) { 64.dp.toPx() }.toInt(), with(density) { 64.dp.toPx() }.toInt(), 0)
                            m.cameraPosition = CameraPosition.Builder()
                                .target(me)
                                .zoom(MapConfig.DEFAULT_ZOOM)
                                .tilt(MapConfig.TILT_3D)
                                .bearing(MapConfig.BEARING_3D)
                                .build()
                            loadStyle(m) { styleReady = true; loadFailed = false }
                            m.addOnMapClickListener { latLng ->
                                val p = m.projection.toScreenLocation(latLng)
                                val r = with(density) { 14.dp.toPx() }
                                val hit = m.queryRenderedFeatures(
                                    RectF(p.x - r, p.y - r, p.x + r, p.y + r),
                                    LYR_PINS
                                ).firstOrNull()
                                val pin = hit?.let { f ->
                                    val id = f.getStringProperty(PROP_ID)
                                    if (f.getStringProperty(PROP_KIND) == KIND_AGENCY) MapPin.AgencyPin(id) else MapPin.ReportPin(id)
                                }
                                onPinClick(pin)
                                pin != null
                            }
                        }
                    }
                },
                modifier = Modifier.fillMaxSize()
            )

            // 顶部：分类筛选（悬浮胶囊，横向可滑）
            Row(
                Modifier
                    .align(Alignment.TopStart)
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                MapChip(stringResource(R.string.filter_all), null, vm.agencyFilter == null) { vm.agencyFilter = null }
                MapChip(stringResource(R.string.filter_gov), typeColor(AgencyType.GOVERNMENT), vm.agencyFilter == AgencyType.GOVERNMENT) { vm.agencyFilter = AgencyType.GOVERNMENT }
                MapChip(stringResource(R.string.filter_lab), typeColor(AgencyType.LAB), vm.agencyFilter == AgencyType.LAB) { vm.agencyFilter = AgencyType.LAB }
                MapChip(stringResource(R.string.filter_wwtp), typeColor(AgencyType.WWTP), vm.agencyFilter == AgencyType.WWTP) { vm.agencyFilter = AgencyType.WWTP }
                MapChip(stringResource(R.string.filter_station), typeColor(AgencyType.STATION), vm.agencyFilter == AgencyType.STATION) { vm.agencyFilter = AgencyType.STATION }
                MapChip(
                    if (vm.showReportsOnMap) stringResource(R.string.map_hide_reports) else stringResource(R.string.map_show_reports),
                    REPORT_COLOR, vm.showReportsOnMap
                ) { vm.showReportsOnMap = !vm.showReportsOnMap }
            }

            // 右侧：3D / 全览 / 定位 / 缩放
            Column(
                Modifier.align(Alignment.CenterEnd).padding(end = 12.dp, bottom = peek / 2),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MapTextBtn(if (is3d) "2D" else "3D", stringResource(if (is3d) R.string.map_switch_2d else R.string.map_switch_3d)) { is3d = !is3d }
                MapIconBtn(Icons.Filled.ZoomOutMap, stringResource(R.string.map_full_view)) { selected = null; fitAll() }
                MapIconBtn(Icons.Filled.MyLocation, stringResource(R.string.map_my_location)) { flyTo(me, MapConfig.FOCUS_ZOOM) }
                Spacer(Modifier.size(4.dp))
                MapIconBtn(Icons.Filled.Add, null) { map?.animateCamera(CameraUpdateFactory.zoomIn()) }
                MapIconBtn(Icons.Filled.Remove, null) { map?.animateCamera(CameraUpdateFactory.zoomOut()) }
            }

            // 左下（面板上方）：署名，点开 OSM 版权页
            Text(
                MapConfig.ATTRIBUTION,
                Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 10.dp, bottom = peek + 6.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color.White.copy(alpha = 0.75f))
                    .clickable {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(MapConfig.ATTRIBUTION_URL)))
                    }
                    .padding(horizontal = 6.dp, vertical = 2.dp),
                fontSize = 8.5.sp,
                color = Color(0xFF374151)
            )

            // 底图加载失败（多半是离线）：给出说明和重试，下面的列表照常可用
            if (loadFailed && !styleReady) {
                CardBox(
                    Modifier.align(Alignment.Center).padding(horizontal = 32.dp)
                ) {
                    Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(stringResource(R.string.map_tiles_failed), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface)
                        Spacer(Modifier.size(10.dp))
                        Text(
                            stringResource(R.string.map_retry),
                            Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .background(Water700)
                                .clickable {
                                    loadFailed = false
                                    map?.let { m -> loadStyle(m) { styleReady = true } }
                                }
                                .padding(horizontal = 16.dp, vertical = 7.dp),
                            color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}

// ============================================================================
// 底部面板
// ============================================================================

@Composable
private fun MapSheet(
    vm: AppViewModel,
    agencies: List<Pair<Agency, Int>>,
    reports: List<Report>,
    selected: MapPin?,
    onClearSelection: () -> Unit,
    onAgencyRow: (Agency) -> Unit
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        when (selected) {
            is MapPin.AgencyPin -> agencies.firstOrNull { it.first.id == selected.id }
                ?: DemoRepo.agencies.firstOrNull { it.id == selected.id }?.let {
                    it to TicketService.distMeters(AppViewModel.DEMO_LAT, AppViewModel.DEMO_LNG, it.lat, it.lng).toInt()
                }
            else -> null
        }?.let { (a, dist) ->
            SelectedAgencyCard(a, dist, onClose = onClearSelection) { vm.go(Screen.AgencyDetail(a.id)) }
            Spacer(Modifier.size(12.dp))
        }
        if (selected is MapPin.ReportPin) {
            DemoRepo.reports.firstOrNull { it.id == selected.id }?.let { r ->
                SelectedReportCard(r, onClose = onClearSelection) {
                    vm.go(r.ticketId?.let { Screen.TicketDetail(it) } ?: Screen.ReportResult(r.id))
                }
                Spacer(Modifier.size(12.dp))
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 8.dp)) {
            Text(stringResource(R.string.map_nearby), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Text(stringResource(R.string.map_sorted, agencies.size), fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        LazyColumn(
            Modifier.fillMaxWidth().heightIn(max = 520.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(agencies, key = { it.first.id }) { (a, dist) ->
                AgencyRow(a, dist) { onAgencyRow(a) }
            }
            item { Spacer(Modifier.size(16.dp)) }
        }
    }
}

@Composable
private fun SelectedAgencyCard(a: Agency, dist: Int, onClose: () -> Unit, onOpen: () -> Unit) {
    CardBox(container = MaterialTheme.colorScheme.surfaceVariant) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).clip(CircleShape).background(typeColor(a.type)))
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(a.type.labelRes) + " · " + fmtDist(dist),
                    fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    Icons.Filled.Close, null,
                    Modifier.size(20.dp).clip(CircleShape).clickable(onClick = onClose),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.size(6.dp))
            Text(a.name, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 2)
            Text(a.address, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
            Text(a.hours, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            Spacer(Modifier.size(10.dp))
            PrimaryPill(
                if (a.type == AgencyType.LAB) stringResource(R.string.agency_booking_title) else stringResource(R.string.map_view_details),
                onOpen
            )
        }
    }
}

@Composable
private fun SelectedReportCard(r: Report, onClose: () -> Unit, onOpen: () -> Unit) {
    val level = r.effectiveLevel
    CardBox(container = MaterialTheme.colorScheme.surfaceVariant) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).clip(CircleShape).background(level.color))
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(R.string.legend_reports) + " · " + levelName(level) + " · " + r.index + "/100",
                    fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    Icons.Filled.Close, null,
                    Modifier.size(20.dp).clip(CircleShape).clickable(onClick = onClose),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.size(6.dp))
            Text(r.waterbodyName, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 2)
            if (r.note.isNotBlank()) {
                Text(r.note, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
            }
            Spacer(Modifier.size(10.dp))
            PrimaryPill(stringResource(R.string.map_view_details), onOpen)
        }
    }
}

@Composable
private fun PrimaryPill(label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Water700)
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) { Text(label, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold) }
}

// ============================================================================
// 悬浮控件（浅色，压在浅色底图上；风格参考 Jalan Pulang）
// ============================================================================

private val FloatBg = Color.White
private val FloatFg = Color(0xFF1F2937)
private val REPORT_COLOR = Color(0xFFDC2626)

@Composable
private fun MapChip(label: String, dot: Color?, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .shadow(3.dp, RoundedCornerShape(999.dp))
            .clip(RoundedCornerShape(999.dp))
            .background(if (selected) Water700 else FloatBg)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (dot != null) {
            Box(
                Modifier.size(8.dp).clip(CircleShape).background(dot)
                    .border(1.dp, Color.White.copy(alpha = if (selected) 0.9f else 0f), CircleShape)
            )
            Spacer(Modifier.width(6.dp))
        }
        Text(
            label, fontSize = 11.5.sp,
            color = if (selected) Color.White else FloatFg,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
        )
    }
}

@Composable
private fun MapIconBtn(icon: ImageVector, desc: String?, onClick: () -> Unit) {
    Box(
        Modifier
            .size(42.dp)
            .shadow(3.dp, RoundedCornerShape(13.dp))
            .clip(RoundedCornerShape(13.dp))
            .background(FloatBg)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) { Icon(icon, desc, tint = FloatFg, modifier = Modifier.size(20.dp)) }
}

@Composable
private fun MapTextBtn(text: String, desc: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(42.dp)
            .shadow(3.dp, RoundedCornerShape(13.dp))
            .clip(RoundedCornerShape(13.dp))
            .background(FloatBg)
            .clickable(onClickLabel = desc, onClick = onClick),
        contentAlignment = Alignment.Center
    ) { Text(text, color = FloatFg, fontSize = 13.sp, fontWeight = FontWeight.Bold) }
}

// ============================================================================
// MapLibre：生命周期、样式、图层
// ============================================================================

private sealed class MapPin {
    data class AgencyPin(val id: String) : MapPin()
    data class ReportPin(val id: String) : MapPin()
}

private const val SRC_PINS = "st-pins"
private const val SRC_ME = "st-me"
private const val SRC_ACCURACY = "st-accuracy"
private const val SRC_SELECTED = "st-selected"
private const val LYR_PINS = "st-pins-circle"
private const val PROP_ID = "id"
private const val PROP_KIND = "kind"
private const val PROP_COLOR = "color"
private const val PROP_NAME = "name"
private const val KIND_AGENCY = "agency"
private const val KIND_REPORT = "report"

/** MapView 是普通 View，必须手动转发宿主生命周期，否则后台仍在渲染、且会泄漏 GL 资源 */
@Composable
private fun rememberMapViewWithLifecycle(): MapView {
    val context = LocalContext.current
    val mapView = remember {
        val opts = MapLibreMapOptions.createFromAttributes(context)
            .textureMode(true) // TextureView：和 Compose 叠层、底部面板动画不打架
        MapView(context, opts).apply { onCreate(null) }
    }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, mapView) {
        var started = false
        var resumed = false
        val obs = LifecycleEventObserver { _, e ->
            when (e) {
                Lifecycle.Event.ON_START -> if (!started) { mapView.onStart(); started = true }
                Lifecycle.Event.ON_RESUME -> if (!resumed) { mapView.onResume(); resumed = true }
                Lifecycle.Event.ON_PAUSE -> if (resumed) { mapView.onPause(); resumed = false }
                Lifecycle.Event.ON_STOP -> if (started) { mapView.onStop(); started = false }
                else -> Unit
            }
        }
        lifecycle.addObserver(obs)
        onDispose {
            lifecycle.removeObserver(obs)
            if (resumed) mapView.onPause()
            if (started) mapView.onStop()
            mapView.onDestroy()
        }
    }
    return mapView
}

private fun loadStyle(m: MapLibreMap, onReady: () -> Unit) {
    m.setStyle(Style.Builder().fromUri(MapConfig.STYLE_URL)) { style ->
        addOverlayLayers(style)
        onReady()
    }
}

/** 叠加图层只在样式加载完时建一次；之后只更新 source 数据 */
private fun addOverlayLayers(style: Style) {
    style.addSource(GeoJsonSource(SRC_ACCURACY))
    style.addSource(GeoJsonSource(SRC_PINS))
    style.addSource(GeoJsonSource(SRC_ME))
    style.addSource(GeoJsonSource(SRC_SELECTED))

    // 位置精度圈：半径 = 设置里的定位精度（米），直观说明隐私降级
    style.addLayer(
        FillLayer("st-accuracy-fill", SRC_ACCURACY).withProperties(
            PropertyFactory.fillColor("#0891B2"),
            PropertyFactory.fillOpacity(0.12f)
        )
    )
    style.addLayer(
        LineLayer("st-accuracy-line", SRC_ACCURACY).withProperties(
            PropertyFactory.lineColor("#0891B2"),
            PropertyFactory.lineOpacity(0.6f),
            PropertyFactory.lineWidth(1.5f)
        )
    )

    // 选中光圈（在点位下面）
    style.addLayer(
        CircleLayer("st-selected-halo", SRC_SELECTED).withProperties(
            PropertyFactory.circleRadius(22f),
            PropertyFactory.circleColor("#0E7490"),
            PropertyFactory.circleOpacity(0.22f),
            PropertyFactory.circleStrokeColor("#0E7490"),
            PropertyFactory.circleStrokeWidth(2f),
            PropertyFactory.circlePitchAlignment(Property.CIRCLE_PITCH_ALIGNMENT_MAP)
        )
    )

    // 机构 + 上报点位：一个圆点图层，颜色由数据决定
    style.addLayer(
        CircleLayer(LYR_PINS, SRC_PINS).withProperties(
            PropertyFactory.circleColor(Expression.toColor(Expression.get(PROP_COLOR))),
            PropertyFactory.circleRadius(
                Expression.interpolate(
                    Expression.linear(), Expression.zoom(),
                    Expression.stop(8, 4f), Expression.stop(13, 7f), Expression.stop(17, 11f)
                )
            ),
            PropertyFactory.circleStrokeColor("#FFFFFF"),
            PropertyFactory.circleStrokeWidth(2.5f)
        )
    )

    // 名称标签：放大到街区级才显示，避免挤成一团
    style.addLayer(
        SymbolLayer("st-pins-label", SRC_PINS).withProperties(
            PropertyFactory.textField(Expression.get(PROP_NAME)),
            PropertyFactory.textFont(arrayOf("Noto Sans Regular")),
            PropertyFactory.textSize(11f),
            PropertyFactory.textAnchor(Property.TEXT_ANCHOR_TOP),
            PropertyFactory.textOffset(arrayOf(0f, 1.1f)),
            PropertyFactory.textMaxWidth(9f),
            PropertyFactory.textColor("#1F2937"),
            PropertyFactory.textHaloColor("#FFFFFF"),
            PropertyFactory.textHaloWidth(1.4f),
            PropertyFactory.textOptional(true)
        ).apply { minZoom = 13.5f }
    )

    // 我的位置
    style.addLayer(
        CircleLayer("st-me", SRC_ME).withProperties(
            PropertyFactory.circleRadius(7f),
            PropertyFactory.circleColor("#0891B2"),
            PropertyFactory.circleStrokeColor("#FFFFFF"),
            PropertyFactory.circleStrokeWidth(3f)
        )
    )
}

private fun pinsToGeoJson(agencies: List<Agency>, reports: List<Report>): FeatureCollection {
    val fs = agencies.map { a ->
        Feature.fromGeometry(Point.fromLngLat(a.lng, a.lat)).apply {
            addStringProperty(PROP_ID, a.id)
            addStringProperty(PROP_KIND, KIND_AGENCY)
            addStringProperty(PROP_COLOR, typeColor(a.type).hex())
            addStringProperty(PROP_NAME, a.name)
        }
    } + reports.map { r ->
        Feature.fromGeometry(Point.fromLngLat(r.lng, r.lat)).apply {
            addStringProperty(PROP_ID, r.id)
            addStringProperty(PROP_KIND, KIND_REPORT)
            addStringProperty(PROP_COLOR, r.effectiveLevel.color.hex())
            addStringProperty(PROP_NAME, r.waterbodyName)
        }
    }
    return FeatureCollection.fromFeatures(fs)
}

private fun pinLatLng(pin: MapPin): LatLng? = when (pin) {
    is MapPin.AgencyPin -> DemoRepo.agencies.firstOrNull { it.id == pin.id }?.let { LatLng(it.lat, it.lng) }
    is MapPin.ReportPin -> DemoRepo.reports.firstOrNull { it.id == pin.id }?.let { LatLng(it.lat, it.lng) }
}

/** 以米为半径的近似圆（赤道附近误差可忽略） */
private fun circlePolygon(center: LatLng, radiusM: Double, steps: Int = 64): Polygon {
    val dLat = radiusM / 111_320.0
    val dLng = radiusM / (111_320.0 * cos(center.latitude * PI / 180))
    val ring = (0..steps).map { i ->
        val t = 2 * PI * i / steps
        Point.fromLngLat(center.longitude + dLng * cos(t), center.latitude + dLat * sin(t))
    }
    return Polygon.fromLngLats(listOf(ring))
}

private fun Color.hex(): String = String.format("#%06X", 0xFFFFFF and toArgb())

// ============================================================================
// 列表项（其他页面也可复用）
// ============================================================================

private fun typeColor(t: AgencyType): Color = when (t) {
    AgencyType.GOVERNMENT -> Color(0xFF3B82F6)
    AgencyType.LAB -> Color(0xFF10B981)
    AgencyType.WWTP -> Color(0xFF8B5CF6)
    AgencyType.STATION -> Color(0xFFF59E0B)
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
                        .background(typeColor(AgencyType.LAB).copy(alpha = 0.15f))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) { Text(stringResource(R.string.agency_booking_title), fontSize = 9.5.sp, color = typeColor(AgencyType.LAB)) }
            } else {
                Text("›", fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private fun fmtDist(m: Int): String = if (m >= 1000) "%.1f km".format(m / 1000.0) else "$m m"

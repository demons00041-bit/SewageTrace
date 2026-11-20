package com.sewagetrace.app.map

import org.maplibre.android.geometry.LatLng

/**
 * 地图配置集中处。
 *
 * 换底图样式只改 [STYLE_URL] 一行；坐标、缩放范围、署名都从这里取，
 * 避免散落在 MapScreen 里。
 */
object MapConfig {

    /**
     * OpenFreeMap 矢量样式（免费、免 Key、无用量上限）。
     * liberty 自带 3D 楼块，倾斜视角下能看到建筑立体效果。
     *
     * 其他可选：
     *   "https://tiles.openfreemap.org/styles/positron"  浅灰极简
     *   "https://tiles.openfreemap.org/styles/bright"    高对比
     *   "https://tiles.openfreemap.org/styles/dark"      深色
     * 换样式不用改署名，三者都来自同一套 OpenMapTiles 数据。
     */
    const val STYLE_URL = "https://tiles.openfreemap.org/styles/liberty"

    /** 署名：OpenFreeMap / OpenMapTiles / OSM 三者都必须保留 */
    const val ATTRIBUTION = "© OpenFreeMap © OpenMapTiles © OpenStreetMap contributors"
    const val ATTRIBUTION_URL = "https://www.openstreetmap.org/copyright"

    /** 需求 MAP-02：缩放严格 3–19 */
    const val MIN_ZOOM = 3.0
    const val MAX_ZOOM = 19.0
    const val DEFAULT_ZOOM = 12.0

    /** 点选标记 / 从记录详情跳转地图时的聚焦级别 */
    const val FOCUS_ZOOM = 15.5

    /** 3D 模式的倾斜角与朝向（参考 Jalan Pulang 的透视视角） */
    const val TILT_3D = 60.0
    const val BEARING_3D = -17.0

    /** 相机动画时长（毫秒） */
    const val ANIM_MS = 900

    /** 马来亚大学主校区 */
    val UM_CENTER = LatLng(3.1209, 101.6538)
}

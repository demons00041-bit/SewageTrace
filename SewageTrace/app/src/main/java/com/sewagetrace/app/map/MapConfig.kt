package com.sewagetrace.app.map

import org.osmdroid.util.GeoPoint

/**
 * 地图配置集中处。
 *
 * 换底图样式只改 [TILE_URL] 一行；坐标、缩放范围、署名都从这里取，
 * 避免散落在 MapScreen 里。
 */
object MapConfig {

    /**
     * 瓦片地址。默认 OSM 官方（全开源）。
     *
     * 想要深色好看版可换成 CARTO Dark Matter（免费、免 Key）：
     *   "https://a.basemaps.cartocdn.com/dark_all/"
     * 换的时候记得同步改 [ATTRIBUTION]。
     */
    const val TILE_URL = "https://tile.openstreetmap.org/"

    const val ATTRIBUTION = "© OpenStreetMap contributors"
    const val ATTRIBUTION_URL = "https://www.openstreetmap.org/copyright"

    /** 需求 MAP-02：缩放严格 3–19 */
    const val MIN_ZOOM = 3.0
    const val MAX_ZOOM = 19.0
    const val DEFAULT_ZOOM = 12.0

    /** 从记录详情跳转地图时的聚焦级别 */
    const val FOCUS_ZOOM = 16.0

    /** 马来亚大学主校区 */
    val UM_CENTER = GeoPoint(3.1209, 101.6538)
}

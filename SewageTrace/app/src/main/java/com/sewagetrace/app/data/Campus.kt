package com.sewagetrace.app.data

import com.sewagetrace.app.model.Agency
import com.sewagetrace.app.model.AgencyType

/**
 * UM 校园范围与「两层受理」规则（单点定义，路由/地图/统计都从这里取）。
 *
 *   校内上报 → 先由校内部门（AgencyType.CAMPUS）受理
 *              → 多人印证提级，或人工/AI 判为 R4 时，移交外部机构（政府）协办
 *   校外上报 → 直接分派给最近的外部政府机构（原有逻辑）
 *
 * 地理围栏用「中心点 + 半径」近似，演示足够；正式版可换成校园边界多边形。
 * 坐标为示意值，录视频前建议用真实点位校正。
 */
object Campus {
    /** 马来亚大学主校区中心（示意） */
    const val LAT = 3.1209
    const val LNG = 101.6538

    /** 围栏半径（米）：覆盖主校区与各 Kolej Kediaman */
    const val RADIUS_M = 1400.0

    /** 工单「区域」字段的显示名 */
    const val REGION = "Universiti Malaya"

    fun contains(lat: Double, lng: Double): Boolean =
        TicketService.distMeters(LAT, LNG, lat, lng) <= RADIUS_M

    /** 离 (lat,lng) 最近的某类机构 */
    fun nearest(type: AgencyType, lat: Double, lng: Double): Agency? =
        DemoRepo.agencies.filter { it.type == type }
            .minByOrNull { TicketService.distMeters(lat, lng, it.lat, it.lng) }

    /** 首次受理方：校内 → 最近的校内部门；校外 → 最近的外部政府机构 */
    fun firstResponder(lat: Double, lng: Double): Agency? =
        if (contains(lat, lng)) nearest(AgencyType.CAMPUS, lat, lng) ?: nearest(AgencyType.GOVERNMENT, lat, lng)
        else nearest(AgencyType.GOVERNMENT, lat, lng)

    /** 校内工单需要外部协办时找谁：最近的外部政府机构 */
    fun externalReferral(lat: Double, lng: Double): Agency? = nearest(AgencyType.GOVERNMENT, lat, lng)

    fun isCampusAgency(agencyId: String?): Boolean =
        agencyId != null && DemoRepo.agencies.any { it.id == agencyId && it.type == AgencyType.CAMPUS }
}

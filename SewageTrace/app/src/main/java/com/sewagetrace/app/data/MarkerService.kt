package com.sewagetrace.app.data

import com.sewagetrace.app.model.Confidence
import com.sewagetrace.app.model.LevelSource
import com.sewagetrace.app.model.Report
import com.sewagetrace.app.model.ReportStatus
import com.sewagetrace.app.model.SeverityLevel
import com.sewagetrace.app.model.SuspectedType

/**
 * 地图标记的增删改查（需求 MAP-03，方案 B：标记 = 我的上报点位）。
 *
 * 「标记」不是新实体，就是 [Report]：
 *   - 地图上长按新增 → 一条没有照片、等级来自人工的 DRAFT 记录（engine = manual）
 *   - 自己的任何记录都能改名称 / 人工等级 / 场景描述
 *   - 只有没进工单的记录能移动和删除：进了工单的点位是证据，不能事后挪走或抹掉
 *
 * 所有写操作都走 DemoRepo 的列表，自动落库。
 */
object MarkerService {

    /** 手动标记没有图像分析，按等级给一个代表性的指数（落在该档区间中段） */
    fun representativeIndex(level: SeverityLevel): Int = when (level) {
        SeverityLevel.R4 -> 80
        SeverityLevel.R3 -> 60
        SeverityLevel.R2 -> 40
        SeverityLevel.R1 -> 22
        SeverityLevel.R0 -> 7
    }

    fun isMine(r: Report, userId: String?): Boolean = userId != null && r.userId == userId

    /** 能否编辑名称/等级/描述：自己的记录都可以 */
    fun canEdit(r: Report, userId: String?): Boolean = isMine(r, userId)

    /** 能否移动/删除：自己的、且还没进工单 */
    fun canMoveOrDelete(r: Report, userId: String?): Boolean = isMine(r, userId) && r.ticketId == null

    fun create(
        userId: String,
        name: String,
        level: SeverityLevel,
        note: String,
        lat: Double,
        lng: Double,
        precision: Int,
        now: Long = System.currentTimeMillis()
    ): Report {
        val idx = representativeIndex(level)
        val r = Report(
            id = "m-" + now.toString(36),
            userId = userId,
            waterbodyName = name.trim(),
            lat = lat, lng = lng, precision = precision,
            capturedAt = now, createdAt = now,
            level = level, index = idx,
            confidence = Confidence.MEDIUM,
            suspectedType = SuspectedType.UNKNOWN,
            evidence = emptyList(),
            note = note,
            photoPath = "",
            status = ReportStatus.DRAFT,
            aiIndex = idx, aiLevel = level,
            manualLevel = level,
            levelSource = LevelSource.MANUAL,
            dedupCounted = false,
            engine = Report.ENGINE_MANUAL
        )
        // 直接进列表（自动落库）；不走 saveReport：手动标记不更新「最近上报时间」
        DemoRepo.reports.add(r)
        return r
    }

    /**
     * 改名称 / 人工等级 / 描述。
     * - 手动标记：没有 AI 结论，人工等级就是等级本身
     * - 拍照记录：只改人工等级，AI 结论保留；路由仍按 max(人工, AI)
     */
    fun update(reportId: String, userId: String?, name: String, level: SeverityLevel, note: String): Report? {
        val r = DemoRepo.reports.firstOrNull { it.id == reportId } ?: return null
        if (!canEdit(r, userId)) return null
        val updated = if (r.engine == Report.ENGINE_MANUAL) {
            val idx = representativeIndex(level)
            r.copy(
                waterbodyName = name.trim(), note = note,
                level = level, index = idx, aiLevel = level, aiIndex = idx,
                manualLevel = level, levelSource = LevelSource.MANUAL
            )
        } else {
            r.copy(
                waterbodyName = name.trim(), note = note,
                manualLevel = level,
                levelSource = LevelSource.of(r.level, level),
                userAdjusted = level != r.level
            )
        }
        DemoRepo.updateReport(updated)
        // 工单标题跟着首条记录的名称走
        updated.ticketId?.let { tid ->
            val i = DemoRepo.tickets.indexOfFirst { it.id == tid }
            if (i >= 0 && DemoRepo.tickets[i].reportIds.firstOrNull() == r.id) {
                DemoRepo.tickets[i] = DemoRepo.tickets[i].copy(waterbodyName = updated.waterbodyName)
            }
        }
        return updated
    }

    fun move(reportId: String, userId: String?, lat: Double, lng: Double): Report? {
        val r = DemoRepo.reports.firstOrNull { it.id == reportId } ?: return null
        if (!canMoveOrDelete(r, userId)) return null
        val updated = r.copy(lat = lat, lng = lng)
        DemoRepo.updateReport(updated)
        return updated
    }

    fun delete(reportId: String, userId: String?): Boolean {
        val i = DemoRepo.reports.indexOfFirst { it.id == reportId }
        if (i < 0 || !canMoveOrDelete(DemoRepo.reports[i], userId)) return false
        DemoRepo.reports.removeAt(i)
        return true
    }
}

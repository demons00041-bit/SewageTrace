package com.sewagetrace.app.data

import androidx.annotation.StringRes
import com.sewagetrace.app.R
import com.sewagetrace.app.ai.AnalysisResult
import com.sewagetrace.app.model.Booking
import com.sewagetrace.app.model.BookingStatus
import com.sewagetrace.app.model.Confidence
import com.sewagetrace.app.model.LeaderRow
import com.sewagetrace.app.model.Report
import com.sewagetrace.app.model.ReportStatus
import com.sewagetrace.app.model.SeverityLevel
import com.sewagetrace.app.model.TicketLog

/**
 * 上报流程编排（对应文档 FR2.3 / FR2.5）：
 *   校验（登录 / 日限 / 去重 / 置信度）→ 存档 → 分级路由 → 建单 → 提级
 *
 * 客户端只负责"提交"；是否建单、是否提级由服务端判定（演示版由 TicketService 扮演服务端）。
 * 所有面向用户的原因都返回资源 id，切换语言后历史记录同步变化。
 */
object ReportFlow {

    private const val DAY = 86_400_000L
    private const val DAILY_LIMIT = 5

    fun submit(
        sceneId: String?,
        analysis: AnalysisResult,
        photoPath: String,
        waterbody: String,
        lat: Double,
        lng: Double,
        note: String,
        autoReport: Boolean,
        locationPrecision: Int = 50,
        now: Long = System.currentTimeMillis()
    ): SubmitOutcome {
        val user = DemoRepo.currentUser() ?: return SubmitOutcome.Failed("not signed in")

        // 1) 低置信度 / 非污水：阻止上报，只保留本地结论
        if (analysis.confidence == Confidence.LOW) {
            DemoRepo.saveReport(
                draftOf(user.id, analysis, photoPath, waterbody, lat, lng, note, now, ReportStatus.DRAFT)
            )
            return SubmitOutcome.Blocked(R.string.blocked_low_conf)
        }
        if (analysis.isNonWater) {
            DemoRepo.saveReport(
                draftOf(user.id, analysis, photoPath, waterbody, lat, lng, note, now, ReportStatus.DRAFT)
            )
            return SubmitOutcome.Blocked(R.string.blocked_not_water)
        }

        // 2) 防滥用：日限 5 次
        val today = DemoRepo.reports.count {
            it.userId == user.id && it.status != ReportStatus.DRAFT &&
                now - it.createdAt < DAY && it.createdAt <= now
        }
        if (today >= DAILY_LIMIT) return SubmitOutcome.Failed("daily limit reached ($DAILY_LIMIT/day)")

        // 3) 分级路由：R0/R1 不自动上报
        val autoAllowed = analysis.level == SeverityLevel.R2 ||
            analysis.level == SeverityLevel.R3 ||
            analysis.level == SeverityLevel.R4

        if (!autoAllowed) {
            DemoRepo.saveReport(
                draftOf(user.id, analysis, photoPath, waterbody, lat, lng, note, now, ReportStatus.DRAFT)
            )
            return SubmitOutcome.DraftOnly(R.string.outcome_draft)
        }
        if (!autoReport) {
            DemoRepo.saveReport(
                draftOf(user.id, analysis, photoPath, waterbody, lat, lng, note, now, ReportStatus.DRAFT)
            )
            return SubmitOutcome.DraftOnly(R.string.outcome_draft)
        }

        // 4) 近 24h 同点重复：不建单，但计入"多人印证"
        val dup = DemoRepo.reports.firstOrNull {
            it.userId == user.id && it.status != ReportStatus.DRAFT && it.status != ReportStatus.REJECTED &&
                now - it.createdAt <= DAY && TicketService.distMeters(it.lat, it.lng, lat, lng) <= 200
        }
        if (dup != null) return SubmitOutcome.Failed("duplicate within 24h: ${dup.waterbodyName}")

        val report = draftOf(user.id, analysis, photoPath, waterbody, lat, lng, note, now, ReportStatus.REPORTED)
            .copy(precision = locationPrecision)

        // 5) 建单（服务端）
        val ticket = TicketService.createTicket(report, user, now)
        DemoRepo.saveReport(report.copy(status = ReportStatus.IN_TICKET, ticketId = ticket.id))
        val reported = TicketService.markReportLinked(report.id)

        // 6) 多人印证提级
        val escalated = TicketService.escalateIfNeeded(reported, now)

        return SubmitOutcome.Reported(reported, ticket, autoReported = true, escalated = escalated)
    }

    private fun draftOf(
        userId: String,
        a: AnalysisResult,
        photoPath: String,
        waterbody: String,
        lat: Double,
        lng: Double,
        note: String,
        now: Long,
        status: ReportStatus
    ) = Report(
        id = "r-" + now.toString(36) + "-" + (0..999).random(),
        userId = userId,
        waterbodyName = waterbody,
        lat = lat,
        lng = lng,
        precision = 50,
        capturedAt = now,
        createdAt = now,
        level = a.level,
        index = a.index,
        confidence = a.confidence,
        suspectedType = a.suspectedType,
        evidence = a.evidence,
        note = note,
        photoPath = photoPath,
        status = status,
        ticketId = null,
        engine = a.engine
    )

    /** 详情页"手动上报"：把一条草稿升级为工单 */
    fun submitDraft(reportId: String, now: Long = System.currentTimeMillis()): SubmitOutcome {
        val user = DemoRepo.currentUser() ?: return SubmitOutcome.Failed("not signed in")
        val r = DemoRepo.reports.firstOrNull { it.id == reportId } ?: return SubmitOutcome.Failed("record missing")
        if (r.status != ReportStatus.DRAFT) return SubmitOutcome.Failed("already reported")
        if (r.confidence == Confidence.LOW) return SubmitOutcome.Blocked(R.string.blocked_low_conf)
        if (r.level == SeverityLevel.R0 || r.level == SeverityLevel.R1) {
            val updated = r.copy(status = ReportStatus.REPORTED, createdAt = now)
            DemoRepo.updateReport(updated)
            return SubmitOutcome.Reported(updated, null, autoReported = false, escalated = false)
        }
        val ticket = TicketService.createTicket(r, user, now)
        val updated = r.copy(status = ReportStatus.IN_TICKET, ticketId = ticket.id)
        DemoRepo.updateReport(updated)
        return SubmitOutcome.Reported(updated, ticket, autoReported = false, escalated = false)
    }

    fun book(
        userId: String,
        agencyId: String,
        date: String,
        time: String,
        tests: List<String>,
        materials: List<String>,
        now: Long = System.currentTimeMillis()
    ): Booking {
        val agency = DemoRepo.agencies.firstOrNull { it.id == agencyId }
        val code = "SW" + (100000..999999).random()
        val b = Booking(
            id = "b-" + now.toString(36),
            userId = userId,
            agencyId = agencyId,
            agencyName = agency?.name ?: "Water testing lab",
            slotDate = date,
            slotTime = time,
            tests = tests,
            materials = materials,
            status = BookingStatus.BOOKED,
            createdAt = now,
            code = code
        )
        DemoRepo.bookings.add(0, b)
        return b
    }

    fun cancelBooking(id: String): Boolean {
        val i = DemoRepo.bookings.indexOfFirst { it.id == id }
        if (i < 0) return false
        DemoRepo.bookings[i] = DemoRepo.bookings[i].copy(status = BookingStatus.CANCELLED)
        return true
    }

    fun rank(scope: String, windowDays: Int): List<LeaderRow> =
        RankService.buildBoard(scope, windowDays, DemoRepo.currentUserId)

    fun logs(ticketId: String): List<TicketLog> = DemoRepo.logsOf(ticketId)
}

/**
 * 引擎回退提示：给 UI 一个资源 id + 可选参数。
 * 语言切换后同一句提示会自动换语言。
 */
data class FallbackNotice(
    @StringRes val textRes: Int,
    val args: List<String> = emptyList()
) {
    companion object {
        fun of(reason: String?): FallbackNotice? = when {
            reason == null -> null
            reason == "no_key" -> FallbackNotice(R.string.fallback_no_key)
            reason == "no_network" -> FallbackNotice(R.string.fallback_no_network)
            reason == "forced_local" -> FallbackNotice(R.string.fallback_forced_local)
            reason.startsWith("http_") -> FallbackNotice(R.string.fallback_cloud_failed, listOf(reason.removePrefix("http_")))
            else -> FallbackNotice(R.string.fallback_cloud_failed, listOf(reason))
        }
    }
}

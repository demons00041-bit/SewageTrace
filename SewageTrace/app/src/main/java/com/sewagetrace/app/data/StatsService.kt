package com.sewagetrace.app.data

import com.sewagetrace.app.model.Agency
import com.sewagetrace.app.model.AgencyType
import com.sewagetrace.app.model.Report
import com.sewagetrace.app.model.ReportStatus
import com.sewagetrace.app.model.SeverityLevel
import com.sewagetrace.app.model.Ticket
import com.sewagetrace.app.model.TicketStage

/** 汇总统计：用于任务反馈页与个人页 */
object StatsService {

    data class Aggregate(
        val valid: Int,
        val tickets: Int,
        val closed: Int,
        val inProgress: Int,
        val avgProgress: Int,
        val avgResponseHours: Double,
        val escalated: Int,
        val disputed: Int
    )

    fun summary(userId: String? = null): Aggregate {
        val rs = if (userId == null) DemoRepo.reports else DemoRepo.reports.filter { it.userId == userId }
        val ts = if (userId == null) DemoRepo.tickets else DemoRepo.tickets.filter { it.reporterUserId == userId }
        val valid = rs.count {
            it.status != ReportStatus.DRAFT && (it.level == SeverityLevel.R2 || it.level == SeverityLevel.R3 || it.level == SeverityLevel.R4)
        }
        val closed = ts.count { it.stage == TicketStage.CLOSED }
        val inProgress = ts.count { it.stage != TicketStage.CLOSED }
        val avg = if (ts.isEmpty()) 0 else ts.sumOf { it.progress } / ts.size
        val resp = ts.map { TicketService.responseHours(it.id) }.filter { it > 0 }
        return Aggregate(
            valid = valid,
            tickets = ts.size,
            closed = closed,
            inProgress = inProgress,
            avgProgress = avg,
            avgResponseHours = if (resp.isEmpty()) 0.0 else resp.average(),
            escalated = ts.count { it.escalated },
            disputed = ts.count { it.disputed }
        )
    }

    /** 机构列表：按类型过滤，按距离排序 */
    fun agencies(type: AgencyType?, fromLat: Double, fromLng: Double): List<Pair<Agency, Int>> {
        val list = DemoRepo.agencies.filter { type == null || it.type == type }
        return list.map { it to TicketService.distMeters(fromLat, fromLng, it.lat, it.lng).toInt() }
            .sortedBy { it.second }
    }

    fun myReports(userId: String): List<Report> = DemoRepo.reportsOf(userId)

    fun makeTickets(now: Long): List<Ticket> = DemoRepo.tickets.sortedByDescending { it.createdAt }
}

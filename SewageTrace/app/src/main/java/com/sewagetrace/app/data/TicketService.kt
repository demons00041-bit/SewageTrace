package com.sewagetrace.app.data

import androidx.annotation.StringRes
import com.sewagetrace.app.R
import com.sewagetrace.app.model.Agency
import com.sewagetrace.app.model.AgencyType
import com.sewagetrace.app.model.Confidence
import com.sewagetrace.app.model.LeaderRow
import com.sewagetrace.app.model.Report
import com.sewagetrace.app.model.ReportStatus
import com.sewagetrace.app.model.SeverityLevel
import com.sewagetrace.app.model.Ticket
import com.sewagetrace.app.model.TicketLog
import com.sewagetrace.app.model.TicketStage
import com.sewagetrace.app.model.User

/**
 * 工单服务：负责"上报 → 工单池 → 分派 → 核实 → 整治 → 复测 → 关闭"的状态机。
 *
 * 说明：文档要求进度百分比由服务端按公开公式计算、客户端不得上传或覆盖。
 * 演示版用本类扮演服务端（ProgressCalculator 的公式与 assets/workflow.json 一致），
 * UI 层只读取，没有任何修改进度的入口。
 */
object TicketService {

    private const val HOUR = 3_600_000L
    private const val DAY = 86_400_000L

    /** 以当前 locale 取一条资源文本（用于把阶段名嵌进日志插值参数） */
    private fun stringOf(@StringRes id: Int): String = AppContextHolder.ctx.getString(id)

    /** 服务端进度公式：progress = clamp(0,100, Σ(阶段权重 × 完成度)) */
    private val stageWeights = linkedMapOf(
        TicketStage.SUBMITTED to 0,
        TicketStage.POOLED to 0,
        TicketStage.DISPATCHED to 5,
        TicketStage.VERIFIED to 15,
        TicketStage.FILED to 15,
        TicketStage.TREATING to 50,
        TicketStage.RETEST to 10,
        TicketStage.CLOSED to 5
    )

    fun computeProgress(stage: TicketStage, stageCompletion: Float): Int {
        val order = TicketStage.entries
        val idx = order.indexOf(stage)
        var sum = 0f
        for (i in 0 until idx) {
            sum += (stageWeights[order[i]] ?: 0)
        }
        sum += (stageWeights[stage] ?: 0) * stageCompletion.coerceIn(0f, 1f)
        return sum.toInt().coerceIn(0, 100)
    }

    /** 创建工单（上报成功后调用），返回工单 */
    fun createTicket(report: Report, user: User, now: Long = System.currentTimeMillis()): Ticket {
        val id = "t-" + now.toString(36)
        val agency = pickAgency(report)
        val t = Ticket(
            id = id,
            reportIds = listOf(report.id),
            waterbodyName = report.waterbodyName,
            region = user.city,
            agencyId = agency?.id,
            agencyName = agency?.name ?: "市级统一受理渠道",
            stage = TicketStage.SUBMITTED,
            progress = 0,
            createdAt = now,
            slaDeadline = now + 30 * DAY,
            reporterUserId = user.id,
            reportCount = 1
        )
        DemoRepo.tickets.add(0, t)
        DemoRepo.ticketLogs.add(
            TicketLog(
                id = "l-$id-0", ticketId = id, at = now,
                actorRoleRes = R.string.role_platform, actorName = "SewageTrace",
                stage = TicketStage.SUBMITTED, progressAfter = 0,
                textRes = R.string.log_created
            )
        )
        return t
    }

    private fun pickAgency(report: Report): Agency? {
        val govs = DemoRepo.agencies.filter { it.type == AgencyType.GOVERNMENT }
        return govs.minByOrNull {
            (it.lat - report.lat) * (it.lat - report.lat) + (it.lng - report.lng) * (it.lng - report.lng)
        }
    }

    /** 报告入库后回填 ticketId（报告在 DemoRepo 中保存） */
    fun markReportLinked(reportId: String): Report {
        val r = DemoRepo.reports.first { it.id == reportId }
        val t = DemoRepo.tickets.firstOrNull { it.reportIds.contains(reportId) }
        val updated = r.copy(status = ReportStatus.IN_TICKET, ticketId = t?.id)
        DemoRepo.updateReport(updated)
        return updated
    }

    /** 平台方推进工单（演示中由"模拟政府处理"按钮触发，真实版本由后台调度） */
    fun advance(ticketId: String, now: Long = System.currentTimeMillis()): Ticket? {
        val t = DemoRepo.ticketOf(ticketId) ?: return null
        val next = when (t.stage) {
            TicketStage.SUBMITTED -> TicketStage.POOLED
            TicketStage.POOLED -> TicketStage.DISPATCHED
            TicketStage.DISPATCHED -> TicketStage.VERIFIED
            TicketStage.VERIFIED -> TicketStage.FILED
            TicketStage.FILED -> TicketStage.TREATING
            TicketStage.TREATING -> TicketStage.RETEST
            TicketStage.RETEST -> TicketStage.CLOSED
            TicketStage.CLOSED -> TicketStage.CLOSED
        }
        if (next == t.stage) return t

        val completion = if (next == TicketStage.TREATING) 0.4f else 1f
        val progress = computeProgress(next, completion)
        val n = narration(next, t)
        val updated = t.copy(stage = next, progress = progress)
        val i = DemoRepo.tickets.indexOfFirst { it.id == ticketId }
        if (i >= 0) DemoRepo.tickets[i] = updated
        DemoRepo.ticketLogs.add(
            TicketLog(
                id = "l-$ticketId-${now}", ticketId = ticketId, at = now,
                actorRoleRes = n.roleRes, actorName = n.actorName,
                stage = next, progressAfter = progress, textRes = n.textRes, formatArgs = n.args
            )
        )
        return updated
    }

    /** 施工治理阶段推进治理完成度（0.4 → 0.7 → 1.0） */
    fun advanceTreatment(ticketId: String, now: Long = System.currentTimeMillis()): Ticket? {
        val t = DemoRepo.ticketOf(ticketId) ?: return null
        val current = when {
            t.progress < 60 -> 0.7f
            else -> 1.0f
        }
        val progress = computeProgress(TicketStage.TREATING, current)
        val updated = t.copy(progress = progress)
        val i = DemoRepo.tickets.indexOfFirst { it.id == ticketId }
        if (i >= 0) DemoRepo.tickets[i] = updated
        DemoRepo.ticketLogs.add(
            TicketLog(
                id = "l-$ticketId-${now}-t", ticketId = ticketId, at = now,
                actorRoleRes = R.string.role_treatment_org, actorName = "River maintenance unit",
                stage = TicketStage.TREATING, progressAfter = progress,
                textRes = if (current < 1f) R.string.log_treating_mid else R.string.log_treating_done
            )
        )
        return updated
    }

    /**
     * 每一阶段的叙述：角色资源 id + 名称 + 文案资源 id（+ 插值参数）。
     * 全部走资源，切换语言后历史日志也会跟着变。
     */
    private fun narration(stage: TicketStage, t: Ticket): Narration = when (stage) {
        TicketStage.POOLED -> Narration(
            R.string.role_platform, "SewageTrace", R.string.log_pooled, emptyList()
        )
        TicketStage.DISPATCHED -> Narration(
            R.string.role_platform, "SewageTrace", R.string.log_dispatched, listOf(t.agencyName)
        )
        TicketStage.VERIFIED -> Narration(
            R.string.role_authority, "Enforcement team", R.string.log_verified, emptyList()
        )
        TicketStage.FILED -> Narration(
            R.string.role_authority, "Authority", R.string.log_filed, emptyList()
        )
        TicketStage.TREATING -> Narration(
            R.string.role_treatment_org, "River maintenance unit", R.string.log_treating_start, emptyList()
        )
        TicketStage.RETEST -> Narration(
            R.string.role_lab, "Water quality station", R.string.log_retest, emptyList()
        )
        TicketStage.CLOSED -> Narration(
            R.string.role_authority, "Authority", R.string.log_closed, emptyList()
        )
        else -> Narration(R.string.role_platform, "SewageTrace", R.string.log_generic, emptyList())
    }

    private data class Narration(
        @StringRes val roleRes: Int,
        val actorName: String,
        @StringRes val textRes: Int,
        val args: List<String>
    )

    /** 用户提交异议：触发工单回退与二次核实 */
    fun dispute(ticketId: String, text: String, now: Long = System.currentTimeMillis()): Ticket? {
        val t = DemoRepo.ticketOf(ticketId) ?: return null
        val back = when (t.stage) {
            TicketStage.CLOSED -> TicketStage.VERIFIED
            TicketStage.RETEST -> TicketStage.TREATING
            TicketStage.TREATING -> TicketStage.VERIFIED
            else -> TicketStage.VERIFIED
        }
        val progress = computeProgress(back, 0.5f)
        val updated = t.copy(stage = back, progress = progress, disputed = true)
        val i = DemoRepo.tickets.indexOfFirst { it.id == ticketId }
        if (i >= 0) DemoRepo.tickets[i] = updated
        DemoRepo.ticketLogs.add(
            TicketLog(
                id = "l-$ticketId-${now}-d", ticketId = ticketId, at = now,
                actorRoleRes = R.string.role_user, actorName = "Me (objection)",
                stage = back, progressAfter = progress,
                textRes = R.string.log_dispute, formatArgs = listOf(text, stringOf(back.labelRes)),
                isDispute = true
            )
        )
        return updated
    }

    fun thank(ticketId: String): Ticket? {
        val t = DemoRepo.ticketOf(ticketId) ?: return null
        val updated = t.copy(thanked = true)
        val i = DemoRepo.tickets.indexOfFirst { it.id == ticketId }
        if (i >= 0) DemoRepo.tickets[i] = updated
        return updated
    }

    /** 自动催办：进度 < 25% 且 7 天无日志 */
    fun checkUrge(ticketId: String, now: Long = System.currentTimeMillis()): Ticket? {
        val t = DemoRepo.ticketOf(ticketId) ?: return null
        val last = DemoRepo.logsOf(ticketId).lastOrNull()?.at ?: t.createdAt
        val idle = now - last
        if (t.progress < 25 && idle > 7 * DAY && !t.urged) {
            val updated = t.copy(urged = true)
            val i = DemoRepo.tickets.indexOfFirst { it.id == ticketId }
            if (i >= 0) DemoRepo.tickets[i] = updated
            DemoRepo.ticketLogs.add(
                TicketLog(
                    id = "l-$ticketId-${now}-u", ticketId = ticketId, at = now,
                    actorRoleRes = R.string.role_platform, actorName = "SewageTrace",
                    stage = t.stage, progressAfter = t.progress,
                    textRes = R.string.log_urged
                )
            )
            return updated
        }
        return t
    }

    /** 多人印证提级：同一水体 1km/72h 内 ≥3 位不同用户 */
    fun escalateIfNeeded(report: Report, now: Long = System.currentTimeMillis()): Boolean {
        val similar = DemoRepo.reports.filter {
            it.id != report.id &&
                distMeters(it.lat, it.lng, report.lat, report.lng) <= 1000 &&
                now - it.createdAt <= 72 * HOUR &&
                it.userId != report.userId
        }
        val distinctUsers = similar.map { it.userId }.distinct().size + 1
        if (distinctUsers >= 3) {
            report.ticketId?.let { tid ->
                val t = DemoRepo.ticketOf(tid) ?: return@let
                val updated = t.copy(escalated = true, reportCount = distinctUsers)
                val i = DemoRepo.tickets.indexOfFirst { it.id == tid }
                if (i >= 0) DemoRepo.tickets[i] = updated
                DemoRepo.ticketLogs.add(
                    TicketLog(
                        id = "l-$tid-${now}-e", ticketId = tid, at = now,
                        actorRoleRes = R.string.role_platform, actorName = "SewageTrace",
                        stage = t.stage, progressAfter = t.progress,
                        textRes = R.string.log_escalated, formatArgs = listOf(distinctUsers.toString())
                    )
                )
            }
            return true
        }
        return false
    }

    fun distMeters(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val r = 6371000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLng = Math.toRadians(lng2 - lng1)
        val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
            Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
            Math.sin(dLng / 2) * Math.sin(dLng / 2)
        return r * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
    }

    fun responseHours(ticketId: String): Double {
        val logs = DemoRepo.logsOf(ticketId)
        val created = DemoRepo.ticketOf(ticketId)?.createdAt ?: return 0.0
        val verified = logs.firstOrNull { it.stage == TicketStage.VERIFIED }?.at ?: return 0.0
        return ((verified - created).toDouble() / HOUR)
    }
}

/** 排行榜服务：有效发现数口径 + 去重 + 反推保护 */
object RankService {

    private const val DAY = 86_400_000L

    /** 是否计入统计：等级≥R2、未被驳回、同水体 1km/24h 未重复 */
    fun countsForRank(report: Report, all: List<Report>): Boolean {
        if (report.level == SeverityLevel.R0 || report.level == SeverityLevel.R1) return false
        if (report.status == ReportStatus.REJECTED) return false
        val dup = all.filter {
            it.id != report.id && it.status != ReportStatus.REJECTED &&
                Math.abs(it.createdAt - report.createdAt) <= DAY &&
                TicketService.distMeters(it.lat, it.lng, report.lat, report.lng) <= 1000
        }.minByOrNull { it.createdAt }
        return dup == null || report.createdAt <= dup.createdAt
    }

    fun buildBoard(scope: String, windowDays: Int, meId: String?): List<LeaderRow> {
        val now = System.currentTimeMillis()
        val since = if (windowDays <= 0) 0L else now - windowDays * DAY
        val myCity = DemoRepo.currentUser()?.city

        val rows = DemoRepo.users.map { u ->
            val mine = DemoRepo.reports.filter { it.userId == u.id && it.createdAt >= since }
            val visible = if (scope == "city") mine.filter { u.city == (DemoRepo.currentUser()?.city ?: "Kuala Lumpur") } else mine
            val valid = visible.filter { countsForRank(it, mine) }.size
            val accepted = visible.count { it.status == ReportStatus.IN_TICKET }
            val resolved = visible.mapNotNull { r -> r.ticketId?.let { DemoRepo.ticketOf(it) } }
                .count { it.stage == TicketStage.CLOSED || it.stage == TicketStage.RETEST }
            u to Triple(valid, accepted, resolved)
        }
            .filter { scope != "city" || it.first.city == (myCity ?: "Kuala Lumpur") }
            .sortedWith(
                compareByDescending<Pair<User, Triple<Int, Int, Int>>> { it.second.first }
                    .thenByDescending { it.second.second }
                    .thenBy { it.first.createdAt }
            )

        // 反推保护：活跃用户 < 5 人的城市榜隐藏
        if (scope == "city" && rows.size < 5) return emptyList()

        return rows.mapIndexed { i, (u, stat) ->
            LeaderRow(
                rank = i + 1,
                displayName = u.nickname,
                city = u.city,
                validFindings = stat.first,
                accepted = stat.second,
                resolved = stat.third,
                badgeRes = badgeOf(stat.first, stat.third),
                isMe = u.id == meId
            )
        }
    }

    private fun badgeOf(valid: Int, resolved: Int): Int? = when {
        valid >= 20 -> R.string.badge_river_guardian
        valid >= 10 -> R.string.badge_river_watcher
        resolved >= 3 -> R.string.badge_driver
        valid >= 5 -> R.string.badge_streak
        else -> null
    }
}

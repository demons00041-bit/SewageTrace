package com.sewagetrace.app.data

import android.content.Context
import com.sewagetrace.app.R
import com.sewagetrace.app.model.Agency
import com.sewagetrace.app.model.AgencyType
import com.sewagetrace.app.model.Booking
import com.sewagetrace.app.model.Confidence
import com.sewagetrace.app.model.Evidence
import com.sewagetrace.app.model.EvidenceDim
import com.sewagetrace.app.model.Report
import com.sewagetrace.app.model.ReportStatus
import com.sewagetrace.app.model.SeverityLevel
import com.sewagetrace.app.model.SuspectedType
import com.sewagetrace.app.model.Ticket
import com.sewagetrace.app.model.TicketLog
import com.sewagetrace.app.model.TicketStage
import com.sewagetrace.app.model.User
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 本地数据仓库（演示版，单机）。
 *
 * 城市固定为马来西亚吉隆坡；机构、工单、用户全部为演示数据，
 * 机构名称为虚构，不代表任何真实政府部门。
 * 真实部署时这一层替换为 Firestore/Cloud Functions，UI 与 ViewModel 无需改动。
 */
object DemoRepo {

    private const val PREF = "sewagetrace_demo_v2"

    private val prefs by lazy { AppContextHolder.ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE) }

    val users = mutableListOf<User>()
    val reports = mutableListOf<Report>()
    val tickets = mutableListOf<Ticket>()
    val ticketLogs = mutableListOf<TicketLog>()
    val agencies = mutableListOf<Agency>()
    val bookings = mutableListOf<Booking>()

    var currentUserId: String? = null
        private set

    fun currentUser(): User? = users.firstOrNull { it.id == currentUserId }

    fun ticketOf(ticketId: String): Ticket? = tickets.firstOrNull { it.id == ticketId }

    fun logsOf(ticketId: String): List<TicketLog> =
        ticketLogs.filter { it.ticketId == ticketId }.sortedBy { it.at }

    fun reportsOf(userId: String): List<Report> =
        reports.filter { it.userId == userId }.sortedByDescending { it.createdAt }

    fun load(ctx: Context) {
        AppContextHolder.ctx = ctx.applicationContext
        if (users.isEmpty()) seed()
        restoreSession()
    }

    fun login(userId: String) {
        currentUserId = userId
        prefs.edit().putString("session", userId).apply()
    }

    fun restoreSession() {
        if (currentUserId == null) {
            prefs.getString("session", null)?.let { sid ->
                if (users.any { it.id == sid }) currentUserId = sid
            }
        }
    }

    fun logout() {
        currentUserId = null
        prefs.edit().remove("session").apply()
    }

    fun register(nickname: String, phone: String, city: String): User {
        val u = User(
            id = "u-" + System.currentTimeMillis().toString(36),
            nickname = nickname,
            phone = phone,
            city = city,
            realName = true,
            anonymous = false,
            createdAt = System.currentTimeMillis()
        )
        users.add(u)
        login(u.id)
        return u
    }

    fun updateUser(u: User) {
        val i = users.indexOfFirst { it.id == u.id }
        if (i >= 0) users[i] = u
    }

    fun saveReport(r: Report) {
        reports.add(r)
        prefs.edit().putLong("lastReportAt", r.createdAt).apply()
    }

    fun updateReport(r: Report) {
        val i = reports.indexOfFirst { it.id == r.id }
        if (i >= 0) reports[i] = r
    }

    // ---------------------------------------------------------------- 种子数据

    private fun seed() {
        val now = System.currentTimeMillis()
        val day = 86_400_000L
        val hour = 3_600_000L

        // 演示用户（吉隆坡本地昵称）
        users += User("u-demo", "River Patrol Aunty", "012****0001", "Kuala Lumpur", true, false, now - 200 * day, isDemoUser = true)
        users += User("u-lin", "Aisyah R.", "012****0002", "Kuala Lumpur", true, false, now - 180 * day, isDemoUser = true)
        users += User("u-wang", "KL River Watch", "012****0003", "Petaling Jaya", true, false, now - 150 * day, isDemoUser = true)
        users += User("u-chen", "Tan M. H.", "012****0004", "Kuala Lumpur", true, true, now - 120 * day, isDemoUser = true)
        users += User("u-zhao", "Faizal A.", "012****0005", "Shah Alam", true, false, now - 90 * day, isDemoUser = true)

        // ------- 虚构机构（坐标为吉隆坡示意位置，不指向任何真实建筑） -------
        agencies += Agency(
            id = "gov-kl-001", name = "KL Federal Environment Agency",
            localName = "Jabatan Alam Sekitar Wilayah",
            type = AgencyType.GOVERNMENT, address = "Federal Government Precinct, Kuala Lumpur",
            phone = "03-8000-0100", hours = "Mon–Fri 08:30–17:00",
            lat = 3.1578, lng = 101.7118, standardRes = R.string.std_eqa
        )
        agencies += Agency(
            id = "gov-kl-002", name = "KL City Enforcement Office",
            localName = "Pejabat Penguatkuasaan DBKL",
            type = AgencyType.GOVERNMENT, address = "Jalan Raja Laut, Kuala Lumpur",
            phone = "03-8000-0200", hours = "Mon–Fri 08:00–16:30",
            lat = 3.1660, lng = 101.6950, standardRes = R.string.std_drainage
        )
        agencies += Agency(
            id = "wwtp-001", name = "Pantai Dalam Treatment Works",
            localName = "Loji Rawatan Air Sisa Pantai Dalam",
            type = AgencyType.WWTP, address = "Pantai Dalam, Kuala Lumpur",
            phone = "03-8000-0300", hours = "Operates 24 h · visits by appointment",
            lat = 3.0960, lng = 101.6730, standardRes = R.string.std_sewage
        )
        agencies += Agency(
            id = "station-001", name = "Klang River Monitoring Station 07",
            localName = "Stesen Pemantauan Sungai Klang 07",
            type = AgencyType.STATION, address = "Klang River bank, Chow Kit",
            phone = null, hours = "Automatic · not open to public",
            lat = 3.1720, lng = 101.6980, standardRes = R.string.std_river
        )
        agencies += Agency(
            id = "lab-001", name = "KL Water Assurance Laboratory",
            localName = "Makmal Jaminan Air KL",
            type = AgencyType.LAB, address = "Jalan Bangsar, Kuala Lumpur",
            phone = "03-8000-0401", hours = "Mon–Fri 09:00–16:30",
            lat = 3.1290, lng = 101.6860, accredited = true,
            tests = listOf("pH", "COD", "Ammoniacal nitrogen", "Total phosphorus", "Dissolved oxygen", "Heavy metals"),
            materials = listOf("ID document", "Sample ≥ 500 mL", "Sampling note")
        )
        agencies += Agency(
            id = "lab-002", name = "Central Valley Water Testing",
            localName = "Makmal Ujian Air Tengah Lembah",
            type = AgencyType.LAB, address = "Jalan Cheras, Kuala Lumpur",
            phone = "03-8000-0402", hours = "Mon–Sat 08:30–18:00",
            lat = 3.1240, lng = 101.7280, accredited = true,
            tests = listOf("pH", "COD", "Ammoniacal nitrogen", "Total phosphorus", "Dissolved oxygen"),
            materials = listOf("ID document", "Sample ≥ 500 mL, chilled")
        )

        // 他人上报的污水点（用于地图聚合与提级演示）
        val others = listOf(
            Triple("u-lin", "Klang River · Masjid Jamek reach", 3.1490 to 101.6960),
            Triple("u-wang", "Gombak River · PWTC reach", 3.1700 to 101.6940),
            Triple("u-zhao", "Sungai Kerayong · Cheras", 3.1250 to 101.7180),
            Triple("u-chen", "Sungai Bunus · Setapak", 3.1900 to 101.7100),
            Triple("u-lin", "Sungai Batu · Jinjang", 3.2100 to 101.6680)
        )
        others.forEachIndexed { idx, (uid, name, geo) ->
            val r = Report(
                id = "r-demo-$idx",
                userId = uid,
                waterbodyName = name,
                lat = geo.first,
                lng = geo.second,
                precision = 12,
                capturedAt = now - (idx + 2) * day,
                createdAt = now - (idx + 2) * day,
                level = SeverityLevel.R3,
                index = 62,
                confidence = Confidence.HIGH,
                suspectedType = SuspectedType.DOMESTIC,
                evidence = listOf(Evidence(EvidenceDim.COLOR_TURBIDITY, 65, sampleKey = "ev_heavy_color")),
                note = "",
                photoPath = "",
                status = ReportStatus.IN_TICKET,
                ticketId = "t-demo-$idx"
            )
            reports += r
            val t = Ticket(
                id = "t-demo-$idx",
                reportIds = listOf(r.id),
                waterbodyName = name,
                region = "Kuala Lumpur",
                agencyId = "gov-kl-002",
                agencyName = "KL City Enforcement Office",
                stage = TicketStage.TREATING,
                progress = 60,
                createdAt = now - (idx + 2) * day,
                slaDeadline = now - (idx + 2) * day + 30 * day,
                reporterUserId = uid,
                reportCount = 1 + idx
            )
            tickets += t
            ticketLogs += TicketLog(
                id = "l-${t.id}-1", ticketId = t.id, at = t.createdAt,
                actorRoleRes = R.string.role_platform, actorName = null,
                stage = TicketStage.SUBMITTED, progressAfter = 0, textRes = R.string.log_submitted
            )
            ticketLogs += TicketLog(
                id = "l-${t.id}-2", ticketId = t.id, at = t.createdAt + 2 * hour,
                actorRoleRes = R.string.role_platform, actorName = null,
                stage = TicketStage.POOLED, progressAfter = 0, textRes = R.string.log_pooled
            )
            ticketLogs += TicketLog(
                id = "l-${t.id}-3", ticketId = t.id, at = t.createdAt + 20 * hour,
                actorRoleRes = R.string.role_platform, actorName = null,
                stage = TicketStage.DISPATCHED, progressAfter = 5,
                textRes = R.string.log_dispatched, formatArgs = listOf(t.agencyName)
            )
            ticketLogs += TicketLog(
                id = "l-${t.id}-4", ticketId = t.id, at = t.createdAt + 2 * day,
                actorRoleRes = R.string.role_authority, actorName = null,
                stage = TicketStage.VERIFIED, progressAfter = 20, textRes = R.string.log_verified
            )
            ticketLogs += TicketLog(
                id = "l-${t.id}-5", ticketId = t.id, at = t.createdAt + 4 * day,
                actorRoleRes = R.string.role_authority, actorName = null,
                stage = TicketStage.FILED, progressAfter = 35, textRes = R.string.log_filed
            )
            ticketLogs += TicketLog(
                id = "l-${t.id}-6", ticketId = t.id, at = t.createdAt + 9 * day,
                actorRoleRes = R.string.role_operator, actorName = null,
                stage = TicketStage.TREATING, progressAfter = 60, textRes = R.string.log_treating
            )
        }
    }

    fun fmtTime(ms: Long): String {
        val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm",
            if (Settings.currentLang().startsWith("zh")) Locale.SIMPLIFIED_CHINESE else Locale.ENGLISH)
        return fmt.format(Date(ms))
    }
}

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
 * 两层场景：UM 校园（主场景，校内部门先受理）+ 吉隆坡外部机构（校外上报 / 校内移交协办）。
 * 规则见 [Campus]。机构、工单、用户全部为演示数据；外部机构名称为虚构，
 * 校内部门名称参照 UM 实际设置但电话、坐标均为占位值。
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

    fun register(nickname: String, studentEmail: String, city: String): User {
        val u = User(
            id = "u-" + System.currentTimeMillis().toString(36),
            nickname = nickname,
            phone = studentEmail.substringBefore('@'),
            city = city,
            realName = true,
            anonymous = false,
            createdAt = System.currentTimeMillis(),
            studentEmail = studentEmail
        )
        users.add(u)
        login(u.id)
        return u
    }

    /** 按校邮箱找人；没注册过就返回 null（由调用方决定是否自动注册） */
    fun findByEmail(email: String): User? =
        users.firstOrNull { it.studentEmail.equals(email, ignoreCase = true) }

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

        // 演示用户：UM 学生与校园社团（邮箱为示意学号）
        users += User("u-demo", "Green Volunteer · FSKTM", "012****0001", "Kuala Lumpur", true, false, now - 200 * day, isDemoUser = true, studentEmail = "s2000001@siswa.um.edu.my")
        users += User("u-lin", "Aisyah R. · KK12", "012****0002", "Kuala Lumpur", true, false, now - 180 * day, isDemoUser = true, studentEmail = "s2000002@siswa.um.edu.my")
        users += User("u-wang", "UM Eco Club", "012****0003", "Kuala Lumpur", true, false, now - 150 * day, isDemoUser = true, studentEmail = "s2000003@siswa.um.edu.my")
        users += User("u-chen", "Tan M. H. · Engineering", "012****0004", "Kuala Lumpur", true, true, now - 120 * day, isDemoUser = true, studentEmail = "s2000004@siswa.um.edu.my")
        users += User("u-zhao", "Faizal A. · KK5", "012****0005", "Petaling Jaya", true, false, now - 90 * day, isDemoUser = true, studentEmail = "s2000005@siswa.um.edu.my")

        // ======= 第一层：UM 校内单位（校内上报的首个受理方）=======
        // 部门名称参照 UM 实际设置，电话为占位值；坐标为校园内示意位置。
        agencies += Agency(
            id = "um-jpphb", name = "UM Development & Estate Maintenance (JPPHB)",
            localName = "Jabatan Pembangunan dan Penyelenggaraan Harta Bina",
            type = AgencyType.CAMPUS, address = "Universiti Malaya, Kuala Lumpur",
            phone = "03-0000-1001", hours = "Mon–Fri 08:00–17:00 · emergencies 24 h",
            lat = 3.1216, lng = 101.6562, standardRes = R.string.std_drainage
        )
        agencies += Agency(
            id = "um-sustain", name = "UM Sustainability Centre",
            localName = "Pusat Kelestarian UM",
            type = AgencyType.CAMPUS, address = "Universiti Malaya, Kuala Lumpur",
            phone = "03-0000-1002", hours = "Mon–Fri 08:30–17:00",
            lat = 3.1230, lng = 101.6528, standardRes = R.string.std_river
        )
        agencies += Agency(
            id = "um-kk-office", name = "Residential College Management Office",
            localName = "Pejabat Pengurusan Kolej Kediaman",
            type = AgencyType.CAMPUS, address = "Kolej Kediaman, Universiti Malaya",
            phone = "03-0000-1003", hours = "Mon–Fri 08:00–17:00 · college office after hours",
            lat = 3.1176, lng = 101.6500, standardRes = R.string.std_drainage
        )
        agencies += Agency(
            id = "um-lab-sci", name = "Faculty of Science Water Teaching Lab",
            localName = "Makmal Pengajaran Air, Fakulti Sains",
            type = AgencyType.LAB, address = "Faculty of Science, Universiti Malaya",
            phone = "03-0000-1004", hours = "Mon–Fri 09:00–16:00 · by appointment",
            lat = 3.1238, lng = 101.6575, accredited = false,
            fee = "Free for UM students (screening only)",
            tests = listOf("pH", "Turbidity", "Dissolved oxygen", "Ammoniacal nitrogen"),
            materials = listOf("UM student card", "Sample ≥ 250 mL")
        )

        // ======= 第二层：外部机构（校外上报直接受理；校内工单需要时移交协办）=======
        // 虚构名称，坐标为吉隆坡示意位置，不指向任何真实建筑
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

        // ======= 他人上报（地图点位 + 工单动态 + 提级演示）=======
        // 两条 Tasik Varsiti 上报在 72 h 内：演示用户再报一次同一水体 → 3 人印证 → 提级并移交外部机构
        val seeds = listOf(
            Seed("u-lin", "Tasik Varsiti · north bank", 3.1226, 101.6552, SeverityLevel.R2, 42, SuspectedType.BLACK_ODOROUS, 1, TicketStage.DISPATCHED),
            Seed("u-wang", "Tasik Varsiti · boathouse", 3.1214, 101.6560, SeverityLevel.R3, 57, SuspectedType.BLACK_ODOROUS, 2, TicketStage.VERIFIED),
            Seed("u-chen", "Cafeteria grease trap · FSKTM", 3.1278, 101.6498, SeverityLevel.R3, 55, SuspectedType.DOMESTIC, 6, TicketStage.TREATING),
            Seed("u-zhao", "Residential college drain · KK5", 3.1168, 101.6522, SeverityLevel.R2, 36, SuspectedType.DOMESTIC, 9, TicketStage.FILED),
            Seed("u-wang", "Klang River · Masjid Jamek reach", 3.1490, 101.6960, SeverityLevel.R3, 62, SuspectedType.DOMESTIC, 4, TicketStage.TREATING),
            Seed("u-zhao", "Sungai Kerayong · Cheras", 3.1250, 101.7180, SeverityLevel.R3, 64, SuspectedType.BLACK_ODOROUS, 5, TicketStage.TREATING),
            Seed("u-lin", "Gombak River · PWTC reach", 3.1700, 101.6940, SeverityLevel.R4, 74, SuspectedType.INDUSTRIAL, 7, TicketStage.TREATING)
        )
        seeds.forEachIndexed { idx, sd -> seedReport(idx, sd, now) }
    }

    private data class Seed(
        val userId: String,
        val waterbody: String,
        val lat: Double,
        val lng: Double,
        val level: SeverityLevel,
        val index: Int,
        val type: SuspectedType,
        val daysAgo: Int,
        val stage: TicketStage
    )

    /** 一条历史上报 + 对应工单 + 推进到指定阶段为止的日志 */
    private fun seedReport(idx: Int, sd: Seed, now: Long) {
        val day = 86_400_000L
        val hour = 3_600_000L
        val created = now - sd.daysAgo * day - 3 * hour
        val onCampus = Campus.contains(sd.lat, sd.lng)
        val agency = Campus.firstResponder(sd.lat, sd.lng)
        val agencyName = agency?.name ?: "KL City Enforcement Office"

        val r = Report(
            id = "r-demo-$idx", userId = sd.userId, waterbodyName = sd.waterbody,
            lat = sd.lat, lng = sd.lng, precision = 12,
            capturedAt = created, createdAt = created,
            level = sd.level, index = sd.index, confidence = Confidence.HIGH,
            suspectedType = sd.type,
            evidence = listOf(Evidence(EvidenceDim.COLOR_TURBIDITY, sd.index, sampleKey = "ev_heavy_color")),
            note = "", photoPath = "",
            status = ReportStatus.IN_TICKET, ticketId = "t-demo-$idx"
        )
        reports += r

        // 校内最高等级：建单即抄送外部机构（与 TicketService 规则一致）
        val referral = if (onCampus && sd.level == SeverityLevel.R4) Campus.externalReferral(sd.lat, sd.lng)?.name else null
        val order = TicketStage.entries
        val progress = when (sd.stage) {
            TicketStage.TREATING -> TicketService.computeProgress(TicketStage.TREATING, 0.4f)
            else -> TicketService.computeProgress(sd.stage, 1f)
        }
        val t = Ticket(
            id = "t-demo-$idx", reportIds = listOf(r.id), waterbodyName = sd.waterbody,
            region = if (onCampus) Campus.REGION else "Kuala Lumpur",
            agencyId = agency?.id, agencyName = agencyName,
            stage = sd.stage, progress = progress,
            createdAt = created, slaDeadline = created + 30 * day,
            reporterUserId = sd.userId, reportCount = 1,
            referredAgencyName = referral
        )
        tickets += t

        val campusRole = R.string.role_campus_unit
        val steps = listOf(
            Triple(TicketStage.SUBMITTED, 0L, Triple(R.string.role_platform, R.string.log_submitted, emptyList<String>())),
            Triple(TicketStage.POOLED, 2 * hour, Triple(R.string.role_platform, R.string.log_pooled, emptyList())),
            Triple(TicketStage.DISPATCHED, 20 * hour, Triple(R.string.role_platform, R.string.log_dispatched, listOf(agencyName))),
            Triple(TicketStage.VERIFIED, 2 * day, Triple(if (onCampus) campusRole else R.string.role_authority, R.string.log_verified, emptyList())),
            Triple(TicketStage.FILED, 4 * day, Triple(if (onCampus) campusRole else R.string.role_authority, R.string.log_filed, emptyList())),
            Triple(TicketStage.TREATING, 5 * day, Triple(if (onCampus) campusRole else R.string.role_operator, R.string.log_treating, emptyList()))
        )
        steps.filter { order.indexOf(it.first) <= order.indexOf(sd.stage) }.forEach { (stage, offset, n) ->
            // 日志时间不能晚于现在（近期工单的后续阶段时间被压缩）
            val at = minOf(created + offset, now - hour)
            ticketLogs += TicketLog(
                id = "l-${t.id}-${stage.name}", ticketId = t.id, at = at,
                actorRoleRes = n.first, actorName = null,
                stage = stage, progressAfter = TicketService.computeProgress(stage, if (stage == TicketStage.TREATING) 0.4f else 1f),
                textRes = n.second, formatArgs = n.third
            )
        }
        if (referral != null) {
            ticketLogs += TicketLog(
                id = "l-${t.id}-ref", ticketId = t.id, at = created + 1,
                actorRoleRes = campusRole, actorName = null,
                stage = TicketStage.SUBMITTED, progressAfter = 0,
                textRes = R.string.log_referred_severe, formatArgs = listOf(referral)
            )
        }
    }

    fun fmtTime(ms: Long): String {
        val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm",
            if (Settings.currentLang().startsWith("zh")) Locale.SIMPLIFIED_CHINESE else Locale.ENGLISH)
        return fmt.format(Date(ms))
    }
}

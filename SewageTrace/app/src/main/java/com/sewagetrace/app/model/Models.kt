package com.sewagetrace.app.model

import androidx.annotation.StringRes
import androidx.compose.ui.graphics.Color
import com.sewagetrace.app.R

enum class SeverityLevel(
    val code: String,
    val minScore: Int,
    val color: Color,
    /** 严重程度序号：R0=0（最轻）→ R4=4（最重）。不要用 ordinal，枚举声明是倒序的。 */
    val rank: Int
) {
    R4("R4", 70, Color(0xFFDC2626), 4),
    R3("R3", 50, Color(0xFFF97316), 3),
    R2("R2", 30, Color(0xFFEAB308), 2),
    R1("R1", 15, Color(0xFF84CC16), 1),
    R0("R0", 0, Color(0xFF22C55E), 0);

    companion object {
        fun of(score: Int): SeverityLevel = entries.first { score >= it.minScore }

        /**
         * 取两者中更严重的一档。
         * 业务规则：人工等级只能「上调」不能「下调」AI 判定，防止人工低估导致漏报。
         */
        fun moreSevere(a: SeverityLevel, b: SeverityLevel?): SeverityLevel =
            if (b == null || a.rank >= b.rank) a else b

        /** 由低到高的展示顺序：R0 → R4（人工等级选择器用这个） */
        fun ascending(): List<SeverityLevel> = entries.sortedBy { it.rank }
    }
}

/** 等级来源，对应需求 CAP-03 的 `level_source` */
enum class LevelSource(val id: String) {
    /** 只有 AI 判定，未人工分级（含历史数据） */
    AI("ai"),

    /** 人工分级，且与 AI 判定不同 */
    MANUAL("manual"),

    /** 人工分级，且与 AI 判定一致 */
    BOTH("both");

    companion object {
        fun of(ai: SeverityLevel, manual: SeverityLevel?): LevelSource = when {
            manual == null -> AI
            manual == ai -> BOTH
            else -> MANUAL
        }
    }
}

/**
 * 污染等级的五档语义。所有面向用户的文案都走 [labelRes] / [adviceRes]，
 * 由 UI 层用 stringResource 渲染，数据层不再持有任何语言相关字符串。
 */
enum class Severity(
    val level: SeverityLevel,
    @StringRes val labelRes: Int,
    @StringRes val adviceRes: Int,
    val autoReport: Boolean
) {
    R4(SeverityLevel.R4, R.string.grade_r4, R.string.grade_r4_advice, true),
    R3(SeverityLevel.R3, R.string.grade_r3, R.string.grade_r3_advice, true),
    R2(SeverityLevel.R2, R.string.grade_r2, R.string.grade_r2_advice, true),
    R1(SeverityLevel.R1, R.string.grade_r1, R.string.grade_r1_advice, false),
    R0(SeverityLevel.R0, R.string.grade_r0, R.string.grade_r0_advice, false);

    companion object {
        fun of(level: SeverityLevel): Severity = entries.first { it.level == level }
    }
}

enum class Confidence(@StringRes val labelRes: Int, @StringRes val descRes: Int, val weight: Int) {
    HIGH(R.string.conf_high, R.string.conf_high_desc, 3),
    MEDIUM(R.string.conf_medium, R.string.conf_medium_desc, 2),
    LOW(R.string.conf_low, R.string.conf_low_desc, 1)
}

enum class SuspectedType(val id: String, @StringRes val labelRes: Int) {
    DOMESTIC("domestic", R.string.type_domestic),
    INDUSTRIAL("industrial", R.string.type_industrial),
    LIVESTOCK("livestock", R.string.type_livestock),
    BLACK_ODOROUS("blackodorous", R.string.type_black_odorous),
    DUMPING("dumping", R.string.type_dumping),
    CONSTRUCTION("construction", R.string.type_construction),
    OIL("oil", R.string.type_oil),
    UNKNOWN("unknown", R.string.type_unknown);

    companion object {
        fun byId(id: String?): SuspectedType = entries.firstOrNull { it.id == id } ?: UNKNOWN
    }
}

enum class ReportStatus(@StringRes val labelRes: Int) {
    DRAFT(R.string.repstatus_draft),
    REPORTED(R.string.repstatus_reported),
    REJECTED(R.string.repstatus_rejected),
    IN_TICKET(R.string.repstatus_inticket)
}

enum class AgencyType(@StringRes val labelRes: Int, val icon: String, val filterKey: String) {
    GOVERNMENT(R.string.agency_type_gov, "🏛", "gov"),
    WWTP(R.string.agency_type_plant, "🏭", "plant"),
    STATION(R.string.agency_type_monitor, "📈", "monitor"),
    LAB(R.string.agency_type_lab, "🔬", "lab")
}

enum class TicketStage(@StringRes val labelRes: Int) {
    SUBMITTED(R.string.stage_1),
    POOLED(R.string.stage_2),
    DISPATCHED(R.string.stage_3),
    VERIFIED(R.string.stage_4),
    FILED(R.string.stage_5),
    TREATING(R.string.stage_6),
    RETEST(R.string.stage_7),
    CLOSED(R.string.stage_8)
}

enum class BookingStatus(@StringRes val labelRes: Int) {
    BOOKED(R.string.booking_status_booked),
    CANCELLED(R.string.booking_status_cancelled),
    DONE(R.string.booking_status_done)
}

/** 证据维度（取代原来的中文字符串维度名） */
enum class EvidenceDim(@StringRes val labelRes: Int) {
    COLOR_TURBIDITY(R.string.dim_color),
    FLOATABLES(R.string.dim_floatables),
    FOAM_OIL(R.string.dim_foam_oil),
    WATER_STATE(R.string.dim_state),
    EUTROPHICATION(R.string.dim_eutro),
    QUALITY(R.string.dim_quality)
}

/**
 * 一条视觉证据。
 * - [dim] 决定维度标题的中英文；
 * - [noteRes] 非空时用资源串（云端 AI / 规则引擎的自由描述）；
 * - [sampleKey] 非空时表示“这是可翻译的样例陈述”，UI 会用 assets 里的双语样例表渲染，
 *   这样切换语言时历史记录里的描述也能跟着变。
 */
data class Evidence(
    val dim: EvidenceDim,
    val weight: Int,
    /** 键值：本机引擎为 note_* / quality 之类的代码，云端为模型给出的自由描述 */
    val noteRes: String? = null,
    /** 键值说明：[noteRes] 是字符串键还是 Android 资源 id */
    val noteResId: Int? = null,
    /** assets 双语样例表里的 key */
    val sampleKey: String? = null
)

data class User(
    val id: String,
    val nickname: String,
    val phone: String,
    val city: String,
    val realName: Boolean,
    val anonymous: Boolean,
    val createdAt: Long,
    val isDemoUser: Boolean = false,
    /** UM 校邮箱，形如 a12345678@siswa.um.edu.my */
    val studentEmail: String = ""
) {
    val isMinor: Boolean get() = false

    /** 学号部分（邮箱 @ 前），个人页展示用 */
    val matric: String get() = studentEmail.substringBefore('@')
}

data class Report(
    val id: String,
    val userId: String,
    val waterbodyName: String,
    val lat: Double,
    val lng: Double,
    val precision: Int,
    val capturedAt: Long,
    val createdAt: Long,
    val level: SeverityLevel,
    val index: Int,
    val confidence: Confidence,
    val suspectedType: SuspectedType,
    val evidence: List<Evidence>,
    val note: String,
    val photoPath: String,
    val status: ReportStatus,
    val ticketId: String? = null,
    val userAdjusted: Boolean = false,
    val aiIndex: Int = index,
    val aiLevel: SeverityLevel = level,
    /** 人工确认的等级；null = 未分级（历史数据，或没有照片的纯点位记录） */
    val manualLevel: SeverityLevel? = null,
    /** 等级来源：ai / manual / both */
    val levelSource: LevelSource = LevelSource.AI,
    val dedupCounted: Boolean = true,
    val engine: String = ENGINE_LOCAL
) {
    val severity: Severity get() = Severity.of(level)

    /** 展示用等级：人工优先，没人工才回落到 AI */
    val displayLevel: SeverityLevel get() = manualLevel ?: level

    /** 业务路由用等级：人工只能上调，取更严重的一档 */
    val effectiveLevel: SeverityLevel get() = SeverityLevel.moreSevere(level, manualLevel)

    /** 人工与 AI 不一致（详情页要高亮提示复核） */
    val levelMismatch: Boolean get() = manualLevel != null && manualLevel != level

    companion object {
        const val ENGINE_LOCAL = "local"
        const val ENGINE_CLOUD = "cloud"
    }
}

data class Ticket(
    val id: String,
    val reportIds: List<String>,
    val waterbodyName: String,
    val region: String,
    val agencyId: String?,
    val agencyName: String,
    val stage: TicketStage,
    val progress: Int,
    val createdAt: Long,
    val slaDeadline: Long,
    val reporterUserId: String,
    val reportCount: Int,
    val escalated: Boolean = false,
    val urged: Boolean = false,
    val disputed: Boolean = false,
    val thanked: Boolean = false
) {
    val isOverdue: Boolean get() = System.currentTimeMillis() > slaDeadline && stage != TicketStage.CLOSED
    val overdueDays: Int get() = if (!isOverdue) 0 else ((System.currentTimeMillis() - slaDeadline) / 86_400_000L).toInt().coerceAtLeast(1)
}

/**
 * 工单日志。文案一律存 [textKey]（资源名，如 "log_pooled"），
 * [formatArgs] 保存需要插值的参数，渲染时再查当前 locale 的资源，
 * 因此切换语言后历史日志也会同步变成另一种语言。
 */
data class TicketLog(
    val id: String,
    val ticketId: String,
    val at: Long,
    @StringRes val actorRoleRes: Int,
    val actorName: String?,
    val stage: TicketStage,
    val progressAfter: Int,
    @StringRes val textRes: Int,
    val formatArgs: List<String> = emptyList(),
    val photoPath: String? = null,
    val isDispute: Boolean = false
)

data class Agency(
    val id: String,
    val name: String,
    val localName: String,
    val type: AgencyType,
    val address: String,
    val phone: String?,
    val hours: String,
    val lat: Double,
    val lng: Double,
    @StringRes val standardRes: Int? = null,
    val accredited: Boolean = false,
    /** 参考费用说明，null 表示面议 */
    val fee: String? = null,
    val tests: List<String> = emptyList(),
    val materials: List<String> = emptyList()
)

data class Booking(
    val id: String,
    val userId: String,
    val agencyId: String,
    val agencyName: String,
    val slotDate: String,
    val slotTime: String,
    val tests: List<String>,
    val materials: List<String>,
    val status: BookingStatus,
    val createdAt: Long,
    val code: String,
    val report: List<Pair<String, String>>? = null
)

data class LeaderRow(
    val rank: Int,
    val displayName: String,
    val city: String,
    val validFindings: Int,
    val accepted: Int,
    val resolved: Int,
    @StringRes val badgeRes: Int?,
    val isMe: Boolean
)

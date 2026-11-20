package com.sewagetrace.app.data.db

import com.sewagetrace.app.model.Booking
import com.sewagetrace.app.model.BookingStatus
import com.sewagetrace.app.model.Confidence
import com.sewagetrace.app.model.Evidence
import com.sewagetrace.app.model.EvidenceDim
import com.sewagetrace.app.model.LevelSource
import com.sewagetrace.app.model.Report
import com.sewagetrace.app.model.ReportStatus
import com.sewagetrace.app.model.SeverityLevel
import com.sewagetrace.app.model.SuspectedType
import com.sewagetrace.app.model.Ticket
import com.sewagetrace.app.model.TicketLog
import com.sewagetrace.app.model.TicketStage
import com.sewagetrace.app.model.User
import org.json.JSONArray
import org.json.JSONObject

/**
 * 字符串资源 id ↔ 资源名 的转换。
 * App 里用 Resources 实现；单元测试里用假的映射表，这样映射逻辑可以脱离 Android 测试。
 */
interface ResKeys {
    /** 资源 id → 资源名；0 或找不到时返回 null */
    fun keyOf(id: Int): String?
    /** 资源名 → 资源 id；找不到时返回 [fallback] */
    fun idOf(key: String?, fallback: Int): Int
}

/** 枚举按名称恢复；名称不认识时（比如旧数据）回落到默认值，而不是崩溃 */
private inline fun <reified E : Enum<E>> enumOr(name: String?, default: E): E =
    name?.let { n -> enumValues<E>().firstOrNull { it.name == n } } ?: default

private fun List<String>.toJson(): String = JSONArray(this).toString()
private fun String?.toStringList(): List<String> {
    if (this.isNullOrBlank()) return emptyList()
    val a = JSONArray(this)
    return List(a.length()) { a.getString(it) }
}

// ------------------------------------------------------------------ User

fun User.toEntity() = UserEntity(id, nickname, phone, city, realName, anonymous, createdAt, isDemoUser, studentEmail)

fun UserEntity.toModel() = User(id, nickname, phone, city, realName, anonymous, createdAt, isDemoUser, studentEmail)

// ------------------------------------------------------------------ Report

fun Report.toEntity(res: ResKeys) = ReportEntity(
    id = id, userId = userId, waterbodyName = waterbodyName,
    lat = lat, lng = lng, precision = precision,
    capturedAt = capturedAt, createdAt = createdAt,
    level = level.name, index = index,
    confidence = confidence.name, suspectedType = suspectedType.name,
    evidenceJson = evidenceToJson(evidence, res),
    note = note, photoPath = photoPath, status = status.name, ticketId = ticketId,
    userAdjusted = userAdjusted, aiIndex = aiIndex, aiLevel = aiLevel.name,
    manualLevel = manualLevel?.name, levelSource = levelSource.name,
    dedupCounted = dedupCounted, engine = engine
)

fun ReportEntity.toModel(res: ResKeys): Report {
    val lvl = enumOr(level, SeverityLevel.R0)
    return Report(
        id = id, userId = userId, waterbodyName = waterbodyName,
        lat = lat, lng = lng, precision = precision,
        capturedAt = capturedAt, createdAt = createdAt,
        level = lvl, index = index,
        confidence = enumOr(confidence, Confidence.MEDIUM),
        suspectedType = enumOr(suspectedType, SuspectedType.UNKNOWN),
        evidence = evidenceFromJson(evidenceJson, res),
        note = note, photoPath = photoPath,
        status = enumOr(status, ReportStatus.DRAFT), ticketId = ticketId,
        userAdjusted = userAdjusted, aiIndex = aiIndex,
        aiLevel = enumOr(aiLevel, lvl),
        manualLevel = manualLevel?.let { enumOr<SeverityLevel>(it, lvl) },
        levelSource = enumOr(levelSource, LevelSource.AI),
        dedupCounted = dedupCounted, engine = engine
    )
}

internal fun evidenceToJson(list: List<Evidence>, res: ResKeys): String {
    val a = JSONArray()
    list.forEach { e ->
        a.put(JSONObject().apply {
            put("dim", e.dim.name)
            put("weight", e.weight)
            e.noteRes?.let { put("noteRes", it) }
            e.noteResId?.let { id -> res.keyOf(id)?.let { put("noteKey", it) } }
            e.sampleKey?.let { put("sampleKey", it) }
        })
    }
    return a.toString()
}

internal fun evidenceFromJson(json: String, res: ResKeys): List<Evidence> {
    if (json.isBlank()) return emptyList()
    val a = JSONArray(json)
    return List(a.length()) { i ->
        val o = a.getJSONObject(i)
        Evidence(
            dim = enumOr(o.optString("dim"), EvidenceDim.QUALITY),
            weight = o.optInt("weight"),
            noteRes = o.optString("noteRes").ifEmpty { null },
            noteResId = o.optString("noteKey").ifEmpty { null }
                ?.let { res.idOf(it, 0) }?.takeIf { it != 0 },
            sampleKey = o.optString("sampleKey").ifEmpty { null }
        )
    }
}

// ------------------------------------------------------------------ Ticket

fun Ticket.toEntity() = TicketEntity(
    id, reportIds.toJson(), waterbodyName, region, agencyId, agencyName, stage.name, progress,
    createdAt, slaDeadline, reporterUserId, reportCount, escalated, urged, disputed, thanked, referredAgencyName
)

fun TicketEntity.toModel() = Ticket(
    id = id, reportIds = reportIdsJson.toStringList(), waterbodyName = waterbodyName, region = region,
    agencyId = agencyId, agencyName = agencyName, stage = enumOr(stage, TicketStage.SUBMITTED),
    progress = progress, createdAt = createdAt, slaDeadline = slaDeadline,
    reporterUserId = reporterUserId, reportCount = reportCount,
    escalated = escalated, urged = urged, disputed = disputed, thanked = thanked,
    referredAgencyName = referredAgencyName
)

// ------------------------------------------------------------------ TicketLog

/**
 * @param fallbackRole / fallbackText 资源名在新版本里找不到时用的兜底文案
 *        （比如某条文案被删了），保证旧日志还能显示而不是崩溃。
 */
fun TicketLog.toEntity(res: ResKeys) = TicketLogEntity(
    id = id, ticketId = ticketId, at = at,
    actorRoleKey = res.keyOf(actorRoleRes) ?: "",
    actorName = actorName, stage = stage.name, progressAfter = progressAfter,
    textKey = res.keyOf(textRes) ?: "",
    formatArgsJson = formatArgs.toJson(), photoPath = photoPath, isDispute = isDispute
)

fun TicketLogEntity.toModel(res: ResKeys, fallbackRole: Int, fallbackText: Int) = TicketLog(
    id = id, ticketId = ticketId, at = at,
    actorRoleRes = res.idOf(actorRoleKey, fallbackRole),
    actorName = actorName, stage = enumOr(stage, TicketStage.SUBMITTED), progressAfter = progressAfter,
    textRes = res.idOf(textKey, fallbackText),
    formatArgs = formatArgsJson.toStringList(), photoPath = photoPath, isDispute = isDispute
)

// ------------------------------------------------------------------ Booking

fun Booking.toEntity() = BookingEntity(
    id, userId, agencyId, agencyName, slotDate, slotTime, tests.toJson(), materials.toJson(),
    status.name, createdAt, code,
    report?.let { pairs ->
        JSONArray().apply { pairs.forEach { (k, v) -> put(JSONArray().put(k).put(v)) } }.toString()
    }
)

fun BookingEntity.toModel() = Booking(
    id = id, userId = userId, agencyId = agencyId, agencyName = agencyName,
    slotDate = slotDate, slotTime = slotTime,
    tests = testsJson.toStringList(), materials = materialsJson.toStringList(),
    status = enumOr(status, BookingStatus.BOOKED), createdAt = createdAt, code = code,
    report = reportJson?.let { j ->
        val a = JSONArray(j)
        List(a.length()) { a.getJSONArray(it).let { p -> p.getString(0) to p.getString(1) } }
    }
)

package com.sewagetrace.app

import com.sewagetrace.app.data.db.PersistentList
import com.sewagetrace.app.data.db.ResKeys
import com.sewagetrace.app.data.db.TicketLogEntity
import com.sewagetrace.app.data.db.toEntity
import com.sewagetrace.app.data.db.toModel
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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** 模型 ↔ 数据库行的往返映射：存进去再读出来必须和原来一模一样 */
class PersistenceMappingTest {

    /**
     * 模拟「两次编译之间资源 id 变了」：写入时用一套 id，读取时用另一套 id。
     * 存资源名而不是 id，读出来就应该映射到新 id 上。
     */
    private class FakeRes(private val idToKey: Map<Int, String>) : ResKeys {
        private val keyToId = idToKey.entries.associate { (k, v) -> v to k }
        override fun keyOf(id: Int): String? = idToKey[id]
        override fun idOf(key: String?, fallback: Int): Int = key?.let { keyToId[it] } ?: fallback
    }

    private val buildA = FakeRes(mapOf(100 to "role_platform", 101 to "log_dispatched", 102 to "note_flag_blur"))
    private val buildB = FakeRes(mapOf(900 to "role_platform", 901 to "log_dispatched", 902 to "note_flag_blur"))

    private fun sampleReport() = Report(
        id = "r-1", userId = "u-1", waterbodyName = "Tasik Varsiti · north bank",
        lat = 3.1222, lng = 101.6548, precision = 12,
        capturedAt = 1_000L, createdAt = 2_000L,
        level = SeverityLevel.R2, index = 41, confidence = Confidence.HIGH,
        suspectedType = SuspectedType.BLACK_ODOROUS,
        evidence = listOf(
            Evidence(EvidenceDim.COLOR_TURBIDITY, 65, sampleKey = "ev_heavy_color"),
            Evidence(EvidenceDim.QUALITY, 10, noteRes = "quality", noteResId = 102)
        ),
        note = "Green film near the outlet", photoPath = "/x/p.jpg",
        status = ReportStatus.IN_TICKET, ticketId = "t-1",
        userAdjusted = true, aiIndex = 41, aiLevel = SeverityLevel.R2,
        manualLevel = SeverityLevel.R3, levelSource = LevelSource.MANUAL,
        dedupCounted = true, engine = Report.ENGINE_LOCAL
    )

    @Test fun report_roundTrip_keepsEverything() {
        val r = sampleReport()
        assertEquals(r, r.toEntity(buildA).toModel(buildA))
    }

    @Test fun report_evidenceResourceId_survivesRebuild() {
        val back = sampleReport().toEntity(buildA).toModel(buildB)
        assertEquals(902, back.evidence[1].noteResId)
        assertEquals("ev_heavy_color", back.evidence[0].sampleKey)
    }

    @Test fun report_nullManualLevel_staysNull() {
        val r = sampleReport().copy(manualLevel = null, levelSource = LevelSource.AI, ticketId = null)
        assertEquals(r, r.toEntity(buildA).toModel(buildA))
    }

    @Test fun ticket_roundTrip_includingReferral() {
        val t = Ticket(
            id = "t-1", reportIds = listOf("r-1", "r-2"), waterbodyName = "Tasik Varsiti",
            region = "Universiti Malaya", agencyId = "um-jpphb", agencyName = "JPPHB",
            stage = TicketStage.VERIFIED, progress = 20, createdAt = 5L, slaDeadline = 99L,
            reporterUserId = "u-1", reportCount = 3, escalated = true,
            referredAgencyName = "KL Federal Environment Agency"
        )
        assertEquals(t, t.toEntity().toModel())
    }

    @Test fun ticketLog_textResource_survivesRebuild() {
        val log = TicketLog(
            id = "l-1", ticketId = "t-1", at = 7L, actorRoleRes = 100, actorName = "SewageTrace",
            stage = TicketStage.DISPATCHED, progressAfter = 5, textRes = 101,
            formatArgs = listOf("UM Development & Estate Maintenance (JPPHB)")
        )
        val back = log.toEntity(buildA).toModel(buildB, fallbackRole = -1, fallbackText = -2)
        assertEquals(900, back.actorRoleRes)
        assertEquals(901, back.textRes)
        assertEquals(log.formatArgs, back.formatArgs)
    }

    @Test fun ticketLog_deletedResource_fallsBackInsteadOfCrashing() {
        val row = TicketLogEntity(
            id = "l-1", ticketId = "t-1", at = 1L, actorRoleKey = "role_removed", actorName = null,
            stage = "NOT_A_STAGE", progressAfter = 0, textKey = "log_removed",
            formatArgsJson = "[]", photoPath = null, isDispute = false
        )
        val back = row.toModel(buildB, fallbackRole = -1, fallbackText = -2)
        assertEquals(-1, back.actorRoleRes)
        assertEquals(-2, back.textRes)
        assertEquals(TicketStage.SUBMITTED, back.stage)
    }

    @Test fun booking_roundTrip_withAndWithoutLabReport() {
        val b = Booking(
            id = "b-1", userId = "u-1", agencyId = "lab-001", agencyName = "Lab",
            slotDate = "2026-12-01", slotTime = "10:00", tests = listOf("pH", "COD"),
            materials = listOf("ID"), status = BookingStatus.DONE, createdAt = 3L, code = "ST-1234",
            report = listOf("pH" to "7.1", "COD" to "45 mg/L")
        )
        assertEquals(b, b.toEntity().toModel())
        val noReport = b.copy(report = null, status = BookingStatus.BOOKED)
        assertNull(noReport.toEntity().toModel().report)
    }

    @Test fun user_roundTrip() {
        val u = User("u-1", "Aisyah", "012", "Kuala Lumpur", true, false, 9L, true, "s1@siswa.um.edu.my")
        assertEquals(u, u.toEntity().toModel())
    }

    @Test fun persistentList_notifiesOnWrites_butNotOnSilentLoad() {
        var n = 0
        val list = PersistentList<String> { n++ }
        list.loadSilently(listOf("a", "b"))
        assertEquals(0, n)
        list.add("c"); list.add(0, "z"); list[1] = "A"; list.removeAt(0); list.remove("b"); list.addAll(listOf("d"))
        assertEquals(6, n)
        list.remove("not-there")          // 没变化就不写库
        list.addAll(emptyList())
        assertEquals(6, n)
        assertEquals(listOf("A", "c", "d"), list.toList())
    }
}

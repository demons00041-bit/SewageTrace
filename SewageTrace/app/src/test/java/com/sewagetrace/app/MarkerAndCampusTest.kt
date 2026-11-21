package com.sewagetrace.app

import com.sewagetrace.app.data.Campus
import com.sewagetrace.app.data.DemoRepo
import com.sewagetrace.app.data.MarkerService
import com.sewagetrace.app.model.Agency
import com.sewagetrace.app.model.AgencyType
import com.sewagetrace.app.model.LevelSource
import com.sewagetrace.app.model.Report
import com.sewagetrace.app.model.ReportStatus
import com.sewagetrace.app.model.SeverityLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** 地图标记 CRUD 的业务规则 + 校内/校外两层受理 */
class MarkerAndCampusTest {

    private fun agency(id: String, type: AgencyType, lat: Double, lng: Double) =
        Agency(id, id, id, type, "", null, "", lat, lng)

    @Before fun setUp() {
        DemoRepo.reports.loadSilently(emptyList())
        DemoRepo.tickets.loadSilently(emptyList())
        DemoRepo.agencies.clear()
        DemoRepo.agencies += agency("um-jpphb", AgencyType.CAMPUS, 3.1216, 101.6562)
        DemoRepo.agencies += agency("um-kk-office", AgencyType.CAMPUS, 3.1176, 101.6500)
        DemoRepo.agencies += agency("gov-kl-001", AgencyType.GOVERNMENT, 3.1578, 101.7118)
        DemoRepo.agencies += agency("gov-kl-002", AgencyType.GOVERNMENT, 3.1660, 101.6950)
    }

    // ---------------------------------------------------------------- 校内 / 校外

    @Test fun campusReport_goesToNearestCampusUnit() {
        assertEquals("um-kk-office", Campus.firstResponder(3.1178, 101.6508)?.id)   // 宿舍排水渠
        assertEquals("um-jpphb", Campus.firstResponder(3.1222, 101.6548)?.id)       // Tasik Varsiti
    }

    @Test fun offCampusReport_goesToExternalAuthority() {
        val a = Campus.firstResponder(3.1490, 101.6960)                             // Klang River
        assertEquals(AgencyType.GOVERNMENT, a?.type)
    }

    @Test fun referral_isAlwaysAnExternalAuthority() {
        assertEquals(AgencyType.GOVERNMENT, Campus.externalReferral(3.1222, 101.6548)?.type)
    }

    @Test fun geofence_boundaries() {
        assertTrue(Campus.contains(Campus.LAT, Campus.LNG))
        assertTrue(Campus.contains(3.1272, 101.6505))    // FSKTM 一带（演示用户位置）
        assertFalse(Campus.contains(3.1290, 101.6860))   // Bangsar
    }

    // ---------------------------------------------------------------- 标记 CRUD

    @Test fun create_makesManualDraftWithManualLevel() {
        val r = MarkerService.create("u-me", "  Drain behind KK12  ", SeverityLevel.R3, "oily sheen", 3.118, 101.651, 50, now = 1_000L)
        assertEquals(ReportStatus.DRAFT, r.status)
        assertEquals(Report.ENGINE_MANUAL, r.engine)
        assertEquals("Drain behind KK12", r.waterbodyName)
        assertEquals(SeverityLevel.R3, r.manualLevel)
        assertEquals(SeverityLevel.R3, r.effectiveLevel)
        assertEquals(LevelSource.MANUAL, r.levelSource)
        assertEquals(1, DemoRepo.reports.size)
    }

    @Test fun update_manualMarker_changesLevelItself() {
        val r = MarkerService.create("u-me", "A", SeverityLevel.R1, "", 3.118, 101.651, 50)
        val u = MarkerService.update(r.id, "u-me", "B", SeverityLevel.R4, "worse now")!!
        assertEquals("B", u.waterbodyName)
        assertEquals(SeverityLevel.R4, u.level)
        assertEquals(MarkerService.representativeIndex(SeverityLevel.R4), u.index)
    }

    @Test fun update_photoReport_keepsAiVerdict_andRoutesByMax() {
        val ai = MarkerService.create("u-me", "A", SeverityLevel.R3, "", 3.118, 101.651, 50)
            .copy(engine = Report.ENGINE_LOCAL, manualLevel = null, levelSource = LevelSource.AI)
        DemoRepo.updateReport(ai)
        val u = MarkerService.update(ai.id, "u-me", "A", SeverityLevel.R1, "")!!
        assertEquals(SeverityLevel.R3, u.level)            // AI 结论不变
        assertEquals(SeverityLevel.R1, u.manualLevel)
        assertEquals(SeverityLevel.R3, u.effectiveLevel)   // 人工只能上调，不能下调
        assertEquals(LevelSource.MANUAL, u.levelSource)
    }

    @Test fun othersMarkers_cannotBeTouched() {
        val r = MarkerService.create("u-other", "A", SeverityLevel.R2, "", 3.118, 101.651, 50)
        assertNull(MarkerService.update(r.id, "u-me", "hacked", SeverityLevel.R0, ""))
        assertNull(MarkerService.move(r.id, "u-me", 0.0, 0.0))
        assertFalse(MarkerService.delete(r.id, "u-me"))
        assertFalse(MarkerService.canEdit(r, null))
        assertEquals(1, DemoRepo.reports.size)
    }

    @Test fun move_and_delete_ownDraft() {
        val r = MarkerService.create("u-me", "A", SeverityLevel.R2, "", 3.118, 101.651, 50)
        val m = MarkerService.move(r.id, "u-me", 3.1200, 101.6550)
        assertNotNull(m)
        assertEquals(3.1200, DemoRepo.reports.single().lat, 1e-9)
        assertTrue(MarkerService.delete(r.id, "u-me"))
        assertTrue(DemoRepo.reports.isEmpty())
    }

    @Test fun reportInTicket_canBeEdited_butNotMovedOrDeleted() {
        val r = MarkerService.create("u-me", "A", SeverityLevel.R3, "", 3.118, 101.651, 50)
        DemoRepo.updateReport(r.copy(status = ReportStatus.IN_TICKET, ticketId = "t-1"))
        assertNotNull(MarkerService.update(r.id, "u-me", "Renamed", SeverityLevel.R3, ""))
        assertNull(MarkerService.move(r.id, "u-me", 3.12, 101.65))
        assertFalse(MarkerService.delete(r.id, "u-me"))
        assertEquals(3.118, DemoRepo.reports.single().lat, 1e-9)
    }
}

package com.sewagetrace.app.ui

import androidx.annotation.StringRes
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sewagetrace.app.R
import com.sewagetrace.app.ai.AnalysisResult
import com.sewagetrace.app.ai.AutoClassifier
import com.sewagetrace.app.ai.CloudClassifier
import com.sewagetrace.app.ai.CloudException
import com.sewagetrace.app.ai.DemoPhotoFactory
import com.sewagetrace.app.ai.DemoScene
import com.sewagetrace.app.data.AnalysisMode
import com.sewagetrace.app.data.AppContextHolder
import com.sewagetrace.app.data.DemoRepo
import com.sewagetrace.app.data.FallbackNotice
import com.sewagetrace.app.data.ReportFlow
import com.sewagetrace.app.data.Settings
import com.sewagetrace.app.data.StatsService
import com.sewagetrace.app.data.SubmitOutcome
import com.sewagetrace.app.data.TicketService
import com.sewagetrace.app.data.defaultSceneForCity
import com.sewagetrace.app.model.Agency
import com.sewagetrace.app.model.AgencyType
import com.sewagetrace.app.model.Booking
import com.sewagetrace.app.model.LeaderRow
import com.sewagetrace.app.model.Report
import com.sewagetrace.app.model.Ticket
import com.sewagetrace.app.model.TicketLog
import com.sewagetrace.app.model.TicketStage
import com.sewagetrace.app.model.User
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 识别页的即时状态机 */
enum class DetectPhase { IDLE, SHOOTING, ANALYZING, DONE, SUBMITTED }

/** API Key 测试状态 */
sealed interface KeyTestState {
    data object Idle : KeyTestState
    data object Running : KeyTestState
    data class Ok(val detail: String) : KeyTestState
    data class Failed(val reason: String) : KeyTestState
}

class AppViewModel : ViewModel() {

    // ------------------------------------------------------------ 路由
    var screen by mutableStateOf<Screen>(Screen.Auth)
        private set
    var stack by mutableStateOf<List<Screen>>(emptyList())
        private set

    var tick by mutableStateOf(0)
        private set

    fun refresh() { tick++ }

    fun go(s: Screen) {
        stack = stack + screen
        screen = s
        tick++
    }

    fun back(): Boolean {
        if (stack.isEmpty()) return false
        screen = stack.last()
        stack = stack.dropLast(1)
        tick++
        return true
    }

    fun home(tab: Tab) {
        stack = emptyList()
        screen = Screen.Home(tab)
        tick++
    }

    // ------------------------------------------------------------ 会话
    val user: User? get() = DemoRepo.currentUser()
    val loggedIn: Boolean get() = user != null

    fun login(userId: String) {
        DemoRepo.login(userId)
        superLoadScene()
        screen = Screen.Home(Tab.DETECT)
        stack = emptyList()
        tick++
    }

    fun register(nickname: String, phone: String, city: String) {
        DemoRepo.register(nickname, phone, city)
        superLoadScene()
        screen = Screen.Home(Tab.DETECT)
        stack = emptyList()
        tick++
    }

    fun logout() {
        DemoRepo.logout()
        resetDetect()
        screen = Screen.Auth
        stack = emptyList()
        tick++
    }

    fun updateProfile(nickname: String, city: String, anonymous: Boolean) {
        val u = user ?: return
        DemoRepo.updateUser(u.copy(nickname = nickname, city = city, anonymous = anonymous))
        Settings.anonymous = anonymous
        tick++
    }

    private fun superLoadScene() {
        // 进入首页时按用户城市预选一个示例场景
        scene = user?.let { defaultSceneForCity(it.city) }
    }

    // ------------------------------------------------------------ 识别
    var phase by mutableStateOf(DetectPhase.IDLE)
        private set
    var scene by mutableStateOf<DemoScene?>(null)
        private set
    var photoPath by mutableStateOf<String?>(null)
        private set
    var analysis by mutableStateOf<AnalysisResult?>(null)
        private set
    var analyzing by mutableStateOf(false)
        private set
    var waterbody by mutableStateOf("")
    var note by mutableStateOf("")

    /** 自动上报开关，持久化到 Settings */
    var autoReport by mutableStateOf(Settings.autoReport)
        private set

    /** 位置精度（米），持久化 */
    var locationPrecision by mutableStateOf(Settings.locationPrecision)
        private set

    fun updateAutoReport(v: Boolean) {
        autoReport = v
        Settings.autoReport = v
        tick++
    }

    fun updateLocationPrecision(v: Int) {
        locationPrecision = v
        Settings.locationPrecision = v
        tick++
    }

    var outcome by mutableStateOf<SubmitOutcome?>(null)
        private set
    var analyzingProgress by mutableStateOf(0)
        private set
    var fallback by mutableStateOf<FallbackNotice?>(null)
        private set

    /** 模拟拍摄：生成场景画面 */
    fun shoot(s: DemoScene) {
        analysis = null
        outcome = null
        fallback = null
        analyzingProgress = 0
        analyzing = false
        scene = s
        photoPath = DemoPhotoFactory.ensure(AppContextHolder.ctx, s)
        waterbody = s.waterbody
        note = ""
        phase = DetectPhase.SHOOTING
        tick++
        runAnalysis()
    }

    /** 不选场景，随机出一张示例照片（模拟真实快门） */
    fun shootRandom() {
        val path = DemoPhotoFactory.generateRandom(AppContextHolder.ctx)
        val s = DemoPhotoFactory.lastScene
        analysis = null
        outcome = null
        fallback = null
        analyzingProgress = 0
        analyzing = false
        scene = s
        photoPath = path
        waterbody = s.waterbody
        note = ""
        phase = DetectPhase.SHOOTING
        tick++
        runAnalysis()
    }

    /**
     * 执行识别。引擎由设置决定（自动 / 仅本机 / 仅云端），
     * 云端失败由 AutoClassifier 回退本机并给出原因。
     */
    fun runAnalysis() {
        val p = photoPath ?: return
        val s = scene ?: return
        if (analyzing) return
        analyzing = true
        phase = DetectPhase.ANALYZING
        analyzingProgress = 10
        tick++
        viewModelScope.launch {
            val r = runCatching {
                withContext(Dispatchers.IO) { AutoClassifier(s.prior).run(p) }
            }
            analyzingProgress = 100
            analyzing = false
            r.onSuccess {
                analysis = it.result
                fallback = FallbackNotice.of(it.fallback)
            }.onFailure {
                fallback = FallbackNotice.of(it.message)
            }
            phase = DetectPhase.DONE
            tick++
        }
    }

    fun submit() {
        val a = analysis ?: return
        val p = photoPath ?: return
        val s = scene
        val res = ReportFlow.submit(
            sceneId = s?.id,
            analysis = a,
            photoPath = p,
            waterbody = waterbody.ifBlank { s?.waterbody ?: "" },
            lat = DEMO_LAT,
            lng = DEMO_LNG,
            note = note,
            autoReport = autoReport,
            locationPrecision = locationPrecision
        )
        outcome = res
        phase = DetectPhase.SUBMITTED
        if (res is SubmitOutcome.Reported) res.ticket?.let { TicketService.checkUrge(it.id) }
        tick++
    }

    fun resetDetect() {
        phase = DetectPhase.IDLE
        scene = null
        photoPath = null
        analysis = null
        outcome = null
        fallback = null
        note = ""
        waterbody = ""
        analyzing = false
        analyzingProgress = 0
        tick++
    }

    fun openSubmitted() {
        when (val o = outcome) {
            is SubmitOutcome.Reported -> go(Screen.ReportResult(o.report.id))
            else -> Unit
        }
    }

    /** 识别历史（本机记录） */
    fun history(): List<Report> = user?.let { StatsService.myReports(it.id) } ?: emptyList()

    // ------------------------------------------------------------ 语言
    var language by mutableStateOf(Settings.currentLang())
        private set

    fun updateLanguage(tag: String) {
        Settings.setLanguage(tag)
        language = tag
        tick++
    }

    // ------------------------------------------------------------ AI 引擎设置
    var deepseekKey by mutableStateOf(Settings.apiKey)
        private set

    var gatewayUrl by mutableStateOf(Settings.gatewayUrl)
        private set

    var cloudModel by mutableStateOf(Settings.cloudModel)
        private set

    /** 当前凭据是否是网关令牌（`st:` 前缀） */
    val proxyMode: Boolean get() = deepseekKey.startsWith(Settings.PROXY_PREFIX)

    /** 网关地址是否已填 */
    val gatewayReady: Boolean get() = gatewayUrl.isNotBlank()

    /** 首次进入「我的」页时需要补全凭据（本次会话提醒过就不再弹） */
    var needsCredentialPrompt by mutableStateOf(!Settings.hasApiKey())
        private set

    fun dismissCredentialPrompt() {
        needsCredentialPrompt = false
        tick++
    }

    var mode by mutableStateOf(AnalysisMode.of(Settings.aiMode))
        private set

    /** 供 UI 直接显示的模式名 */
    val modeRes: Int
        @StringRes get() = mode.labelRes

    /** 当前是否具备联网识别条件（网关模式还必须填了网关地址） */
    val cloudReady: Boolean
        get() = Settings.hasApiKey() && (!proxyMode || gatewayReady)

    /** 引擎提示语：告诉用户当前真正会走哪条路 */
    val engineHintRes: Int
        @StringRes get() = when {
            mode == AnalysisMode.LOCAL -> R.string.hint_local_forced
            !cloudReady -> if (proxyMode && !gatewayReady) R.string.hint_no_gateway else R.string.hint_no_key
            else -> R.string.hint_cloud_ready
        }

    var keyTestState by mutableStateOf<KeyTestState>(KeyTestState.Idle)
        private set

    private val _messages = MutableSharedFlow<Int>(extraBufferCapacity = 8)
    val messages: SharedFlow<Int> = _messages

    fun saveDeepseekKey(key: String) {
        Settings.apiKey = key.trim()
        deepseekKey = Settings.apiKey
        keyTestState = KeyTestState.Idle
        if (deepseekKey.isNotBlank()) needsCredentialPrompt = false
        _messages.tryEmit(if (deepseekKey.isBlank()) R.string.api_key_cleared else R.string.api_key_saved)
        tick++
    }

    fun saveGateway(url: String) {
        Settings.gatewayUrl = url.trim()
        gatewayUrl = Settings.gatewayUrl
        tick++
    }

    fun saveCloudModel(m: String) {
        Settings.cloudModel = m
        cloudModel = Settings.cloudModel
        tick++
    }

    /** 生成新的设备令牌（换设备 / 疑似泄露时用，旧令牌在网关侧可撤销） */
    fun rotateToken() {
        saveDeepseekKey(Settings.rotateDeviceToken())
    }

    fun updateMode(m: AnalysisMode) {
        Settings.aiMode = m.key
        mode = m
        tick++
    }

    /** 校验凭据：直连查 /models，网关查 /v1/ai/health，都不消耗图像 token */
    fun testKey(key: String, gateway: String = gatewayUrl) {
        val k = key.trim()
        val g = gateway.trim().trimEnd('/')
        if (k.isBlank()) {
            keyTestState = KeyTestState.Failed("no_key")
            return
        }
        val proxy = k.startsWith(Settings.PROXY_PREFIX)
        if (proxy && g.isBlank()) {
            keyTestState = KeyTestState.Failed("no_gateway")
            return
        }
        keyTestState = KeyTestState.Running
        viewModelScope.launch {
            keyTestState = try {
                val info = withContext(Dispatchers.IO) {
                    if (proxy) {
                        CloudClassifier(
                            apiKey = k,
                            model = cloudModel,
                            baseUrl = g,
                            proxyMode = true
                        ).ping()
                    } else {
                        CloudClassifier(k).ping()
                    }
                }
                KeyTestState.Ok(info)
            } catch (e: CloudException) {
                KeyTestState.Failed(e.reason)
            } catch (e: Exception) {
                KeyTestState.Failed(e.message ?: e::class.java.simpleName)
            }
        }
    }

    // ------------------------------------------------------------ 地图 / 预约
    var agencyFilter by mutableStateOf<AgencyType?>(null)
    var selectedAgencyId by mutableStateOf<String?>("lab-001")
    var showReportsOnMap by mutableStateOf(true)

    fun agencies(): List<Pair<Agency, Int>> = StatsService.agencies(agencyFilter, DEMO_LAT, DEMO_LNG)

    fun agency(id: String): Agency? = DemoRepo.agencies.firstOrNull { it.id == id }

    var bookingAgencyId by mutableStateOf<String?>(null)
    var bookingDate by mutableStateOf("")
    var bookingTime by mutableStateOf("")
    var bookingTests by mutableStateOf(setOf<String>())
    var lastBooking by mutableStateOf<Booking?>(null)

    fun startBooking(agencyId: String) {
        bookingAgencyId = agencyId
        bookingDate = ""
        bookingTime = ""
        bookingTests = agency(agencyId)?.tests?.take(2)?.toSet() ?: emptySet()
        go(Screen.Booking)
    }

    fun confirmBooking() {
        val u = user ?: return
        val a = bookingAgencyId?.let { agency(it) } ?: return
        if (bookingDate.isBlank() || bookingTime.isBlank() || bookingTests.isEmpty()) return
        lastBooking = ReportFlow.book(
            userId = u.id,
            agencyId = a.id,
            date = bookingDate,
            time = bookingTime,
            tests = a.tests.filter { bookingTests.contains(it) },
            materials = a.materials
        )
        tick++
    }

    fun cancelBooking(id: String) {
        ReportFlow.cancelBooking(id)
        tick++
    }

    fun myBookings(): List<Booking> = DemoRepo.bookings
        .filter { it.userId == DemoRepo.currentUserId }
        .sortedByDescending { it.createdAt }

    // ------------------------------------------------------------ 排行
    var rankScope by mutableStateOf("city")
    var rankWindow by mutableStateOf(30)

    fun leaderboard(): List<LeaderRow> = ReportFlow.rank(rankScope, rankWindow)

    // ------------------------------------------------------------ 工单
    fun tickets(): List<Ticket> = DemoRepo.tickets
        .filter { it.reporterUserId == DemoRepo.currentUserId }
        .sortedByDescending { it.createdAt }

    fun allTickets(): List<Ticket> = DemoRepo.tickets.sortedByDescending { it.createdAt }

    fun ticket(id: String): Ticket? = DemoRepo.ticketOf(id)

    fun logs(id: String): List<TicketLog> = DemoRepo.logsOf(id)

    fun report(id: String): Report? = DemoRepo.reports.firstOrNull { it.id == id }

    fun myReports(): List<Report> = user?.let { StatsService.myReports(it.id) } ?: emptyList()

    fun stats() = StatsService.summary(user?.id)

    fun globalStats() = StatsService.summary(null)

    fun advanceTicket(id: String) {
        val t = DemoRepo.ticketOf(id) ?: return
        if (t.stage == TicketStage.TREATING && t.progress < 70) {
            TicketService.advanceTreatment(id)
        } else {
            TicketService.advance(id)
        }
        TicketService.checkUrge(id)
        tick++
    }

    fun disputeTicket(id: String, text: String) {
        TicketService.dispute(id, text)
        tick++
    }

    fun thankTicket(id: String) {
        TicketService.thank(id)
        tick++
    }

    fun manualSubmit(reportId: String) {
        outcome = ReportFlow.submitDraft(reportId)
        tick++
    }

    companion object {
        /** 演示定位：吉隆坡市政中心附近（真实版本来自 GPS + 逆地理编码） */
        const val DEMO_LAT = 3.1578
        const val DEMO_LNG = 101.7118
    }

    // ------------------------------------------------------------ 工单视觉复核
    /** 复核完成后的结论，null 表示还没复核 */
    var resolveResult by mutableStateOf<VerifyResult?>(null)
        private set

    /** 复核是否进行中 */
    var resolving by mutableStateOf(false)
        private set

    /**
     * 用本机引擎重新分析一张「洁净」照片，判断该工单是否真的处理干净。
     * 演示版不接摄像头，直接使用内置的干净水体样本。
     */
    fun resolveTicketByPhoto(ticketId: String, onDone: () -> Unit = {}) {
        if (resolving) return
        resolving = true
        resolveResult = null
        viewModelScope.launch {
            val scene = DemoScene.CLEAN
            val path = withContext(Dispatchers.IO) { DemoPhotoFactory.ensure(AppContextHolder.ctx, scene) }
            val r = runCatching { withContext(Dispatchers.IO) { AutoClassifier(scene.prior).run(path) } }
            r.onSuccess {
                val res = it.result
                val cleared = res.index < 30 && !res.isNonWater
                resolveResult = VerifyResult(
                    cleared = cleared,
                    index = res.index,
                    grade = res.level.code,
                    confidencePct = res.confidence.weight * 33,
                    engine = res.engine
                )
                if (cleared) TicketService.advanceTreatment(ticketId)
            }.onFailure {
                resolveResult = VerifyResult(cleared = false, index = -1, grade = "-", confidencePct = 0, engine = "local")
            }
            resolving = false
            onDone()
            tick++
        }
    }

    /** 示例照片里合成的水质实测值文案（演示用，非真实检测） */
    fun sampleText(key: String): String {
        val prefix = when (key) {
            "ph" -> "pH"
            "cod" -> "COD"
            "nh3n" -> "NH3-N"
            "tp" -> "TP"
            "do" -> "DO"
            else -> return ""
        }
        val v = DemoPhotoFactory.measuredSample(60).firstOrNull { it.first.startsWith(prefix) }?.second
        return if (v == null) "" else "$prefix $v"
    }

    /** 云端引擎显示名 */
    val cloudEngineName: String get() = Settings.MODEL

    /** 本机引擎显示名 */
    val localEngineName: String get() = "rule-engine-1.1.0"

    /** 关于页技术栈一行 */
    val engineLine: String get() = "SewageTrace 1.0 · " + Settings.MODEL + " / rule-engine-1.1.0"

}

package com.sewagetrace.app.ui

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Leaderboard
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.ui.graphics.vector.ImageVector
import com.sewagetrace.app.R

/** 四个主导航：识别污水 / 地图与预约 / 排行榜与反馈 / 我的 */
enum class Tab(@StringRes val labelRes: Int, val icon: ImageVector) {
    DETECT(R.string.tab_detect, Icons.Filled.PhotoCamera),
    MAP(R.string.tab_map, Icons.Filled.Map),
    RANK(R.string.tab_feedback, Icons.Filled.Leaderboard),
    PROFILE(R.string.tab_profile, Icons.Filled.Person)
}

/** 全局页面栈（演示版用一个 sealed class 表示路由） */
sealed class Screen {
    data object Auth : Screen()
    data class Home(val tab: Tab = Tab.DETECT) : Screen()
    data class ReportResult(val reportId: String) : Screen()
    data class TicketDetail(val ticketId: String) : Screen()
    data class AgencyDetail(val agencyId: String) : Screen()
    data object Booking : Screen()
    data object About : Screen()
    data object Standards : Screen()
    data object Achievements : Screen()
    data object ApiKey : Screen()
}

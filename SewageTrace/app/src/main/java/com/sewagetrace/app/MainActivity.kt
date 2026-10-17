package com.sewagetrace.app.ui

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sewagetrace.app.data.DemoRepo
import com.sewagetrace.app.data.Settings
import com.sewagetrace.app.ui.screens.AboutScreen
import com.sewagetrace.app.ui.screens.AgencyScreen
import com.sewagetrace.app.ui.screens.ApiKeyScreen
import com.sewagetrace.app.ui.screens.AuthScreen
import com.sewagetrace.app.ui.screens.BookingScreen
import com.sewagetrace.app.ui.screens.HomeShell
import com.sewagetrace.app.ui.screens.ReportResultScreen
import com.sewagetrace.app.ui.screens.StandardsScreen
import com.sewagetrace.app.ui.screens.TicketDetailScreen
import com.sewagetrace.app.ui.theme.SewageTraceTheme

/**
 * 继承 AppCompatActivity 是为了使用 AppCompatDelegate 的 per-app language
 * （Settings.setLanguage 依赖它做即时切换，无需重建进程）。
 */
class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        DemoRepo.load(this)
        DemoRepo.restoreSession()
        Settings.applySavedLanguage()
        setContent {
            SewageTraceTheme {
                AppRoot()
            }
        }
    }
}

@Composable
fun AppRoot(vm: AppViewModel = viewModel()) {
    var ready by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (DemoRepo.currentUserId != null && vm.screen is Screen.Auth) {
            vm.home(Tab.DETECT)
        }
        ready = true
    }
    if (!ready) return

    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        when (val s = vm.screen) {
            is Screen.Auth -> AuthScreen(vm)
            is Screen.Home -> HomeShell(vm, s.tab)
            is Screen.ReportResult -> ReportResultScreen(vm, s.reportId)
            is Screen.TicketDetail -> TicketDetailScreen(vm, s.ticketId)
            is Screen.AgencyDetail -> AgencyScreen(vm, s.agencyId)
            is Screen.Booking -> BookingScreen(vm)
            is Screen.About -> AboutScreen(vm)
            is Screen.Standards -> StandardsScreen(vm)
            is Screen.Achievements -> ReportResultScreen(vm, "")
            is Screen.ApiKey -> ApiKeyScreen(vm) { vm.back() }
        }
    }

    androidx.activity.compose.BackHandler(enabled = vm.stack.isNotEmpty()) { vm.back() }
}

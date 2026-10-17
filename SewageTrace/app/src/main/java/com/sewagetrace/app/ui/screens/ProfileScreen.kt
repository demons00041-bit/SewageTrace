package com.sewagetrace.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sewagetrace.app.R
import com.sewagetrace.app.data.AnalysisMode
import com.sewagetrace.app.data.DemoRepo
import com.sewagetrace.app.model.BookingStatus
import com.sewagetrace.app.ui.AppViewModel
import com.sewagetrace.app.ui.Screen
import com.sewagetrace.app.ui.components.CardBox
import com.sewagetrace.app.ui.components.Divider2
import com.sewagetrace.app.ui.components.SectionTitle
import com.sewagetrace.app.ui.components.SeverityChip
import com.sewagetrace.app.ui.components.StatPill
import com.sewagetrace.app.ui.theme.Water300
import com.sewagetrace.app.ui.theme.Water700

@Composable
fun ProfileScreen(vm: AppViewModel) {
    vm.tick
    val u = vm.user ?: return
    val stats = vm.stats()
    val myReports = vm.myReports()

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
        Spacer(Modifier.height(18.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(56.dp).clip(RoundedCornerShape(18.dp))
                    .background(Water700.copy(alpha = 0.22f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    u.nickname.take(1), fontSize = 24.sp, color = Water300, fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(u.nickname, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text(
                    "${u.city} · ${u.phone}",
                    fontSize = 10.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (u.realName) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(5.dp).clip(CircleShape).background(Color(0xFF34e399)))
                        Spacer(Modifier.width(4.dp))
                        Text(
                            stringResource(R.string.realname_demo), fontSize = 9.5.sp,
                            color = Color(0xFF34e399)
                        )
                    }
                }
            }
            Box(
                Modifier.size(34.dp).clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                    .clickable { vm.go(Screen.About) },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.Settings, null, Modifier.size(17.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (vm.needsCredentialPrompt) {
            Spacer(Modifier.height(14.dp))
            CredentialPromptCard(vm)
        }

        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            StatPill(stringResource(R.string.stat_valid), "${stats.valid}", Water300, Modifier.weight(1f))
            StatPill(stringResource(R.string.stat_open), "${stats.inProgress}", Color(0xFFF59E0B), Modifier.weight(1f))
            StatPill(stringResource(R.string.stat_closed), "${stats.closed}", Color(0xFF22C55E), Modifier.weight(1f))
            StatPill(stringResource(R.string.stat_avg), "${stats.avgProgress}%", MaterialTheme.colorScheme.primary, Modifier.weight(1f))
        }

        Spacer(Modifier.height(16.dp))
        SectionTitle(stringResource(R.string.my_reports), stringResource(R.string.entry_count, myReports.size))
        Spacer(Modifier.height(8.dp))
        if (myReports.isEmpty()) {
            CardBox {
                Column(Modifier.padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(stringResource(R.string.no_reports), fontSize = 13.sp)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        stringResource(R.string.no_reports_sub), fontSize = 10.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            myReports.forEach { r ->
                CardBox {
                    Row(
                        Modifier.fillMaxWidth().clickable { vm.go(Screen.ReportResult(r.id)) }.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            Modifier.size(44.dp).clip(RoundedCornerShape(12.dp))
                                .background(r.level.color.copy(alpha = 0.16f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "${r.index}", fontSize = 15.sp,
                                fontWeight = FontWeight.Bold, color = r.level.color
                            )
                        }
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(r.waterbodyName, fontSize = 12.5.sp, fontWeight = FontWeight.Medium)
                                Spacer(Modifier.width(6.dp))
                                SeverityChip(r.level, small = true)
                            }
                            val ctx = androidx.compose.ui.platform.LocalContext.current
                            Text(
                                DemoRepo.fmtTime(r.createdAt) + " · " + stringResource(r.status.labelRes) +
                                    (r.ticketId?.let { " · $it" } ?: ""),
                                fontSize = 9.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text("›", fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }

        Spacer(Modifier.height(10.dp))
        SectionTitle(stringResource(R.string.my_bookings))
        Spacer(Modifier.height(8.dp))
        val bookings = DemoRepo.bookings.filter { it.userId == u.id }
        if (bookings.isEmpty()) {
            CardBox {
                Text(
                    stringResource(R.string.no_bookings),
                    Modifier.padding(16.dp), fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            bookings.forEach { b ->
                CardBox {
                    Column(Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                b.agencyName, fontSize = 12.5.sp, fontWeight = FontWeight.Medium,
                                modifier = Modifier.weight(1f)
                            )
                            val active = b.status == BookingStatus.BOOKED
                            Box(
                                Modifier.clip(RoundedCornerShape(6.dp))
                                    .background(
                                        if (active) Color(0xFF34e399).copy(alpha = 0.16f)
                                        else MaterialTheme.colorScheme.surfaceVariant
                                    )
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    stringResource(b.status.labelRes), fontSize = 9.sp,
                                    color = if (active) Color(0xFF34e399)
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "${b.slotDate} ${b.slotTime} · ${b.tests.joinToString(", ")}",
                            fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            stringResource(R.string.booking_code_line, b.code),
                            fontSize = 9.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (b.status == BookingStatus.BOOKED) {
                            Spacer(Modifier.height(6.dp))
                            Row {
                                Text(
                                    stringResource(R.string.cancel_booking),
                                    Modifier.clickable { vm.cancelBooking(b.id) },
                                    fontSize = 10.5.sp, color = MaterialTheme.colorScheme.error
                                )
                                Spacer(Modifier.width(16.dp))
                                Text(stringResource(R.string.reschedule), fontSize = 10.5.sp, color = Water300)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }

        // ── 设置 ──
        Spacer(Modifier.height(12.dp))
        SectionTitle(stringResource(R.string.settings))
        Spacer(Modifier.height(8.dp))
        CardBox {
            Column(Modifier.padding(14.dp)) {
                SettingRow(
                    stringResource(R.string.setting_auto_report),
                    stringResource(R.string.setting_auto_report_desc),
                    vm.autoReport
                ) { vm.updateAutoReport(it) }
                Divider2()
                SettingRow(
                    stringResource(R.string.setting_anon),
                    stringResource(R.string.setting_anon_desc),
                    u.anonymous
                ) { vm.updateProfile(u.nickname, u.city, it) }
                Divider2()
                Spacer(Modifier.height(10.dp))

                // 语言
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.Language, null, Modifier.size(15.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.setting_language), fontSize = 13.sp)
                }
                Row(
                    Modifier.padding(top = 8.dp, bottom = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf("en" to "English", "zh" to "中文").forEach { (code, label) ->
                        SelectPill(label, vm.language == code) { vm.updateLanguage(code) }
                    }
                }
                Divider2()
                Spacer(Modifier.height(10.dp))

                // AI 引擎模式
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.Memory, null, Modifier.size(15.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.setting_engine), fontSize = 13.sp)
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    stringResource(R.string.setting_engine_desc),
                    fontSize = 9.5.sp, lineHeight = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    Modifier.padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(
                        AnalysisMode.AUTO to stringResource(R.string.mode_auto),
                        AnalysisMode.CLOUD to stringResource(R.string.mode_cloud),
                        AnalysisMode.LOCAL to stringResource(R.string.mode_local)
                    ).forEach { (m, label) ->
                        SelectPill(label, vm.mode == m) { vm.updateMode(m) }
                    }
                }
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(6.dp).clip(CircleShape)
                            .background(if (vm.cloudReady) Color(0xFF22C55E) else Color(0xFFF59E0B))
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        stringResource(
                            if (vm.cloudReady) R.string.key_configured else R.string.key_missing
                        ),
                        fontSize = 9.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Divider2()
                Spacer(Modifier.height(10.dp))

                Text(stringResource(R.string.loc_precision_with, vm.locationPrecision), fontSize = 12.sp)
                Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(
                        1000 to stringResource(R.string.prec_1km_short),
                        500 to stringResource(R.string.prec_500m_short),
                        50 to stringResource(R.string.prec_50m_short)
                    ).forEach { (v, label) ->
                        SelectPill(label, vm.locationPrecision == v) { vm.updateLocationPrecision(v) }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.loc_precision_note),
                    fontSize = 9.5.sp, lineHeight = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(Modifier.height(10.dp))
        CardBox {
            Column(Modifier.padding(14.dp)) {
                MenuRow(
                    Icons.Filled.Verified,
                    stringResource(R.string.menu_standards),
                    stringResource(R.string.menu_standards_sub)
                ) { vm.go(Screen.Standards) }
                Divider2()
                MenuRow(
                    Icons.Filled.VpnKey,
                    stringResource(R.string.menu_api_key),
                    if (vm.cloudReady) stringResource(R.string.menu_api_key_ok)
                    else stringResource(R.string.menu_api_key_none)
                ) { vm.go(Screen.ApiKey) }
                Divider2()
                MenuRow(
                    Icons.Filled.Shield,
                    stringResource(R.string.menu_privacy),
                    stringResource(R.string.menu_privacy_sub)
                ) { vm.go(Screen.About) }
                Divider2()
                MenuRow(
                    Icons.Filled.Settings,
                    stringResource(R.string.menu_about),
                    stringResource(R.string.menu_about_sub)
                ) { vm.go(Screen.About) }
            }
        }

        Spacer(Modifier.height(16.dp))
        OutlinedButton(
            onClick = { vm.logout() },
            modifier = Modifier.fillMaxWidth().height(46.dp),
            shape = RoundedCornerShape(12.dp)
        ) { Text(stringResource(R.string.logout), color = MaterialTheme.colorScheme.error) }
        Spacer(Modifier.height(10.dp))
        Text(
            stringResource(R.string.footer_demo),
            Modifier.fillMaxWidth(), fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun SelectPill(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(9.dp))
            .background(
                if (selected) Water700.copy(alpha = 0.24f)
                else MaterialTheme.colorScheme.surfaceVariant
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 11.dp, vertical = 6.dp)
    ) {
        Text(
            label, fontSize = 10.5.sp,
            color = if (selected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}

@Composable
private fun CredentialPromptCard(vm: AppViewModel) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFFF59E0B).copy(alpha = 0.14f))
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Filled.Warning, null, Modifier.size(16.dp),
                tint = Color(0xFFF59E0B)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                stringResource(R.string.cred_prompt_title),
                fontSize = 13.sp, fontWeight = FontWeight.Bold,
                color = Color(0xFFF59E0B)
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            stringResource(R.string.cred_prompt_body),
            fontSize = 11.sp, lineHeight = 16.5.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { vm.go(Screen.ApiKey) },
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) { Text(stringResource(R.string.cred_prompt_now), fontSize = 12.sp) }
            OutlinedButton(
                onClick = { vm.dismissCredentialPrompt() },
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) { Text(stringResource(R.string.cred_prompt_later), fontSize = 12.sp) }
        }
    }
}

@Composable
private fun SettingRow(title: String, desc: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 13.sp)
            Text(
                desc, fontSize = 9.5.sp, lineHeight = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun MenuRow(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, Modifier.size(17.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 13.sp)
            Text(subtitle, fontSize = 9.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text("›", fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

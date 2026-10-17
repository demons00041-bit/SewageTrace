package com.sewagetrace.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sewagetrace.app.R
import com.sewagetrace.app.model.AgencyType
import com.sewagetrace.app.ui.AppViewModel
import com.sewagetrace.app.ui.Tab
import com.sewagetrace.app.ui.components.CardBox
import com.sewagetrace.app.ui.components.Divider2
import com.sewagetrace.app.ui.components.KV
import com.sewagetrace.app.ui.components.SectionTitle
import com.sewagetrace.app.ui.theme.Water700

/** 机构详情：政府机关 / 污水厂 / 监测站 / 可送检实验室 */
@Composable
fun AgencyScreen(vm: AppViewModel, id: String) {
    vm.tick
    val a = vm.agency(id) ?: return
    val dist = vm.agencies().firstOrNull { it.first.id == id }?.second ?: 0

    Column(Modifier.fillMaxSize()) {
        TopBar(a.name) { vm.back() }
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            Spacer(Modifier.height(4.dp))
            CardBox {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(46.dp).clip(RoundedCornerShape(14.dp))
                                .background(Water700.copy(alpha = 0.18f)),
                            contentAlignment = Alignment.Center
                        ) { Text(a.type.icon, fontSize = 22.sp) }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(a.name, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                            Text(
                                a.localName,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Divider2()
                    Spacer(Modifier.height(10.dp))
                    KV(stringResource(R.string.agency_address), a.address)
                    KV(
                        stringResource(R.string.agency_distance),
                        if (dist >= 1000) "%.1f km".format(dist / 1000.0) else "$dist m"
                    )
                    KV(stringResource(R.string.agency_phone), a.phone ?: "—")
                    KV(stringResource(R.string.agency_hours), a.hours)
                    if (a.standardRes != null) {
                        KV(stringResource(R.string.agency_standard), stringResource(a.standardRes))
                    }
                    Spacer(Modifier.height(8.dp))
                    Box(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFFF59E0B).copy(alpha = 0.12f)).padding(10.dp)
                    ) {
                        Text(
                            stringResource(R.string.agency_demo_data),
                            fontSize = 10.5.sp, lineHeight = 16.sp, color = Color(0xFFF59E0B)
                        )
                    }
                }
            }

            if (a.type == AgencyType.LAB) {
                Spacer(Modifier.height(14.dp))
                SectionTitle(stringResource(R.string.agency_booking_title))
                Spacer(Modifier.height(8.dp))
                CardBox {
                    Column(Modifier.padding(16.dp)) {
                        if (a.accredited) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(6.dp).clip(CircleShape).background(Color(0xFF34D399)))
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    stringResource(R.string.agency_accredited),
                                    fontSize = 11.5.sp, color = Color(0xFF34D399)
                                )
                            }
                            Spacer(Modifier.height(10.dp))
                        }
                        Text(
                            stringResource(R.string.agency_tests),
                            fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.height(6.dp))
                        FlowChips(a.tests) { it }
                        Spacer(Modifier.height(12.dp))
                        KV(stringResource(R.string.agency_fee), a.fee ?: stringResource(R.string.agency_quote))
                        KV(
                            stringResource(R.string.agency_materials),
                            a.materials.joinToString(", ").ifBlank { "—" }
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            stringResource(R.string.agency_booking_note),
                            fontSize = 10.sp, lineHeight = 15.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(12.dp))
                        Button(
                            onClick = { vm.startBooking(a.id) },
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Water700)
                        ) {
                            Text(
                                stringResource(R.string.agency_book_slot),
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            } else {
                Spacer(Modifier.height(14.dp))
                CardBox(container = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)) {
                    Text(
                        stringResource(R.string.agency_gov_channel),
                        Modifier.padding(14.dp), fontSize = 10.5.sp, lineHeight = 16.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun TopBar(title: String, onBack: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.back),
                tint = MaterialTheme.colorScheme.onBackground
            )
        }
        Text(title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun FlowChips(items: List<String>, transform: (String) -> String) {
    Column {
        items.chunked(2).forEach { line ->
            Row(
                Modifier.fillMaxWidth().padding(bottom = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                line.forEach { t ->
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
                            .padding(horizontal = 9.dp, vertical = 5.dp)
                    ) { Text(transform(t), fontSize = 10.5.sp) }
                }
            }
        }
    }
}

@Composable
fun BookingScreen(vm: AppViewModel) {
    vm.tick
    val agency = vm.bookingAgencyId?.let { vm.agency(it) } ?: return
    val dates = listOf(
        stringResource(R.string.day_today),
        stringResource(R.string.day_tomorrow),
        stringResource(R.string.day_day_after),
        stringResource(R.string.day_next_mon)
    )
    val slots = listOf("09:00-10:00", "10:00-11:00", "14:00-15:00", "15:00-16:00")

    Column(Modifier.fillMaxSize()) {
        TopBar(stringResource(R.string.booking_title)) { vm.back() }
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)
        ) {
            val b = vm.lastBooking
            if (b != null) {
                CardBox(container = Color(0xFF22C55E).copy(alpha = 0.10f)) {
                    Column(Modifier.padding(18.dp)) {
                        Text(
                            stringResource(R.string.booking_success),
                            fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFF22C55E)
                        )
                        Spacer(Modifier.height(8.dp))
                        KV(stringResource(R.string.booking_code), b.code)
                        KV(stringResource(R.string.booking_agency), b.agencyName)
                        KV(stringResource(R.string.booking_slot), b.slotDate + " " + b.slotTime)
                        KV(stringResource(R.string.booking_tests), b.tests.joinToString(", "))
                        KV(stringResource(R.string.booking_materials), b.materials.joinToString(", ").ifBlank { "—" })
                        Spacer(Modifier.height(10.dp))
                        Text(
                            stringResource(R.string.booking_reminder),
                            fontSize = 10.5.sp, lineHeight = 16.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(12.dp))
                        Button(
                            onClick = { vm.home(Tab.MAP) },
                            modifier = Modifier.fillMaxWidth().height(46.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Water700)
                        ) { Text(stringResource(R.string.done)) }
                    }
                }
                Spacer(Modifier.height(24.dp))
                return@Column
            }

            Text(agency.name, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Text(
                "${agency.address} · ${agency.hours}",
                fontSize = 10.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(16.dp))

            SectionTitle(stringResource(R.string.booking_pick_date))
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                dates.forEach { d ->
                    Chip(d, vm.bookingDate == d, Modifier.weight(1f)) { vm.bookingDate = d }
                }
            }

            Spacer(Modifier.height(16.dp))
            SectionTitle(stringResource(R.string.booking_pick_slot))
            Spacer(Modifier.height(8.dp))
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                slots.chunked(2).forEach { line ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        line.forEach { s ->
                            Chip(s, vm.bookingTime == s, Modifier.weight(1f)) { vm.bookingTime = s }
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            SectionTitle(stringResource(R.string.booking_pick_tests), stringResource(R.string.booking_multi))
            Spacer(Modifier.height(8.dp))
            agency.tests.chunked(2).forEach { line ->
                Row(
                    Modifier.fillMaxWidth().padding(bottom = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    line.forEach { t ->
                        Chip(t, vm.bookingTests.contains(t), Modifier.weight(1f)) {
                            vm.bookingTests =
                                if (vm.bookingTests.contains(t)) vm.bookingTests - t else vm.bookingTests + t
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            CardBox(container = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)) {
                Column(Modifier.padding(12.dp)) {
                    KV(stringResource(R.string.agency_fee), agency.fee ?: stringResource(R.string.agency_quote))
                    KV(
                        stringResource(R.string.agency_materials),
                        agency.materials.joinToString(", ").ifBlank { "—" }
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
            Button(
                onClick = { vm.confirmBooking() },
                enabled = vm.bookingDate.isNotBlank() && vm.bookingTime.isNotBlank() && vm.bookingTests.isNotEmpty(),
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Water700)
            ) {
                Text(stringResource(R.string.booking_confirm), fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun Chip(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .clip(RoundedCornerShape(10.dp))
            .background(
                if (selected) Water700.copy(alpha = 0.24f)
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
            )
            .border(1.dp, if (selected) Water700 else Color.Transparent, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 9.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label, fontSize = 11.sp,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}

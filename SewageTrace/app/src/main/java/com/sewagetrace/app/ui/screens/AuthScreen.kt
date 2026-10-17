package com.sewagetrace.app.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sewagetrace.app.R
import com.sewagetrace.app.data.DemoRepo
import com.sewagetrace.app.ui.AppViewModel
import com.sewagetrace.app.ui.components.CardBox
import com.sewagetrace.app.ui.components.Divider2
import com.sewagetrace.app.ui.theme.Water300
import com.sewagetrace.app.ui.theme.Water700

/**
 * 登录 / 注册。
 * 演示版用本地账号；真实版本对应 Firebase Auth（手机号验证码 + 邮箱）。
 * 无论哪种方式，登录与注册都必须各自勾选隐私政策与位置信息授权（可分项撤回）。
 */
@Composable
fun AuthScreen(vm: AppViewModel) {
    var mode by remember { mutableStateOf(0) } // 0 登录 1 注册
    var phone by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var nickname by remember { mutableStateOf("") }
    var city by remember { mutableStateOf("Kuala Lumpur") }
    var agree by remember { mutableStateOf(false) }
    var errorRes by remember { mutableStateOf<Int?>(null) }

    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF06232B), Color(0xFF0B1418), Color(0xFF0B1418))
                )
            )
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(64.dp))
            Box(
                Modifier
                    .size(74.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(Brush.linearGradient(listOf(Water700, Water300))),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.WaterDrop, null,
                    tint = Color.White, modifier = Modifier.size(40.dp)
                )
            }
            Spacer(Modifier.height(18.dp))
            Text(
                stringResource(R.string.app_title),
                fontSize = 27.sp, fontWeight = FontWeight.Bold, color = Color.White
            )
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(R.string.app_tagline),
                fontSize = 12.5.sp, color = Color(0xFF9FB8C2), textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(30.dp))

            CardBox(container = Color(0xFF11212A)) {
                Column(Modifier.padding(18.dp)) {
                    TabRow(
                        selectedTabIndex = mode,
                        containerColor = Color.Transparent,
                        contentColor = Water300
                    ) {
                        Tab(selected = mode == 0, onClick = { mode = 0; errorRes = null }) {
                            Text(
                                stringResource(R.string.sign_in),
                                Modifier.padding(vertical = 12.dp), fontWeight = FontWeight.SemiBold
                            )
                        }
                        Tab(selected = mode == 1, onClick = { mode = 1; errorRes = null }) {
                            Text(
                                stringResource(R.string.sign_up),
                                Modifier.padding(vertical = 12.dp), fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                    Spacer(Modifier.height(18.dp))
                    OutlinedTextField(
                        value = phone,
                        onValueChange = { v ->
                            phone = v.filter { c -> c.isDigit() }.take(11); errorRes = null
                        },
                        label = { Text(stringResource(R.string.phone)) },
                        placeholder = { Text(stringResource(R.string.phone_hint)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone)
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = password,
                        onValueChange = { v -> password = v; errorRes = null },
                        label = { Text(stringResource(R.string.password)) },
                        placeholder = { Text(stringResource(R.string.password_hint)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (mode == 1) {
                        Spacer(Modifier.height(10.dp))
                        OutlinedTextField(
                            value = nickname,
                            onValueChange = { v -> nickname = v.take(16); errorRes = null },
                            label = { Text(stringResource(R.string.display_name)) },
                            placeholder = { Text(stringResource(R.string.nickname_hint)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(10.dp))
                        OutlinedTextField(
                            value = city,
                            onValueChange = { v -> city = v.take(24) },
                            label = { Text(stringResource(R.string.city_hint)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    Spacer(Modifier.height(14.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = agree, onCheckedChange = { agree = it })
                        Column {
                            Text(
                                stringResource(R.string.agree_terms),
                                fontSize = 12.sp, color = Color(0xFFCFE3E9)
                            )
                            Text(
                                stringResource(R.string.auth_agree_sub),
                                fontSize = 10.5.sp, color = Color(0xFF7E99A4)
                            )
                        }
                    }

                    errorRes?.let { res ->
                        Spacer(Modifier.height(8.dp))
                        Text(
                            stringResource(res),
                            color = MaterialTheme.colorScheme.error, fontSize = 12.sp
                        )
                    }

                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = {
                            when {
                                phone.length < 10 -> errorRes = R.string.err_phone_length
                                password.isBlank() -> errorRes = R.string.err_no_password
                                mode == 1 && nickname.isBlank() -> errorRes = R.string.err_no_nickname
                                !agree -> errorRes = R.string.must_agree
                                mode == 0 -> {
                                    val u = DemoRepo.users.firstOrNull { it.phone == phone }
                                        ?: DemoRepo.users.first()
                                    vm.login(u.id)
                                }
                                else -> {
                                    vm.register(
                                        nickname,
                                        phone.take(2) + "****" + phone.takeLast(3),
                                        city.ifBlank { "Kuala Lumpur" }
                                    )
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Water700)
                    ) {
                        Text(
                            stringResource(
                                if (mode == 0) R.string.sign_in else R.string.register_and_login
                            ),
                            fontSize = 15.sp, fontWeight = FontWeight.SemiBold
                        )
                    }

                    Spacer(Modifier.height(10.dp))
                    TextButton(
                        onClick = { vm.login("u-demo") },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            stringResource(R.string.demo_login),
                            fontSize = 12.5.sp, color = Water300, textAlign = TextAlign.Center
                        )
                    }
                }
            }

            Spacer(Modifier.height(26.dp))
            CardBox(container = Color(0x14FFFFFF)) {
                Column(Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(6.dp).clip(CircleShape).background(Water300))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            stringResource(R.string.about_app),
                            fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                            color = Color(0xFFCFE3E9)
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        stringResource(R.string.disclaimer_law),
                        fontSize = 10.5.sp, lineHeight = 16.sp, color = Color(0xFF8FA9B4)
                    )
                    Spacer(Modifier.height(8.dp))
                    Divider2()
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.demo_note),
                        fontSize = 10.5.sp, lineHeight = 15.sp, color = Color(0xFF6E8794)
                    )
                }
            }
            Spacer(Modifier.height(30.dp))
        }
    }
}

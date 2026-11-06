package com.sewagetrace.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sewagetrace.app.R
import com.sewagetrace.app.ui.AppViewModel
import com.sewagetrace.app.ui.components.CardBox
import com.sewagetrace.app.ui.components.Divider2
import com.sewagetrace.app.ui.theme.Water300
import com.sewagetrace.app.ui.theme.Water700

/** 学校邮箱域名：注册只接受这个后缀 */
private const val UM_DOMAIN = "@siswa.um.edu.my"

/**
 * 登录 / 注册。
 *
 * 登录：只能用马来亚大学（UM）的 Google 账号 —— 即 @siswa.um.edu.my 域。
 * 注册：必须填学校发放的学号邮箱 学号@siswa.um.edu.my。
 *
 * 演示版不接真实 Google OAuth：点「使用 Google 账号登录」会弹出账号选择框，
 * 选中的账号会走与真实流程相同的域校验与建档逻辑。
 */
@Composable
fun AuthScreen(vm: AppViewModel) {
    var mode by remember { mutableStateOf(0) } // 0 登录 1 注册
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var nickname by remember { mutableStateOf("") }
    var city by remember { mutableStateOf("Kuala Lumpur") }
    var agree by remember { mutableStateOf(false) }
    var errorRes by remember { mutableStateOf<Int?>(null) }
    var showGoogleDialog by remember { mutableStateOf(false) }

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

            Spacer(Modifier.height(10.dp))
            // 仅限马大的标识
            Box(
                Modifier
                    .clip(RoundedCornerShape(99.dp))
                    .background(Water300.copy(alpha = 0.14f))
                    .border(1.dp, Water300.copy(alpha = 0.35f), RoundedCornerShape(99.dp))
                    .padding(horizontal = 12.dp, vertical = 5.dp)
            ) {
                Text(
                    stringResource(R.string.auth_um_only),
                    fontSize = 10.5.sp, color = Water300, fontWeight = FontWeight.Medium
                )
            }

            Spacer(Modifier.height(22.dp))

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

                    if (mode == 0) {
                        // ---------------- 登录：只允许学校 Google 账号 ----------------
                        Text(
                            stringResource(R.string.auth_google_sub),
                            fontSize = 12.sp, lineHeight = 18.sp, color = Color(0xFF9FB8C2)
                        )
                        Spacer(Modifier.height(14.dp))
                        Button(
                            onClick = { showGoogleDialog = true },
                            modifier = Modifier.fillMaxWidth().height(50.dp),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.White,
                                contentColor = Color(0xFF1F2937)
                            )
                        ) {
                            GoogleMark()
                            Spacer(Modifier.width(10.dp))
                            Text(
                                stringResource(R.string.auth_google_signin),
                                fontSize = 15.sp, fontWeight = FontWeight.SemiBold
                            )
                        }

                        errorRes?.let { res ->
                            Spacer(Modifier.height(10.dp))
                            Text(
                                stringResource(res),
                                color = MaterialTheme.colorScheme.error, fontSize = 12.sp
                            )
                        }

                        Spacer(Modifier.height(14.dp))
                        Divider2()
                        Spacer(Modifier.height(12.dp))
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
                        Spacer(Modifier.height(6.dp))
                        TextButton(
                            onClick = { mode = 1; errorRes = null },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                stringResource(R.string.auth_switch_to_signup),
                                fontSize = 12.sp, color = Water300
                            )
                        }
                    } else {
                        // ---------------- 注册：必须用学号校邮箱 ----------------
                        Text(
                            stringResource(R.string.auth_signup_note),
                            fontSize = 11.sp, lineHeight = 17.sp, color = Color(0xFF9FB8C2)
                        )
                        Spacer(Modifier.height(12.dp))
                        OutlinedTextField(
                            value = email,
                            onValueChange = { v -> email = v.trim().take(60); errorRes = null },
                            label = { Text(stringResource(R.string.auth_email_label)) },
                            placeholder = { Text(stringResource(R.string.auth_email_hint)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
                        )
                        Spacer(Modifier.height(10.dp))
                        OutlinedTextField(
                            value = password,
                            onValueChange = { v -> password = v; errorRes = null },
                            label = { Text(stringResource(R.string.password)) },
                            placeholder = { Text(stringResource(R.string.password_hint)) },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            modifier = Modifier.fillMaxWidth()
                        )
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
                                    !email.contains('@') -> errorRes = R.string.err_email_format
                                    !email.endsWith(UM_DOMAIN, ignoreCase = true) ->
                                        errorRes = R.string.err_email_domain
                                    email.substringBefore('@').isBlank() ->
                                        errorRes = R.string.err_email_format
                                    password.isBlank() -> errorRes = R.string.err_no_password
                                    nickname.isBlank() -> errorRes = R.string.err_no_nickname
                                    !agree -> errorRes = R.string.must_agree
                                    com.sewagetrace.app.data.DemoRepo.findByEmail(email) != null ->
                                        errorRes = R.string.err_email_exists
                                    else -> vm.register(
                                        nickname, email, city.ifBlank { "Kuala Lumpur" }
                                    )
                                }
                            },
                            modifier = Modifier.fillMaxWidth().height(50.dp),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Water700)
                        ) {
                            Text(
                                stringResource(R.string.register_and_login),
                                fontSize = 15.sp, fontWeight = FontWeight.SemiBold
                            )
                        }

                        Spacer(Modifier.height(6.dp))
                        TextButton(
                            onClick = { mode = 0; errorRes = null },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                stringResource(R.string.auth_switch_to_signin),
                                fontSize = 12.sp, color = Water300
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            // 演示账号：仍然走 Google 那条路，不绕过域校验
            TextButton(onClick = { vm.login("u-demo") }) {
                Text(
                    stringResource(R.string.demo_login),
                    fontSize = 12.5.sp, color = Water300, textAlign = TextAlign.Center
                )
            }

            Spacer(Modifier.height(14.dp))
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

    if (showGoogleDialog) {
        val demoEmail = stringResource(R.string.auth_google_demo_account)
        val demoName = stringResource(R.string.auth_google_demo_name)
        AlertDialog(
            onDismissRequest = { showGoogleDialog = false },
            title = { Text(stringResource(R.string.auth_google_dialog_title), fontSize = 16.sp) },
            text = {
                Column {
                    Text(
                        stringResource(R.string.auth_google_dialog_sub),
                        fontSize = 12.sp, lineHeight = 18.sp
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(
                        Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                            .clickable {
                                showGoogleDialog = false
                                if (!agree) {
                                    errorRes = R.string.must_agree
                                } else {
                                    vm.signInWithSchoolEmail(demoEmail, demoName)
                                }
                            }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            Modifier.size(34.dp).clip(CircleShape)
                                .background(Water700.copy(alpha = 0.25f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                demoName.take(1), fontSize = 15.sp,
                                fontWeight = FontWeight.Bold, color = Water300
                            )
                        }
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(demoName, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                            Text(
                                demoEmail, fontSize = 10.5.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showGoogleDialog = false }) {
                    Text(stringResource(R.string.cancel), fontSize = 13.sp)
                }
            }
        )
    }
}

/** 用几个色块拼一个 Google 风格的四色标记，避免引入图片资源 */
@Composable
private fun GoogleMark() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(18.dp).clip(CircleShape)
                .background(Brush.sweepGradient(
                    listOf(
                        Color(0xFF4285F4), Color(0xFF34A853),
                        Color(0xFFFBBC05), Color(0xFFEA4335), Color(0xFF4285F4)
                    )
                ))
        )
    }
}

package com.sewagetrace.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sewagetrace.app.R
import com.sewagetrace.app.data.Settings
import com.sewagetrace.app.ui.AppViewModel
import com.sewagetrace.app.ui.KeyTestState
import com.sewagetrace.app.ui.components.SectionCard

/**
 * 云端凭据配置页（每次打开 App 首次进入「我的」页时会主动提示）。
 *
 * 两种模式：
 *  - **网关令牌**（推荐，`st:` 开头）：APK 里不存在任何第三方密钥，只持有本应用自签的设备令牌，
 *    真正的模型密钥留在服务端反代网关。泄露令牌只能耗尽配额，无法盗用密钥或账单。
 *  - **直连密钥**：直接用原厂 key，仅供本地调试，UI 上会显示红色风险警示。
 *
 * 存储：[Settings.apiKey] 走 Android Keystore AES-GCM 加密，磁盘上不是明文。
 * 不上传任何服务端，不写日志。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApiKeyScreen(
    vm: AppViewModel,
    onBack: () -> Unit
) {
    val savedKey = vm.deepseekKey
    var keyInput by remember(savedKey) { mutableStateOf(savedKey) }
    var gatewayInput by remember(vm.gatewayUrl) { mutableStateOf(vm.gatewayUrl) }
    var modelInput by remember(vm.cloudModel) { mutableStateOf(vm.cloudModel) }
    var visible by remember { mutableStateOf(false) }
    val test = vm.keyTestState
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current

    // 输入内容决定模式，而不是已保存的内容——用户可能正在把直连切换成网关
    val inputIsProxy = keyInput.trim().startsWith(Settings.PROXY_PREFIX)
    val dirty = keyInput.trim() != savedKey ||
        gatewayInput.trim().trimEnd('/') != vm.gatewayUrl ||
        modelInput.trim() != vm.cloudModel

    LaunchedEffect(Unit) {
        vm.messages.collect { msg -> snackbar.showSnackbar(context.getString(msg)) }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_api_key)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                }
            )
        }
    ) { pad ->
        Column(
            Modifier
                .padding(pad)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ---------------------------------------------------------- 说明
            SectionCard(title = stringResource(R.string.api_key_title)) {
                Text(
                    stringResource(R.string.api_key_body),
                    fontSize = 12.5.sp,
                    lineHeight = 19.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // ---------------------------------------------------------- 模式提示
            if (inputIsProxy) {
                SectionCard(title = stringResource(R.string.proxy_mode_title)) {
                    Text(
                        stringResource(R.string.proxy_mode_body),
                        fontSize = 12.5.sp,
                        lineHeight = 19.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            } else if (keyInput.isNotBlank()) {
                SectionCard(title = stringResource(R.string.direct_mode_title)) {
                    Text(
                        stringResource(R.string.direct_mode_body),
                        fontSize = 12.5.sp,
                        lineHeight = 19.sp,
                        color = Color(0xFFB3261E),
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // ---------------------------------------------------------- 凭据
            SectionCard(title = stringResource(R.string.api_key_fields)) {
                OutlinedTextField(
                    value = keyInput,
                    onValueChange = { keyInput = it.trim() },
                    label = {
                        Text(
                            stringResource(
                                if (inputIsProxy) R.string.token_label else R.string.api_key_label
                            )
                        )
                    },
                    placeholder = { Text(if (inputIsProxy) "st:…" else "sk-…") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    trailingIcon = {
                        IconButton(onClick = { visible = !visible }) {
                            Icon(
                                if (visible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = null
                            )
                        }
                    }
                )

                if (inputIsProxy) {
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = gatewayInput,
                        onValueChange = { gatewayInput = it.trim() },
                        label = { Text(stringResource(R.string.gateway_label)) },
                        placeholder = { Text("https://gw.example.com") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri)
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = modelInput,
                        onValueChange = { modelInput = it },
                        label = { Text(stringResource(R.string.cloud_model_label)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(10.dp))
                    TextButton(onClick = { vm.rotateToken() }) {
                        Icon(Icons.Default.Refresh, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.rotate_token))
                    }
                }

                Spacer(Modifier.height(10.dp))
                Text(
                    stringResource(R.string.api_key_storage),
                    fontSize = 11.5.sp,
                    lineHeight = 17.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // ---------------------------------------------------------- 测试
            SectionCard(title = stringResource(R.string.api_key_test)) {
                when (val t = test) {
                    is KeyTestState.Idle -> Text(
                        stringResource(R.string.api_key_untested),
                        fontSize = 12.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    is KeyTestState.Running -> Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        Text(stringResource(R.string.api_key_testing), fontSize = 12.5.sp)
                    }
                    is KeyTestState.Ok -> {
                        Text(
                            stringResource(R.string.api_key_ok),
                            fontSize = 12.5.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            t.detail,
                            fontSize = 11.5.sp,
                            fontFamily = FontFamily.Monospace,
                            lineHeight = 17.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    is KeyTestState.Failed -> {
                        Text(
                            stringResource(R.string.api_key_failed),
                            fontSize = 12.5.sp,
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            t.reason,
                            fontSize = 11.5.sp,
                            fontFamily = FontFamily.Monospace,
                            lineHeight = 17.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { vm.testKey(keyInput.trim(), gatewayInput.trim()) },
                        enabled = keyInput.isNotBlank() && test !is KeyTestState.Running,
                        modifier = Modifier.weight(1f)
                    ) { Text(stringResource(R.string.api_key_test)) }
                    Button(
                        onClick = {
                            vm.saveGateway(gatewayInput)
                            vm.saveCloudModel(modelInput)
                            vm.saveDeepseekKey(keyInput)
                        },
                        enabled = dirty,
                        modifier = Modifier.weight(1f)
                    ) { Text(stringResource(R.string.save)) }
                }
                if (savedKey.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = {
                        vm.saveDeepseekKey("")
                        keyInput = ""
                    }) { Text(stringResource(R.string.api_key_clear)) }
                }
            }

            // ---------------------------------------------------------- 用法
            SectionCard(title = stringResource(R.string.api_key_usage)) {
                Text(
                    stringResource(R.string.api_key_usage_body),
                    fontSize = 12.5.sp,
                    lineHeight = 19.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

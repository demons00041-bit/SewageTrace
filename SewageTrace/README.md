# SewageTrace 污水治理 · 开发交接说明

一个 Android 演示应用：拍下污水照片 → AI 判定污染等级 → 自动生成工单上报监管部门 → 在地图上找机构、预约线下送检 → 跟踪治理进度。

这份文档写给**要接手改代码的人**。目标是让你在 Android Studio 里打开就能跑、改得动、知道哪里是坑。

---

## 一、五分钟跑起来

### 1. 环境要求

| 项目 | 版本 | 说明 |
|---|---|---|
| Android Studio | Narwhal 及以上 | 需支持 **AGP 9.4.1** |
| JDK | 17 或 21 | Studio 自带的 JBR 25 也能跑 |
| Android SDK | **Platform 37** + Build-Tools 36.0.0 | 必须装这两项 |
| 模拟器 | API 26 及以上 | 推荐 API 35/36，用 Google APIs 镜像 |

> **SDK 37 是关键**。`compileSdk = 37`、`targetSdk = 37`，装不上 SDK 37 会在 Sync 阶段直接失败。
> 路径：`Settings → Languages & Frameworks → Android SDK → SDK Platforms` 勾选 *Android 37*，
> `SDK Tools` 勾选 *Android SDK Build-Tools 36.0.0*。

### 2. 打开工程

```
File → Open → 选择 SewageTrace 文件夹（注意不是废水目录的上一级）
```

选错了会看到「不是 Gradle 项目」的提示。正确的目录里应该同时有 `settings.gradle.kts` 和 `app/`。

首次打开 Studio 会自动做三件事：

1. 生成 `local.properties`（你本机 SDK 的绝对路径）
2. 用 wrapper 下载 Gradle 9.6.0（约 130 MB，视网络）
3. 拉取依赖（Compose BOM 2026.09.00 等，约 300 MB）

**如果卡在 Sync**，先看是不是网络问题，见文末排错表。

### 3. 跑起来

直接点绿色 ▶ Run 即可。默认配置就是 `app` 模块、`debug` 变体。

命令行等价操作：

```bash
# Windows
gradlew.bat :app:assembleDebug

# macOS / Linux
./gradlew :app:assembleDebug
```

产物：`app/build/outputs/apk/debug/app-debug.apk`（约 24 MB）

---

## 二、这个 App 到底是什么

### 四个 Tab

| Tab | 做什么 |
|---|---|
| **识别污水** Report | 拍照 → 识别 → 得到污染指数 + 等级 + 置信度 → 确认上报 |
| **地图** Map | 示意地图 + 机构筛选 + 机构详情 + 预约线下送检 |
| **社区** Community | 同城/全国排行榜 + 我的工单治理进度 + 全平台动态 |
| **我的** Me | 资料、数据统计、上报记录、我的预约、设置 |

### 污染等级 R0–R4

满分 100，四个证据维度加权：

| 等级 | 区间 | 含义 | 是否自动上报 |
|---|---|---|---|
| R4 重度 | ≥ 70 | 可能含工业废水 / 偷排 | ✅ 附加高危流程 |
| R3 中度 | 50–69 | 明显污染 | ✅ |
| R2 轻度 | 30–49 | 可见污染 | ✅ |
| R1 轻微 | 15–29 | 有污染迹象 | ❌ 仅存草稿 |
| R0 无 | < 15 | 未见明显污染 | ❌ 仅存草稿 |

### 治理进度怎么算

工单分 8 个阶段，每阶段有权重，进度百分比 = 已完成阶段权重之和：

```
已提交 → 工单池 → 已分派 → 现场核实 → 方案立项 → 施工治理 → 水质复测 → 验收关闭
```

**进度由服务端计算，客户端只读**。正式版绝对不能允许客户端提交进度值，
否则排名和督办数据全部失去可信度。Demo 里用 `TicketService.kt` 模拟了这个规则。

---

## 三、代码结构

```
SewageTrace/
├─ app/src/main/
│  ├─ java/com/sewagetrace/app/
│  │  ├─ SewageTraceApp.kt          Application 入口，初始化仓库与设置
│  │  ├─ MainActivity.kt            单 Activity，Compose 根
│  │  │
│  │  ├─ model/Models.kt            全部数据类 + R0–R4 枚举 + 工单阶段枚举
│  │  │
│  │  ├─ ai/                        识别引擎（这是最值得读的部分）
│  │  │  ├─ OnDeviceClassifier.kt   本机规则引擎，纯 Kotlin 图像特征加权
│  │  │  ├─ CloudClassifier.kt      DeepSeek 云端视觉识别
│  │  │  ├─ AutoClassifier.kt       自动模式：云端优先，失败回退本机
│  │  │  └─ DemoPhotoFactory.kt     Canvas 合成演示照片（无真实相机依赖）
│  │  │
│  │  ├─ data/
│  │  │  ├─ DemoRepo.kt             全局仓库（SharedPreferences 持久化）
│  │  │  ├─ ReportFlow.kt           上报流程：置信度拦截、去重、日限
│  │  │  ├─ TicketService.kt        工单状态机、进度计算、提级规则、排行榜计分
│  │  │  ├─ Settings.kt             设置读写（语言/引擎/密钥/精度）
│  │  │  ├─ SecretStore.kt          密钥加密存储
│  │  │  └─ StatsService.kt         统计聚合
│  │  │
│  │  └─ ui/
│  │     ├─ AppViewModel.kt         唯一的 ViewModel，所有状态都在这里
│  │     ├─ Nav.kt                  导航（密封类 Screen）
│  │     ├─ screens/                9 个界面文件
│  │     └─ components/Common.kt    共用组件（卡片、进度条、小徽章）
│  │
│  ├─ res/
│  │  ├─ values/strings.xml         英文（默认）
│  │  ├─ values-zh/strings.xml      中文
│  │  └─ ...
│  └─ assets/
│     ├─ agencies.json              机构数据（**虚构**）
│     ├─ standards.json             水质标准引用
│     ├─ severity-rubric.json       污染等级评分规则
│     ├─ workflow.json              工单 8 阶段定义与权重
│     └─ model-card.json            模型说明
└─ gateway/                         （在上一层）云端识别反代网关
```

**架构一句话**：单 Activity + Compose，`AppViewModel` 持有全部状态，`DemoRepo` 是单例仓库，
界面通过 ViewModel 的方法改状态。没有 Fragment、没有 Navigation Component、没有 DI 框架。

---

## 四、识别引擎（改这里前必读）

三档模式，设置里切换：

| 模式 | 行为 |
|---|---|
| **自动**（默认） | 有密钥且联网 → 云端；否则回退本机，**并在结果页显示原因** |
| 仅本机 | 只跑规则引擎，抓包零请求 |
| 仅云端 | 只调云端，失败报错不回退 |

### 本机引擎

`OnDeviceClassifier.kt` 是**规则模型，不是训练模型**。它把图片降采样后按维度打分：

```
index = 25×颜色与浊度 + 20×漂浮物 + 15×泡沫油膜 + 20×水体与岸边状态 + 10×富营养化 + 10×画质
```

（`OnDeviceClassifier.kt:149-151`，每项归一化到 0–1，最后 clamp 到 0–100）

**这个数字与任何国家标准限值无关**，它只是「画面视觉异常程度」。
改它的时候不要往「合规判定」方向解释。

### 云端引擎

`CloudClassifier.kt` 调用 DeepSeek 的 OpenAI 兼容接口。

**四个硬约束**，改代码时不要碰：

1. 图片**只能放在 `user` 消息**的 `content` 数组里。放进 `system` 或 `assistant` 会直接 400。
2. `max_tokens` 必须给足（当前 2000）。给 700 会被视觉模型的推理 token 占满，
   返回空 `content`，表现为 `云端识别失败（empty_content）`。
3. 图片先压到长边 1280 再 base64。官方会把图缩到约 1300×1300，传原图只浪费时间。
4. 单图 base64 不得超过 32 MiB，整个请求体不得超过 48 MiB。

### 密钥怎么存

**不要把密钥硬编码进 APK。** 反编译 APK 或 root 手机导出 SharedPreferences 都能拿到。

当前实现走的是**网关模式**：

```
App  ──X-ST-Token: st:xxxx──▶  你的网关  ──Authorization: Bearer sk-真key──▶  api.deepseek.com
```

APK 里只有 `st:` 令牌，真密钥在服务器上。网关代码在上一层的 `gateway/` 目录，
自带限流、模型白名单和令牌吊销，跑 `node test/forward.test.js` 有 12 项自测。

**示例代码里没有任何真实密钥**。你在设置页填的密钥只存在你本机。

---

## 五、中英文

两套资源，**key 必须一一对应（各 553 条，实测无缺失无重复）**：

```
res/values/strings.xml      English（默认）
res/values-zh/strings.xml   中文
```

切换用 `AppCompatDelegate.setApplicationLocales()`，**实时生效不用重启**。

所以 `MainActivity` 必须继承 `AppCompatActivity` 而不是 `ComponentActivity`，
主题也必须是 `Theme.AppCompat.*` 系列 —— 否则会崩在 `createSubDecor`。

### 加新文案的流程

1. 在 `values/strings.xml` 加 `<string name="xxx">English text</string>`
2. 在 `values-zh/strings.xml` 加同名的 `<string name="xxx">中文</string>`
3. 代码里用 `stringResource(R.string.xxx)`

**注意 XML 转义**：文案里的 `<` 要写成 `&lt;`，`&` 要写成 `&amp;`，否则编译报
「名称不能以某字符开头」这种完全不提正确位置的错误。

**带参数的文案**：`<string name="rank_row">%1$s %2$d</string>`，
调用时 `stringResource(R.string.rank_row, name, count)`。如果写了 `%1$s` 却调用时不传参，
界面上会原样显示 `%1$s` —— 这个 bug 出现过一次。

---

## 六、Demo 的边界（别把它当正式产品）

| 项目 | Demo 现状 | 正式版应该 |
|---|---|---|
| 地图 | Canvas 手绘示意底图 | 真实 OSM / Google 瓦片 |
| 机构数据 | `assets/agencies.json` 虚构名称 | 政府公布的机构名录 |
| 后端 | 无，全部本地 | 需要真实 API + 鉴权 |
| 上报 | 只写本地工单 | 对接政务系统 / 12345 |
| 政府处理 | 「模拟推进一阶段」按钮 | 真实状态流转 |
| 照片 | Canvas 合成 | CameraX 实拍 + EXIF |
| 识别校准 | 未做 | 用实测报告做校准数据集 |
| 数据持久化 | SharedPreferences，重装即清空 | 数据库 + 云端同步 |

**法律定位**：这是线索收集与可视化工具，不是法定检测机构，也不是官方举报渠道。
真实场景下应引导用户走 12345 / 12369，处理结论以主管部门为准。
UI 里的免责声明不要删。

---

## 七、踩过的坑（省你几个小时）

1. **`MainActivity` 在 `ui` 包下**，manifest 必须写 `.ui.MainActivity`。
   写成 `.MainActivity` 会得到 `ClassNotFoundException`，而且报错信息只说找不到类，
   不会告诉你是包名错了。

2. **用 `AppCompatActivity` 就必须用 `Theme.AppCompat.*` 主题**。
   否则崩溃日志是：
   ```
   IllegalStateException: You need to use a Theme.AppCompat theme (or descendant) with this activity
   ```

3. **Compose 的 `by` 委托要显式导入**：
   ```kotlin
   import androidx.compose.runtime.getValue
   import androidx.compose.runtime.setValue
   ```
   缺了会报 `Type 'State<Float>' has no method 'getValue'`，看起来像版本问题，其实是没导入。

4. **Kotlin 大括号不配对会产生大量误导性错误**。
   会看到「局部函数不能用 private」「Unresolved reference」等一堆错误，
   真正的错误只有一行 `Syntax error: Expecting '}'`。**先找这一行**。

5. **`enableEdgeToEdge()` 之后每个屏幕要自己处理 inset**。
   当前实现放在 `HomeShell` 里，用
   `windowInsetsPadding(WindowInsets.safeDrawing.only(Top + Horizontal))`。
   **不要**放到全局 `AppRoot`，否则登录页的深色渐变会被裁出黑边。

6. **别给文本加 `maxLines` 又不给宽度预算**。中文会被硬截断成半句。
   英文长标题的问题更严重，会和右侧元素重叠。

7. **Row 里并排的两个 `Text` 必须都带 `weight`**，只给一个会挤掉另一个。

8. **属性 setter 和同名函数冲突**：
   ```kotlin
   var autoReport by mutableStateOf(false)   // 已生成 setAutoReport(Z)V
   fun setAutoReport(v: Boolean) { ... }     // ✗ Platform declaration clash
   fun updateAutoReport(v: Boolean) { ... }  // ✓ 改名
   ```

9. **不要按行号批量改源码**。用脚本按索引替换会损坏文件
   （出现过字母被吞、标识符被替换的情况）。要改就整文件重写，
   或者做严格有序的字面量替换。

10. **AGP 9.4.1 内置 Kotlin 支持**。`app/build.gradle.kts` 里只有
    `com.android.application` 和 `org.jetbrains.kotlin.plugin.compose` 两个插件，
    **不要**再加 `kotlin-android`，加了会冲突。

11. **`compileSdk 37` 对应的构建工具是 36.0.0**。装错版本会在
    `checkDebugAarMetadata` 阶段报一堆 `requires JDK` / `requires compileSdk` 错误。

---

## 八、排错表

| 现象 | 原因 | 解决 |
|---|---|---|
| Sync 失败提示找不到 SDK 37 | 没装 Platform 37 | SDK Manager 勾选 Android 37 |
| `Unsupported class file major version` | JDK 太旧 | `File → Settings → Build → Gradle → Gradle JDK` 改成 17 或 21 |
| 依赖下载超时 | 网络 | 挂代理，或在 `settings.gradle.kts` 的 repositories 前面加国内镜像 |
| 模拟器启动黑屏 | 显示驱动 | 改用 *Software - SwiftShader* 渲染 |
| 装了却闪退 | 签名冲突 | 先 `adb uninstall com.sewagetrace.app` 再装 |
| 中文不显示 | 系统语言 | 设置 → 语言 手动切中文 |
| 云端识别失败 `empty_content` | `max_tokens` 给小了 | 见第四节第 2 条 |
| 云端识别 401 | 令牌无效或已吊销 | 重新在网关 `/v1/admin/issue` 签发 |

---

## 九、想改什么，看哪里

| 需求 | 改这个文件 |
|---|---|
| 调整污染等级阈值 | `model/Models.kt`（R0–R4 区间）+ `assets/severity-rubric.json` |
| 调本机识别权重 | `ai/OnDeviceClassifier.kt` 里的维度系数 |
| 换云端模型或提示词 | `ai/CloudClassifier.kt` 的 `MODEL` 常量和 prompt |
| 改工单阶段或权重 | `assets/workflow.json` |
| 加上报去重规则 | `data/ReportFlow.kt` |
| 加排行榜算法 | `data/TicketService.kt` 的计分函数 |
| 改机构列表 | `assets/agencies.json` |
| 加新界面 | `ui/screens/` 新建文件 + `ui/Nav.kt` 加 `Screen` 子类 |
| 加新文案 | 两个 `strings.xml` 同时加 |

---

## 十、许可与署名

- 地图图例标注 `© OpenStreetMap contributors, ODbL`，正式版换真实瓦片时**必须保留署名**。
- 机构名称、标准引用均为演示占位，不代表任何真实机构。
- 本项目仅用于学习与技术演示。

---

有任何对不上的地方，先看第七节，多数坑都在那里。

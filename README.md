# 吃药提醒 + 自动锁屏 App（MedReminder）

我妈有三高，需要按时吃药。目前她又在帮我看孩子，吃药不能强提醒，只能被动提醒。所以用workbuddy制作了一个打开手机屏幕就会立即弹窗提醒吃药的一个Android app，轻量化不占内存。同时，针对我妈看抖音时会睡着的现象，支持了10/15分钟未点击/滑动屏幕就会自动息屏的功能，可按需开启。

一款给老人使用的本地 Android 应用：分时段吃药提醒 + 日历记录 + 无操作自动锁屏。
设计遵循 iOS Human Interface Guidelines（iOS 风格 Android 应用），完全离线、轻量化。

## 功能

| 功能 | 说明 |
| --- | --- |
| 分时段吃药提醒 | 早/中/晚三个时段内，解锁手机即弹窗提醒；超时未点「已吃药」自动记为未吃 |
| 日历记录 | 月历视图展示每天三次吃药的完成情况（绿点=全吃 / 橙点=部分吃 / 灰点=未吃），点某天看明细 |
| 无操作自动锁屏 | 连续 N 分钟（默认 15）无触摸/滑动自动锁屏，播放视频不豁免 |

## 技术栈与设计决策

| 决策 | 选择 | 理由 |
| --- | --- | --- |
| 语言 | Kotlin | 现代 Android 标准 |
| UI | 纯平台 API（XML View + 系统 Material 主题） | 零 androidx、零第三方 UI 库 → APK < 3MB |
| 存储 | SharedPreferences + org.json | 数据量级为每天 3 条记录，无需数据库 |
| 日历 | 自定义 View 自绘（`CalendarView`） | 无第三方日历库 |
| 提醒 | 前台服务监听解锁广播（`ACTION_USER_PRESENT`） | 仅在解锁边界做轻量判定，无定时唤醒，省电 |
| 锁屏 | 无障碍服务（`AutoLockService`） | 系统对「跨应用感知触摸」的唯一标准方案 |
| 网络 | 不声明 INTERNET 权限 | 物理上无法联网，无任何数据上传 |

架构上采用单 Activity（视图切换）+ 两个常驻组件（前台服务 / 无障碍服务），无 Fragment、无导航库、无依赖注入，避免冗余。

## 工程结构

```
MedReminder/
├── app/src/main/java/com/zwen/medreminder/
│   ├── MainActivity.kt          # 主界面：引导 / 日历 Tab / 设置 Tab / 详情浮层
│   ├── ReminderActivity.kt      # 解锁触发的提醒弹窗页（透明 Activity）
│   ├── ReminderService.kt       # 前台服务：监听解锁广播，时段判定后弹提醒
│   ├── AutoLockService.kt       # 无障碍服务：触摸计时 + 无操作自动锁屏
│   ├── BootReceiver.kt          # 开机自启，恢复提醒服务
│   ├── data/Prefs.kt            # 设置 + 每日记录存储
│   ├── model/Models.kt          # DoseRecord / DayRecord / TimeSlot
│   ├── ui/CalendarView.kt       # 自绘月历（状态点 / 今天高亮 / 滑动切月）
│   └── util/TimeUtil.kt         # 时间工具（时段判定 / 格式化）
└── app/src/main/res/            # 布局 / 颜色 / 尺寸 / 图标 / 无障碍配置
```

## 构建

前置：JDK 17+、Android SDK（compileSdk 36）。

```bash
cd MedReminder
gradle wrapper --gradle-version 8.14.3   # 首次
./gradlew assembleRelease
```

产物：`app/build/outputs/apk/release/app-release.apk`。

如需生成签名 release 包，请将 keystore 放在本地 `keystore/medreminder.jks`，并通过 Gradle 参数或环境变量提供密码；签名文件和密码不会提交到版本库：

```bash
MEDREMINDER_STORE_PASSWORD='...' \\
MEDREMINDER_KEY_PASSWORD='...' \\
./gradlew assembleRelease
```

## 安装与使用

1. 安装 APK 后首次启动进入引导页，按提示开启两项权限：
   - **通知权限**（Android 13+ 需授权）：解锁弹窗提醒依赖
   - **无障碍权限**：自动锁屏依赖（需手动在系统设置中开启本应用的「吃药提醒 · 自动锁屏」服务）
   - **悬浮窗权限**：后台/桌面弹窗提醒依赖（Android 10+ 禁止后台启动 Activity，悬浮窗是唯一可靠的跨应用弹窗通道）
2. 主界面底部 Tab：日历 / 设置
3. 设置页可调整：提醒时段、锁屏开关与时长、权限状态与跳转

## 权限说明

- `POST_NOTIFICATIONS`：前台服务静默通知（Android 13+ 动态申请）
- `FOREGROUND_SERVICE` / `FOREGROUND_SERVICE_SPECIAL_USE`：提醒服务常驻
- `RECEIVE_BOOT_COMPLETED`：开机后恢复提醒服务
- `SYSTEM_ALERT_WINDOW`：解锁后以悬浮窗弹出提醒，绕过 Android 10+ 后台启动 Activity 限制
- **无 INTERNET 权限**：完全离线，不收集任何数据

## 设计要点（与需求文档对应）

- 弹窗为插屏居中卡片：270dp 宽、圆角 14dp、40% 遮罩，点遮罩 = 还没吃
- 按钮高度 ≥ 54dp、正文字号 ≥ 17sp、触控目标 ≥ 44×44dp（老人友好）
- 早/中/晚三个时段相互独立，超时各自记为未吃，超时后不再弹窗
- 自动锁屏：任意触摸/滑动/切窗口重置计时，屏幕熄灭取消计时，解锁后重新计时

## 已知限制

- 提醒依赖「解锁」事件：若解锁后手机一直亮屏使用（未再次锁屏），同一时段内不重复弹窗（与需求一致：时段内每次解锁都弹）
- 未授权悬浮窗时，App 在后台被系统杀死后解锁不弹窗（厂商省电策略）；建议同时在系统设置中允许本应用自启动/后台运行
- 无障碍服务需在系统设置中手动开启，部分系统关闭后无法由应用自动恢复
- 日历早于「首次运行日期」不显示状态点（无记录），此后未吃仍显示灰点

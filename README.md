# 知序 Notice · 0.2.0

一款用 Material 3 Expressive / Jetpack Compose 编写的原生 Android 通知管理 App。

[下载 APK](https://github.com/Halo0sama/Notice/releases/latest) · [验证记录](docs/VALIDATION-0.2.0.md) · [MIT 许可证](LICENSE)

- **首页预设轮播**：手指向左滑动切换营销推广、优惠促销、互动消息、热门推广四张预设，也可点击指示点切换；静默预设不会重新打开已关闭通道。
- **云雀建议**：云雀分批分析全部已扫描通道（包括系统应用和关闭通道），结合长期偏好、对话摘要及近期操作记录，完成后展示原因、涉及应用和可执行操作。点击“让云雀执行”检查具体通道，应用后可撤销；建议会保存，已完成项会标记，相关状态变化会要求重新分析。
- **同名类别批量管理**：例如按“营销推广”归集所有已创建的通知通道，检查后一起关闭或开启。
- **按应用精细管理**：查看每个通道的开关、静默级别、应用总开关和通道组限制，跳转对应系统页面设置更多选项。
- **云雀 AI 通知助手**：DeepSeek `deepseek-flash`，使用 `https://api.deepseek.com/responses` 和 SSE；查询真实通道，理解近义类别，生成可勾选的修改方案。
- **逐项回读与撤销**：写入前记录原通知级别；写入后读取系统状态；撤销时恢复每个通道的原级别，拒绝覆盖已变化的状态。
- **持久上下文**：本地保存对话、工具调用与结果、长期偏好；压力触发摘要，保留近期完整轮次；进程中断后补齐工具结果。
- **隐私与密钥**：不申请通知读取权限，不读取通知正文。API Key 用 Android Keystore AES-GCM 加密，本地数据禁用备份与设备迁移；APK 不内置密钥。

## 使用

1. 从 Releases 下载并安装 APK，启动并授权 [Shizuku](https://shizuku.rikka.app/zh-hans/)（免 Root）。手机重启后需要重新启动 Shizuku。
2. 在“知序”中连接 Shizuku，并允许系统读取已安装应用信息。小米可能单独弹出该权限。
3. 首次连接会扫描当前用户的真实通道。“概览”的营销推广按钮精确匹配所有应用的同名类别，包括系统应用；类别/应用列表默认隐藏系统应用，可在设置中显示。
4. 左右滑动首页预设卡片，或点击类别、应用按钮生成预览，检查勾选项后应用。在“记录”里查看逐项结果并撤销。
5. 使用云雀前，在右上角设置中配置 DeepSeek API Key。可以说“减少购物促销，保留物流和订单”；助手先查询，再生成方案。AI 本身不直接写入系统。

开启通道不会绕过应用通知总开关或通道组开关。未知原级别的已关闭通道开启时使用默认级别 3；有本机记录时使用上次保存的级别。精确恢复一次操作应使用“记录 → 撤销”。静默使用 Android `IMPORTANCE_LOW`，保留原声音 URI、振动等配置以支持恢复。

## 范围

这里的“类别”是 Android 通知通道的显示名称，不是所有应用共享的标准类别 ID。小米推送创建的 `mipush|...` 通道同样按真实包名、UID 和通道 ID 管理。新创建的通道需要重新扫描，不会后台自动修改。不同名称的近义类别由 AI 建议，并展示实际匹配结果。

当前直接支持通道开启、关闭、静默。悬浮、锁屏显示、厂商专有角标等额外选项通过“更多系统选项”进入系统通知页面。只管理当前用户，不访问分身或工作资料中的其他用户。部分系统保护通道可能被系统拒绝修改，结果中会明确记录失败。

## 架构

- `NotificationEngine.java`：Shizuku shell 进程中的通知服务适配；反射寻找系统接口，不硬编码 Binder 事务编号；检查包名与 UID，写前校验当前级别，写后回读。
- `Bridge.kt`：服务授权、绑定、死亡通知与生命周期管理。
- `NoticeViewModel.kt`：扫描、方案、原级别记忆、操作前日志、中断恢复和撤销。
- `AiAgent.kt`：云雀对话、工具循环、本地持久化和压缩。
- `DeepSeekClient.kt`：对话与建议共用的 Responses SSE 客户端。
- `RecommendationAgent.kt` / `Recommendations.kt`：完整分批分析、结构化建议校验、持久保存及执行前状态检查。
- `HomeWidgets.kt`：可滑动预设轮播、云雀建议与执行入口。
- `Storage.kt`：AtomicFile 存储与 Keystore 加密密钥。

云雀对话只暴露三个工具：`list_categories`、`find_channels`、`propose_changes`。工具参数校验真实通道标识；提案只能形成预览。工具返回和应用自定义通道描述被视为数据，不作为指令。每轮最多 10 步，查询分页返回，支持用户停止、限流/服务端临时错误重试、上下文溢出后的压缩重试。网络重定向关闭，密钥只发送到固定的 DeepSeek HTTPS 地址。

参考 [DeepSeek Harness](https://github.com/deepseek-ai/deepseek-harness/tree/687f282e8625c6b8d11ff58211a90524be2e4317) 的本地持久会话、闭合工具配对、保留尾部、原子摘要替换与失败恢复原则，做了适合 Android 的原生实现，并未将 Node/TypeScript harness 嵌入 APK。参阅 [compaction](https://github.com/deepseek-ai/deepseek-harness/blob/687f282e8625c6b8d11ff58211a90524be2e4317/docs/subsystems/compaction.zh.md) 和 [agent loop](https://github.com/deepseek-ai/deepseek-harness/blob/687f282e8625c6b8d11ff58211a90524be2e4317/packages/core/agent-loop/README.zh.md)。

DeepSeek Responses API 是无状态接口，因此每次发送本地重建的历史，不依赖 `previous_response_id`、服务端 conversation 或 `context_management`。参阅 [DeepSeek Responses API 文档](https://api-docs.deepseek.com/zh-cn/guides/responses_api/)。

## 开发与验证

需要 JDK 21、Android SDK 37.0、Build Tools 36.0.0。Gradle 9.8.0、AGP 9.4.1、Compose Material3 1.5.0-alpha29。minSdk 28。

在 Android Studio 中打开项目，或设置 `ANDROID_HOME` 后使用命令行。SDK 包名为 `platforms;android-37.0`，各模块显式设置 `compileSdkMinor 0`，以匹配其目录；[Android 官方构建文档](https://developer.android.com/build#module-level)说明了次级 API 版本的配置。

```sh
sdkmanager "platforms;android-37.0" "build-tools;36.0.0" "platform-tools"
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
./gradlew :fixture:assembleDebug :fixture2:assembleDebug :app:assembleDebugAndroidTest
```

`fixture` 和 `fixture2` 是独立的验证应用，各有营销推广、交易物流、优惠促销、账户提醒通道，不发送通知，不打包进主 App。设备测试只修改这些专用样本通道。

```sh
# 安装两个验证 APK 和 androidTest APK，并授权主 App 的 Shizuku/应用列表权限后：
adb -s SERIAL shell am instrument -w com.halo.notice.test/androidx.test.runner.AndroidJUnitRunner
# DOCX 密钥只在内存中读取；验证 Responses 流式工具往返：
python3 tools/check_deepseek.py /path/to/key.docx
# 为 debug 安装单独配置密钥，私有导入文件在启动时加密后删除：
python3 tools/provision_key.py SERIAL /path/to/key.docx
```

密钥不通过构建注入，不包含在源码、APK、测试报告或命令参数中。`tools/verify_engine.py` 仅允许操作固定的 `com.halo.notice.fixture` 测试包，可配合 `tools/SystemProbe.java` 检查系统服务的关闭、静默、过期快照拒绝与完整恢复。

公开的 0.2.0 APK 是已验证的 debug 构建。源码构建会使用开发者自己的调试签名，与下载版签名不同时无法直接覆盖安装。API Key 需由使用者自行配置，不提供公共密钥。

[GitHub Actions](https://github.com/Halo0sama/Notice/actions/workflows/build.yml) 会执行本地单元测试、lint 和 APK 构建。需要 Shizuku 或真实 DeepSeek Key 的设备测试按上面的步骤单独运行，不向 CI 提供个人密钥。

仓库不包含本机 SDK 路径、构建缓存、签名私钥、密钥文档、个人对话、通知通道快照或真机截图。请勿在 Issue、日志或提交中附带这些私有数据。

## 云雀建议的分析范围

每个已扫描通道都进入且只进入一个分析批次；按通道数量和内容长度分批，避免只分析前一页。每批都携带全局类别概览及用户偏好，响应必须是结构化 JSON，通道引用必须来自该批次。某批次遇到输出或上下文上限时会自动拆成更小批次重试，不会丢弃剩余通道。全部批次成功后才原子保存与展示合并建议；每个通过校验的批次都会保存断点；格式不完整会自动修正，失败或停止后可继续，已分析的信息未变化时无需从头开始。上一次完整结果仍会保留。分析不会更改系统设置。

建议依据扫描时的通道设置元数据，不包含通知正文或未授予权限的数据。扫描和偏好变化后可以重新分析；执行时重新核对相关通道，已完成建议不会重复执行。

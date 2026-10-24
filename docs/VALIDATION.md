# 验证报告

日期：2026-10-22（Asia/Shanghai）

## 已完成的验证

| 项目 | 环境 | 结果 |
| --- | --- | --- |
| 编译、7 项本地单元测试、lint | Gradle 9.8 / AGP 9.4.1 / JDK 21 | 通过，lint 无错误 |
| Responses SSE + function call/result 往返 | 实际 DeepSeek `deepseek-flash` API | 通过，正常 completed 终态 |
| 同名类别跨两个应用批量关闭、逐项回读、撤销 | 小米真机 Android 17，Shizuku shell 用户服务 | 通过；两个专用样本应用的营销通道恢复原级别，其他通道不变 |
| 过期状态拒绝写入、操作日志持久化 | 同上 | 通过 |
| App 内真实 AI 搜索和生成方案 | 同上，实际 API | 通过；方案只包含指定营销推广，call_id 与结果完整配对，返回 preview_only |
| 长对话摘要压缩 | 同上，实际 API | 通过；压缩旧轮次，保留公交提醒偏好及完整工具配对 |
| 关闭、静默、回读、过期快照拒绝、精确恢复 | 新建的独立 Android 17 模拟器 `notice-validation-20261022` | 通过；所有测试通道完整设置与测试前一致 |
| 手机与模拟器启动、布局 | 真机 1200×2608 / 模拟器 1080×2400 | 已查看截图，无启动崩溃 |
| 密钥与产物检查 | APK 解包及源码文件扫描 | 未包含用户密钥；手机只保存 Keystore 加密后的密文，私有导入文件已删除 |

真机最终扫描（清理样本后）：230 个有通道的应用，共 969 个通道；其中“营销推广”同名通道 21 个，17 个开关开启。该数字是当时设备状态，不代表其他设备，也不代表所有通道均可写。

## 测试边界

写入测试仅作用于 `com.halo.notice.fixture` 和 `com.halo.notice.fixture.second`。没有批量关闭用户真实应用的营销通道。测试 APK、两个样本应用已从个人真机卸载；知序和加密 API Key 配置保留。样本修改已逐项撤销，非测试通知设置未写入。

真实 API 工具往返与压缩测试使用合成的测试通道和对话，不将真实通知正文发往 AI。App 不申请 NotificationListenerService，不读取通知正文。测试结果不保证 AI 对所有模糊语义的判断都正确，因此修改以逐项预览和本机验证结果为准。

当前直接修改的设置为通知通道重要级别（开启/关闭/静默）。厂商专属锁屏、悬浮、角标等通过系统页面手动管理；未宣称可以直接批量改写全部厂商专用选项。新通道需要再次扫描；不含定时监控或后台自动执行。

## 主要命令

```sh
./gradlew :app:assembleDebug :app:assembleDebugAndroidTest :app:testDebugUnitTest :app:lintDebug --offline
adb -s DEVICE shell am instrument -w com.halo.notice.test/androidx.test.runner.AndroidJUnitRunner
python3 tools/check_deepseek.py /path/to/key.docx
python3 tools/verify_engine.py emulator-5580
```

原始测试日志保留在工作区被忽略的 `.private/` 目录中。交付 APK 和 SHA-256 校验文件位于 `artifacts/`。

最终 APK 已安装至真机，并对比安装路径中的 APK 与交付文件 SHA-256 一致。批量面板已验证直接展开，17 项选择与应用按钮均可见；预览后已退出，没有应用真实通知修改。

```text
9df057995a3c4efc424aff1c67d328435470a55caf9489358a30bf2b5ebdb618  Notice-0.1.0.apk
```

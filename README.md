# PVZH 钻石工具

特别鸣谢：是朵朵啊

一个使用 Kotlin 与 Jetpack Compose 编写的 Android 工具项目。本仓库同时保存应用源码、凭证池数据和版本信息，供学习、研究与经授权的测试使用。

> [!CAUTION]
> 本项目不是 EA、PopCap 或《Plants vs. Zombies Heroes》的官方产品，与上述公司及其关联方无隶属、授权或合作关系。使用前请完整阅读 [免责声明](DISCLAIMER.md)。

## 功能

- Material 3 深色界面与实时任务进度
- 凭证池和游戏版本信息自动加载
- 多镜像数据源回退，改善部分网络环境下的拉取成功率
- 请求失败后刷新当前 Token，并自动切换下一枚 Token 重试
- 批量任务、中止任务、运行日志复制
- QQ 群与 GitHub 项目入口

## 构建环境

- Android Studio（推荐使用当前稳定版）
- JDK 17
- Android SDK 37
- 最低 Android 版本：Android 7.0（API 24）

克隆后，用 Android Studio 打开仓库根目录，等待 Gradle 同步完成即可运行。也可以使用命令行：

```powershell
.\gradlew.bat testDebugUnitTest assembleDebug
```

Linux/macOS：

```bash
./gradlew testDebugUnitTest assembleDebug
```

调试 APK 生成于 `app/build/outputs/apk/debug/app-debug.apk`。

### 本地认证配置

仓库不会保存客户端密钥。需要认证刷新功能时，请任选一种方式在构建前设置：

- 环境变量：`EA_CLIENT_SECRET`
- 在不会被 Git 跟踪的 `local.properties` 中加入：`ea.client.secret=你的值`

未配置时项目仍可编译，但依赖客户端密钥的 Token 刷新请求将无法正常工作。请勿把真实密钥写入源码、Issue、构建日志或 Pull Request。

## 数据源

- `token.json`：凭证池数据
- `version.json`：游戏内容版本
- `.github/workflows/daily_fetch.yml`：原有数据同步工作流

应用依次尝试 jsDelivr、GitHub 代理和 GitHub Raw。镜像仅用于提高公开仓库文件的可达性；其可用性、时效性和安全性不由本项目保证。

## 项目结构

```text
app/src/main/java/com/example/diamond/       Android UI 与状态管理
app/src/main/java/com/example/pvzh/data/     网络、认证、凭证池与任务逻辑
app/src/main/res/                            图标及 Android 资源
gradle/                                      Gradle Wrapper 与版本目录
```

## 参与贡献

欢迎提交 Issue 和 Pull Request。开始前请阅读 [贡献指南](CONTRIBUTING.md) 与 [安全策略](SECURITY.md)。请勿在 Issue、日志或提交中公开私人账号、Cookie、访问令牌或其他敏感信息。

## 开源许可

代码基于 [MIT License](LICENSE) 开源。第三方产品名称、商标和游戏素材归各自权利人所有，MIT 许可证不授予任何第三方商标、服务或内容的权利。

## 兼容性问题与并发实测记录

开发和设备差异排查中确认了以下问题：

- HTTP 2xx 只表示服务器接收并处理了请求，不代表奖励一定生效。现在只有响应中存在 `rewards`，且 `gemsAwarded` 与请求数量一致时，才记录为“确认成功”。
- 并发请求可能同时返回 HTTP 200，但只有部分响应实际包含奖励；常见竞争响应为 `{"rewards":[]}`。该响应现在单独统计为“竞争落败”，不会计入成功、刷新 Token 或淘汰当前公共凭证。
- 批量结果分别展示“确认成功、竞争落败、真实失败”，避免把 HTTP 成功或部分成功显示成全部完成。
- 只有 401/403 认证错误会触发 Token 刷新。429、网络错误和业务拒绝保留各自分类与响应摘要，避免无意义地反复刷新公共凭证。
- 高并发下，同一失效 Token 可能被多个任务同时刷新。当前使用单飞互斥刷新：第一个任务负责刷新，其余任务复用更新后的 Token。
- `version.json` 拉取失败或不是合法的 32 位十六进制 Content-Version 时会停止加载，避免静默携带旧版本或把代理错误页作为版本号发送。
- 库存同步接口依赖目标账号自己的认证信息，不属于通用成功验证手段，因此应用不会调用该接口验证目标账号余额。

### 并发进程数

在隔离其他请求后，以相同公共凭证、相同目标、每次相同最小请求量，对 1、10、20 并发分别进行了 5 轮实测。响应确认成功数与测试账号的最终余额增量一致：共确认 25 次，余额也增加 25。

| 并发数 | 确认成功 | 竞争落败 | 真实失败 | 平均每轮耗时 | 确认成功吞吐 |
|---:|---:|---:|---:|---:|---:|
| 1 | 5 | 0 | 0 | 3.193 秒 | 0.313 次/秒 |
| 10 | 6 | 44 | 0 | 4.090 秒 | 0.293 次/秒 |
| 20 | 14 | 85 | 1 | 6.987 秒 | 0.401 次/秒 |

20 并发在本次环境中的确认成功吞吐最高，比单线程约高 28%，因此当前批量任务默认使用 20 并发，同时将内部并发上限限制为 20。并发效果会受网络、服务器状态、凭证状态和目标账号竞争影响；`rewards=[]` 属于竞争结果，不等同于程序异常或 Token 失效。

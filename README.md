# PVZH 钻石工具

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

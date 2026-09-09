# Release 构建与签名

v1.0.0：versionCode 10000，applicationId `com.chacha.jadeime`，Android 10+，仅 arm64-v8a。Release 不包含 debug instrumentation、测试 PCM 或调试 UI 工具。

## 构建

```shell
python tools/prepare_voice.py
./gradlew assembleDebug testDebugUnitTest assembleRelease
```

模型资源不入 Git，由固定 URL/SHA-256 下载；静态词库在仓库中，转换脚本及对应原始词库随 Release 提供。应用不会在手机首次启动时下载模型。

## 私有签名配置

设置 `JADE_SIGNING_PROPERTIES` 指向仓库外的 properties 文件；未设置则读取 `${user.home}/.jadeboard/release-signing.properties`。

```properties
storeFile=/absolute/private/path/jadeboard-release.jks
storePassword=YOUR_PRIVATE_PASSWORD
keyAlias=jadeboard
keyPassword=YOUR_PRIVATE_PASSWORD
```

上述只是格式示例，不是可用密钥。没有文件时生成 unsigned APK，不得作为正式安装包发布。私钥及配置必须安全备份；丢失签名密钥将无法正常覆盖更新。不要把它们放进仓库、Release 附件或分享给用户。

验证：Android SDK `apksigner verify --verbose --print-certs <apk>`；记录 SHA-256。发布附件包含签名 APK、SHA256SUMS.txt、词库源数据包和开源/源码公开声明。GitHub release tag 应指向构建对应提交。

## 从 Debug 切换

正式签名不同于 Android Debug 签名，Android 不允许直接覆盖。不要卸载用户正在使用的 Debug 版作为自动发布步骤；卸载会删除本地数据。目前没有全量数据迁移工具。用户自行保存短语/主题等后决定何时切换。

## v1 发布检查

- 新安装草稿/回传均关闭，地址/密钥为空；配对密钥不进入 APK。
- HTTPS + 私有地址、禁止重定向、系统证书校验；每设备独立密钥由用户自己的后端管理。
- 无录音文件、用户数据库、私钥或 Debug 测试组件进入 Release。
- PolyForm 仅用于本项目原始代码，第三方独立组件保留原许可证和对应源数据；不是 OSI 意义的开源许可证。

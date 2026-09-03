# CLAUDE.md — 施工约定

## 项目

安卓自用输入法「叶键盘 JadeBoard」。先读 `README.md`（决策）→ `DESIGN.md`(架构) → `PLAN.md`（当前干哪期）。

## 规范

- 包名 `com.chacha.jadeime`；Kotlin 2.x + Jetpack Compose；minSdk 29 / target 35；仅 arm64-v8a
- 单模块 app 工程，按 feature 分包：`ime/`(service+ui)、`engine/`(拼音)、`voice/`、`theme/`、`stats/`、`clipboard/`、`settings/`、`data/`(Room)
- 依赖能少则少；不引 Hilt（手写简单 ServiceLocator 即可）、不引 RxJava；协程 + Flow
- 每完成一个 PLAN 勾选项就构建一次 `./gradlew assembleDebug`，保持随时可安装
- 拼音引擎必须走 `PinyinEngine` 接口，不许把 Trie 实现耦合进 UI
- 词库构建脚本放 `tools/`，PC 端服务放 `server/`，与 app 代码隔离

## 红线（隐私）

- 任何情况下不记录、不上传用户输入的明文内容
- `inputType` 为 password/邮箱等敏感变体时：不统计、不进剪贴板历史、不进用户词库
- 上报开关默认关闭

## 本机构建环境（已验证，别乱改）

- 构建 JDK 用 Android Studio 自带 JBR（JDK 21），**不要用系统默认 java（JDK 26，Gradle 8.12 无法解析）**；构建前确认 JAVA_HOME 指向 JBR，或在 `gradle.properties` 里固定 `org.gradle.java.home`
- `local.properties` 指向 `D:\soft3\AndroidSDK`（已 gitignore，重建环境时需手动补）

## 已知坑（先看再写）

- Compose 用于 IME 窗口需手动提供 Lifecycle/ViewModelStore/SavedStateRegistry owner，参考 FlorisBoard 源码解法，M0 第一件事验证
- `InputMethodService` 进程常驻，注意内存：Trie 异步构建、ASR 模型懒加载闲置释放
- IME 里不要启动 Activity 干重活，设置一律走独立 Activity

## 验收方式

每期 PLAN 末尾有验收标准，真机（用户自己的手机）装上用，用户说行才算完。

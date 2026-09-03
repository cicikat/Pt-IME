# 叶键盘 (JadeBoard) — 安卓自用输入法

个人自用的安卓输入法：本地离线优先，可选连接电脑后端，输入行为统计回传日报小助手 + AI 陪伴角色。

## 需求评估结论（2026-07-17）

| 需求 | 可行性 | 说明 |
|---|---|---|
| 基础打字（26键/9键、中文拼音、英文） | ✅ 可行 | Android `InputMethodService` 标准路线，英文简单，拼音是全项目最大工作量 |
| 语音转文字 | ✅ 可行 | sherpa-onnx 离线模型（本地）+ 可选 PC 后端 WebSocket（更准） |
| 表情符号预设 | ✅ 简单 | emoji 面板 + 常用颜文字/短语预设 |
| 智能词库（常打的字自动排前） | ✅ 可行 | 用户词频表，候选排序时叠加权重，这是自研引擎的天然优势 |
| 中英切换、剪贴板 | ✅ 简单 | 标准功能 |
| 皮肤/UI 自定义 | ✅ 可行 | JSON 主题方案（参考 FlorisBoard Snygg 的简化版） |
| 击键统计回传日报/角色 | ✅ 可行 | IME 自研自用无网络限制，本地 SQLite 聚合，每日 HTTP 上报 |

## 关键决策（已拍板）

1. **拼音引擎：纯 Kotlin 自研词库引擎**，不用 librime/RIME。
   - 理由：librime 需要 NDK + JNI + CMake 交叉编译，是 Trime/fcitx5-android 团队多年维护的深坑，Claude Code 施工风险极高；自研 Trie + 有界 Beam 解码全程 Kotlin，可控、可测试，且可在不记录明文的前提下做会话内重排。
   - 代价：整句智能仍低于 RIME/搜狗（尚未引入大规模语言模型）；当前用词频与全局切分改善词组连打，后续可按需要接 librime JNI 或 PC 后端整句转换。
   - 升级路线已留：候选不满意时 M-later 接 librime JNI 或 PC 后端整句转换，接口已按可替换设计（`PinyinEngine` 接口）。
   - 词库：开源 rime-ice 雾凇拼音词库（GPL-3.0，自用无碍）转 SQLite。
2. **语音：双通道**。默认本地 sherpa-onnx streaming zipformer 中英双语小模型（~40MB，流式实时上屏）；设置里可切 PC 后端模式（WebSocket 推流到电脑，电脑跑 FunASR/whisper，识别更准）。SenseVoice int8（~230MB）作为可选高精度本地模型，不默认内置。
3. **回传内容（传什么，我替你拍了）**：只传行为画像，永不传输入内容明文。
   - 数据集：击键数、打字字符数、退格数/退格率、会话次数、总时长、每小时分布、top 使用 App、中英文比例、语音输入次数/时长、emoji top5、深夜打字标记（0-5点有输入）。
   - 角色看的是服务端派生的浓缩画像（总字数、活跃时段、late_night、退格率烦躁信号、emoji 情绪 top3、语音占比），能说出"你今天打了一万二千字，凌晨两点还在敲字"这类话，情绪价值拉满且零隐私泄露。
   - **通道（2026-07-17 按三仓源码修订）**：IME 只推 Emerald-presence `POST /sensor/ime`（照抄其现有 `/sensor/push` 手机传感器模式，Bearer token）；assistant 无 HTTP 服务器（local-first 架构），其 daemon 从 Emerald-presence 落盘的 `data/ime_stats/{date}.json` 文件拉取（同它扫 obsidian vault 的模式）。
   - 红线：密码/邮箱等敏感 inputType 字段完全不统计；明文内容任何情况不落盘不上传。
4. **UI：Jetpack Compose 全自研**，不 fork 现有键盘。皮肤 = JSON 主题文件（颜色/圆角/字体大小/背景图/按键音），运行时热切换。
5. **最低支持 Android 10 (API 29)，目标 API 35**，仅 arm64-v8a。

## 参考项目（施工时可查源码抄思路，不 fork）

- [FlorisBoard](https://github.com/florisboard/florisboard) — Compose IME 架构、Snygg 主题引擎
- [HeliBoard](https://github.com/heliborg/heliboard) — 离线隐私键盘、布局定义
- [fcitx5-android](https://github.com/fcitx5-android/fcitx5-android) / [Trime](https://github.com/osfans/trime) — librime 集成方式（备用升级路线）
- [sherpa-onnx](https://github.com/k2-fsa/sherpa-onnx) — 离线 ASR，官方有 Android Kotlin 示例和 IME 语音示例

## 文档索引

- `DESIGN.md` — 技术架构设计
- `PLAN.md` — 分期施工计划（Claude Code 按此施工）
- `CLAUDE.md` — 施工规范与约定

## 关联项目

- `D:\ai\assistant` — 日报小助手，接收统计数据（需在其侧新增 `/api/ime/stats` 接收端点）
- `D:\ai\Emerald-presence` — AI 陪伴后端，接收每日画像（需新增接收端点）
- `D:\ai\Emerald-mobile` — 陪伴前端（本项目不直接对接）

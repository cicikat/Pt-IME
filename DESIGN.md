# DESIGN — 技术架构设计

> 当前草稿及语音实现以 [三小时草稿 v2](docs/draft_sync_v2.md) 为准：用户授权的数字遮蔽长条与版本化 HTTPS 回传、Android 系统语音识别。下述日报聚合与 sherpa-onnx 为原规划。

## 1. 总体架构

```
┌─────────────────────────────────────────────────────┐
│ JadeIME (InputMethodService)                        │
│  ┌───────────────┐  ┌──────────────────────────┐    │
│  │ KeyboardView   │  │ InputSessionController   │    │
│  │ (Compose UI)   │←→│ 按键事件→引擎→commitText  │    │
│  └──────┬────────┘  └───────┬──────────────────┘    │
│         │ ThemeManager       │                       │
│  ┌──────┴────────┐  ┌───────┴──────────────────┐    │
│  │ Snab 主题引擎  │  │ PinyinEngine (interface) │    │
│  │ (JSON 皮肤)    │  │  └ TrieLexiconEngine     │    │
│  └───────────────┘  └───────┬──────────────────┘    │
│  ┌───────────────┐  ┌───────┴──────────────────┐    │
│  │ VoiceInput     │  │ SessionRanker (memory)   │    │
│  │ sherpa-onnx /  │  │ 会话内候选重排            │    │
│  │ PC WebSocket   │  └──────────────────────────┘    │
│  └───────────────┘                                   │
│  ┌───────────────────────────────────────────────┐  │
│  │ StatsCollector → SQLite → DailyReporter(HTTP)  │  │
│  └───────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────┘
         │ HTTP POST (每日/手动)
         ▼
  assistant (日报)          Emerald-presence (角色画像)
```

单模块 app 工程（不搞多模块，个人项目降复杂度），包名 `com.chacha.jadeime`。

## 2. 技术栈

- Kotlin 2.x + Jetpack Compose（键盘 UI 与设置页共用）
- SQLite via Room（词库、用户词频、统计、剪贴板历史）
- kotlinx.serialization（主题 JSON、上报 JSON）
- OkHttp（上报 + WebSocket 语音推流）
- sherpa-onnx AAR（离线 ASR，官方 maven/jitpack 或直接放 libs/）
- minSdk 29 / targetSdk 35 / 仅 arm64-v8a

## 3. 核心模块设计

### 3.1 InputMethodService 层

`JadeImeService : InputMethodService`：

- `onCreateInputView()` 返回 `ComposeView`（注意：IME 窗口无 ViewTreeLifecycleOwner，需手动实现并 attach，FlorisBoard 有成熟做法，施工时抄）。
- `onStartInputView(EditorInfo)`：读取 `inputType` 决定键盘形态（文本/数字/URL/密码）；密码字段设 `sessionSensitive=true`（统计豁免）；记录 `editorInfo.packageName` 供统计。
- `onFinishInputView()`：结束输入会话，flush 统计。
- 候选确认走 `InputConnection.commitText`；未确认的拼音 composing 只保留在键盘本地 UI，避免在宿主文本框产生闪烁或残留。

### 3.2 键盘 UI（Compose）

- 布局定义为数据类 + JSON：`KeyboardLayout(rows: List<Row>)`，Key 含主字符、长按弹出候选、宽度权重、特殊动作（shift/backspace/enter/space/lang-switch/symbols/voice/clipboard/emoji）。
- 内置布局：qwerty-zh（26键拼音）、qwerty-en、九宫数字页、横排常用符号页、emoji 面板、剪贴板面板。9键 T9 放低优先级（M-later）。
- 交互：点按、长按重复（退格）、长按弹出、滑动光标（空格滑动移光标）、按键气泡、震动/按键音（可关）。
- 候选栏：横向 LazyRow，首屏 5-8 候选，下拉展开全部。

### 3.3 拼音引擎（PinyinEngine 接口）

```kotlin
interface PinyinEngine {
    fun input(pinyin: String): List<Candidate>   // 增量输入
    fun choose(candidate: Candidate): ChooseResult // 可能剩余未消费拼音
    fun reset()
}
```

`TrieLexiconEngine` 实现（**2026-07-17 M1 施工后修订，按实际代码同步**）：

- **词库**：rime-ice `cn_dicts/8105`(字表，全量保留) + `cn_dicts/base`(词组，按词频过滤，`tools/build_lexicon.py --min-word-freq` 默认 2000) 预处理为 SQLite 表 `lexicon(id, pinyin_key, initials, word, base_freq)`。`pinyin_key` 为无调全拼串（如 `nihao`），`initials` 为逐音节首字母（如 `nh`，构建期从 rime 原始"空格分隔音节"直接算出，供简拼检索，是对早期设计的补充字段）。当前产出约 20 万条，落在原定"10-20 万条"量级内。随 APK assets 分发；**首启拷贝改用 Room `createFromAsset` 内置机制**，不需要手写拷贝代码（`data/LexiconDatabase.kt`）。
- **检索**：两棵内存 Trie，分别以 `pinyin_key`/`initials` 为 key，节点存完整命中该 key 的词条；查询时从头walk输入串、每步收集当前前缀命中的词条，天然得到"词典里所有是输入前缀的词"，全拼、简拼共用同一套结构。M1 暂未做"构建完成前 SQLite LIKE 兜底"（个人使用场景下 Trie 构建远快于 1-2s，先简化）。
- **切分**：全拼/简拼共用两棵 Trie 提供每个输入位置的可走词条；在完整缓冲区上跑宽度 12 的 Beam Search，取分数最高的完整路径。单字母简拼保留但降权，且只在恰好吃完当前片段时参与，避免中段噪声；手动分隔符 `'` 是硬边界。模糊音留 M-later。
- **纠错**：qwerty 邻接表 + 单字符替换。纠错候选始终以明显罚分参与排序，而非只在原串无候选时触发；这样"有效但打错的拼音"也可被救回，且不会压过直接命中。
- **整句**：Beam 路径切出 ≥2 段时作为 `isSentence` 候选展示；它和单词候选按相同的消费长度与分数排序，绝不因 `isSentence` 标记被强制置顶。更大语言模型/Viterbi 是后续升级点。
- **排序与隐私**：候选携带显式来源层级：用户词、合法单音节精确、完整多字词、纯全拼整句、保守纠错、全拼前缀、简拼。合法单音节（例如 `xuan`）的精确同音字先于 `xu + an` 拆分句；`shihuai` 和既有长句 Beam/保守纠错回归保持有效。候选刷新是 latest-only：Compose 只提交当前 revision/raw pinyin，旧任务在 Trie、Beam、纠错阶段通过有界取消检查点尽快退出，刷新期间不展示旧候选。
- **v2 记忆与造词**：`LexiconEntry.id` 保留只读词库行 ID；候选 comparator 只按 entry-id 列表查内存，不做 SHA/十六进制格式化。一次 composing 中连续选择的静态组件，在完整消费后组成用户词；引擎从组件行恢复 word、canonical pinyin 和 initials。Room `candidate_memory_v2` 仅保存单个静态 ID 或有序组件 ID 序列、次数、逻辑时钟和词库版本指纹；词库版本/组件不匹配时 fail closed。旧 `candidate_memory` 表保留但不再读写，`learned_word`/`user_freq` 也不参与本链路；敏感字段关闭记忆读取与写入。
- 英文模式无引擎直通，带简单单词补全（英文词频表 top 10k，M-later 可选）。

### 3.4 语音输入

- `VoiceInputController`，两个实现：
  - `LocalAsr`：sherpa-onnx streaming zipformer 中英双语模型（bilingual zh-en small，int8 ~40-70MB），流式识别边说边上屏 composing text。模型放 assets 或首启下载（拍板：**随 APK 打包**，自用不在乎包体）。
  - `RemoteAsr`：WebSocket 连 PC 后端（地址在设置页配），16kHz PCM 推流，收 JSON partial/final。PC 端协议定义在 `PLAN.md` M6，服务端实现放 Emerald-presence 或独立小服务均可（拍板：独立 `asr-server` 脚本，放本仓库 `server/` 目录，FunASR/sherpa-onnx python 实现）。
- 麦克风权限在设置页引导授予；键盘上麦克风键按住说话/点击开关两种模式（拍板：点击开关）。

### 3.5 主题系统（Snab）

JSON 主题文件，字段：

```json
{
  "name": "墨玉", "isDark": true,
  "keyboardBg": "#1A1B26", "keyBg": "#24283B", "keyBgPressed": "#414868",
  "keyText": "#C0CAF5", "keyTextSecondary": "#565F89",
  "accent": "#7AA2F7", "candidateText": "#C0CAF5",
  "keyCornerRadius": 8, "keyGap": 4, "fontScale": 1.0,
  "bgImage": null, "keySound": null, "keyboardHeightScale": 1.0
}
```

- 内置 4 套（亮/暗/护眼绿/粉），用户主题放 `filesDir/themes/*.json`，设置页导入/编辑/热切换。
- Compose 侧 `LocalJadeTheme` CompositionLocal 全局下发。

### 3.6 剪贴板 & Emoji

- 剪贴板：`ClipboardManager.addPrimaryClipChangedListener` 监听，历史存 Room（上限 50 条，可固定收藏），键盘剪贴板面板点击上屏。敏感字段（密码框）期间不记录。
- Emoji：Unicode emoji 按类别静态表 + 最近使用（独立 MRU 机制）+ 颜文字/自定义短语预设页（用户在设置页维护短语，如常用地址、邮箱）。

### 3.7 统计与上报（StatsCollector）

事件模型（只计数，不存内容）：

```
key_event: ts, type(char|backspace|enter|space|candidate|emoji|voice), app_pkg, lang
session: start_ts, end_ts, app_pkg, chars_committed, backspace_count, candidate_picks
voice_use: ts, duration_ms, chars_out, mode(local|remote)
```

- 内存累积，会话结束批量写 Room；`sessionSensitive` 会话完全跳过。

**上报架构（2026-07-17 修订，已核对三仓源码）**：IME **只推一个目标** —— Emerald-presence（它是 PC 上唯一常驻 HTTP 服务）。assistant 无 HTTP 服务器（local-first 架构，daemon 走"拉取"集成，同 obsidian/mail_stats 模式），由它自己去拉 Emerald-presence 落盘的文件。

```
IME (手机) ──POST /sensor/ime (Bearer token)──▶ Emerald-presence (admin server)
                                                  ├─ portrait 摘要 → user_profile.ime_today / ime_log
                                                  │   （角色感知，模式照抄现有 /sensor/push 的 phone_sensor_today）
                                                  └─ 全量 payload → data/ime_stats/{date}.json（保留90天）
                                                        ▲
assistant daemon ── ime_stats.py collect(day, path) ──┘ （本地文件读取，路径进 settings，
                                                          同 obsidian_vault_path 模式）
```

- `DailyReporter`（WorkManager 每日 23:50 + 手动触发）聚合当日，POST 单一 payload：

```json
{
  "date": "2026-07-17", "device": "phone",
  "keys": 12345, "chars": 8000, "backspaces": 600, "backspace_rate": 0.075,
  "sessions": 40, "duration_secs": 5400,
  "hours": [0,0,"...24个整数，每小时chars"],
  "top_apps": [{"pkg": "com.tencent.mm", "chars": 5000}],
  "lang_ratio": {"zh": 0.8, "en": 0.2},
  "voice": {"count": 5, "duration_secs": 300, "chars": 800},
  "emoji_top": ["😂","🥺","✨"],
  "late_night": false
}
```

- portrait（角色看的浓缩版）由 Emerald-presence 服务端从该 payload 派生（total_chars、活跃时段、late_night、backspace_rate、emoji_top、voice 占比），IME 不用发两份。
- 鉴权：Bearer token，与 Emerald-mobile `backend_client.dart` 同机制（admin token registry，scope `sensor.write`）；IME 设置页配 base URL + token，可直接复用手机上 Emerald-mobile 用的 token。
- 失败重试：本地保留 30 天，每次上报把未确认日期一起补传；assistant daemon 本就会回补近几日 summary，晚到一天无碍。
- 统计功能默认开、上报默认关（首次配好地址后再开）。

### 3.8 设置页（普通 Activity, Compose）

普通「设置」页负责键盘启用引导（跳系统 IME 设置）、模糊音/键高/震动声音、语音模式与 PC 地址、剪贴板与短语管理、统计开关与上报配置、用户词库导出/清空；「主题与皮肤」为独立页，由键盘工具栏皮肤入口直接打开。两页共用一个持久化的日/夜显示偏好，但它不改变键盘皮肤本身的「跟随系统」选择。

## 4. 风险清单

1. **Compose in IME window 的 lifecycle 坑** — 已知问题有成熟解法（FlorisBoard `provideLifecycle` 方案），M0 首先验证。
2. 自研引擎整句体验 — 预期打词组没问题、长整句一般；不满意再上 Viterbi 二元语法或 librime，接口已隔离。
3. sherpa-onnx AAR 集成 — 官方有现成 Android 示例与预编译包，风险低；真机验证 RTF。
4. 词库构建脚本一次性成本 — rime-ice yaml 格式简单，脚本半天工作量。
5. IME 进程内存 — 键盘进程常驻，Trie + ASR 模型同时加载需控制 <300MB；ASR 模型懒加载、闲置 60s 卸载。

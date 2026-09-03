# PLAN — 分期施工计划

每个里程碑独立可用、可真机验收。按顺序施工，完成一个勾一个。

## M0 — 骨架：能打英文的键盘 ✦ 全项目地基

- [x] Gradle 工程：Kotlin 2.x、Compose BOM、Room、kotlinx.serialization、OkHttp、minSdk 29 / target 35、仅 arm64-v8a
- [x] `JadeImeService : InputMethodService`，manifest 注册 IME（`android.view.im` metadata + method.xml）
- [ ] **先验证 Compose in IME window**：ComposeView + 手动 LifecycleOwner/SavedStateRegistryOwner/ViewModelStoreOwner（参考 FlorisBoard 做法）——这是 M0 最大坑，最先做
  - 代码接入与构建检查已完成；真机 IME 窗口运行验收待有调试数据线后进行。
- [x] qwerty 英文布局渲染 + 点按上屏 + shift（中文态单击进英文、英文态单击回中文、英文态长按切换大写）+ 退格（长按重复）+ 空格 + 回车（按 imeOptions 变 action）+ 九宫数字页
- [x] 按键气泡、震动反馈
- [x] 设置 Activity 壳：启用键盘引导（跳转系统设置 + 切换 IME 弹窗）
- 验收：真机设为默认输入法，微信里流畅打英文

## M1 — 中文拼音 ✦ 最大工作量

> 2026-08-11 修订：M1 早期候选记忆条目仅保留为历史施工记录；当前实现以 M2.5 的 P0 latest-only、来源分层和 `candidate_memory_v2` 条目为准。

- [x] 词库构建脚本 `tools/build_lexicon.py`：拉取 rime-ice `8105`(全量字表) + `base`(词组，按 `--min-word-freq` 过滤，默认 2000) → 生成 `lexicon.db`（`pinyin_key`/`initials`/`word`/`base_freq`），入 assets。当前产出 200479 条（8105 字全量 + 约 19.2 万高频词），命中 DESIGN 3.3 的"10-20万"量级；下载源缓存在 `tools/.cache/`（已 gitignore）
- [x] Room 双库：`LexiconDatabase`（只读，`Room.createFromAsset("lexicon.db")` 直接托管首启拷贝，不需要手写拷贝代码）+ `UserDataDatabase`（读写，`learned_word`/`user_freq` 两表）
- [x] `TrieLexiconEngine`（`engine/` 包，零 Android 依赖，JVM 可测）：两棵 Trie 分别按 `pinyin_key`/`initials` 建索引；全拼前缀匹配 + 简拼（≥2 字母，1 字母简拼过滤掉避免噪声）；整句用"每步取最长可成词段，长度打平比分数"的贪心切分
- [x] 候选栏 UI：`ImeRoot` 顶部 `LazyRow` 横排展示，点选消费拼音、剩余拼音回填键盘本地 composing 状态继续候选；下拉可展开查看全部候选。候选刷新期间清空旧标签，latest-only 任务只允许当前 revision 回写。
- [x] 候选自学习：全拼候选选中一次立即置顶；同词用首字母简拼命中时提升到第二位。记忆跨输入会话和进程重启保留，只落盘候选身份 SHA-256、次数与最近使用时钟，不保存拼音或候选明文；旧 `learned_word`/`user_freq` 保持不读不写
- [x] 移除「中/En」切换键；Shift 负责中英文直接切换，`?123` 直接进入九宫数字页；中文态下无候选时空格/回车/切页把已敲的拼音字母串原样上屏
- [x] 全半角标点映射（中文态）：`, . ! ? ; : ( )` → `，。！？；：（）`；其余符号（`@#$%&*-+=_/\` 等）保持半角，未做完整映射
- [x] 引擎 JVM 单元测试 `TrieLexiconEngineTest`：全拼精确匹配、简拼、1 字母简拼噪声过滤、整句贪心切分、单条目候选不重复生成整句候选、剩余拼音上报、同音字按 base_freq 排序、选中后 user_freq 反超排序、连续单字自造词、reset 打断连字流、跨引擎实例重新播种自造词、空输入、无匹配输入——14 条全绿，`./gradlew testDebugUnitTest` 验证，**无需真机**
- 验收：单测全绿（已过）+ 真机部分（候选点选是否顺手、"茶茶"这类自造词打两次后排第一）待数据线到位后跑

## M1.5 — 真机反馈修缮（2026-07-18 首次真机验收产出）

- [x] **简拼检索**：首字母匹配（`nh`→你好）+ 简拼/全拼混输（如 `nih`→你好、`n'hao`→你好）；简拼命中的选词同样写入 user_dict（与全拼共用词条，不分家）。实现：`TrieLexiconEngine` 不再过滤 1 字母简拼，改为降权（`SINGLE_LETTER_ABBREV_WEIGHT`）；`bestSegmentMatch` 在整句切分里全拼优先、简拼兜底，且 1 字母简拼只在恰好吃光剩余串时才采信，避免中途乱猜出噪声候选（如 `nihaoma` 不会被拆成"你好们"）。9 条新增单测覆盖。
- [x] **邻键纠错**：qwerty 邻接表 + 单字替换纠错；仅当原始串既无全拼/多字母简拼命中、整句切分也全靠 1 字母简拼撑起时才触发（`hasConfidentMatch` 判定）；纠错候选降权（`CORRECTION_WEIGHT`）；不做整句级智能纠错（成本收益不符，M-later）。范围收敛为"单键替换"，未做插入/删除两种编辑，真机反馈后再评估是否需要。
- [x] **拼音切分可视 + 手动分隔**：`PinyinEngine.previewSegmentation()` 暴露自动切分片段，composing 激活时候选栏最左侧文字回显切分结果（如 `nihao'ma`）；候选栏紧接着固定一个 `'` 键插入 `PINYIN_SEPARATOR`，强制分段点，切分逻辑原生跳过该分隔符不跨界匹配
- [x] **顶部工具栏**：无 composing 时候选栏行让位给 `ToolbarRow`，布局 `[符号][皮肤][emoji][设置] …… [收起▾]`；有 composing 时整行回到 `ComposingBar`（切分回显 + `'` 键 + 候选）。第一个入口打开不带数字行的横排符号页，再点一次回到来源键盘；`?123` 仍直达九宫数字页。皮肤入口直达「主题与皮肤」页，齿轮进入普通设置页，收起均为真实功能。
- [x] 收起 = `requestHideSelf(0)`
- 验收：单测 23 条全绿（`./gradlew testDebugUnitTest`）+ `assembleDebug` 通过；真机验收待有数据线后进行——简拼出词、邻键救回、切分回显、工具栏（皮肤/emoji 除外）需真机确认

## M1.6 — 二轮真机反馈：交互修缮（2026-07-18）

- [x] **模式切换防误触**：中英文由 Shift 切换，`?123` 直达九宫数字；工具栏首位仅负责横排符号页的可逆切换，不再存在 `LangToggle`/`ModePanel`。
- [x] **工具栏入口落地**：皮肤和 emoji 均为真实入口；语音仍由长按空格进入 M4 占位提示，不保留独立工具栏空壳。
- [x] **composing 拼音移入键盘内**：拼音不再 `setComposingText` 进应用文本框——`onSetComposingText`/`onFinishComposing` 整条链路已删除，`updateComposing`/`chooseCandidate` 只更新 Compose 本地状态，`ComposingBar` 把切分回显文字放进键盘左上角一个小 `Surface` 块；选中候选才走 `onCommitText`
- [x] **候选栏展开**：候选行右端新增「⌄/⌃」按钮，展开为 `LazyVerticalGrid`（5 列，覆盖键盘下半区，可滚动）；点选候选后 `candidatesExpanded` 自动置回 false
- [x] **键帽角标小字符**：`KeySpec.corner` 字段 + 长按手势（`detectTapGestures(onLongPress=...)`），Q行标 1-0，A行标 `@#$%&-+()`，Z行标 `:;"'!?/`；长按气泡内容切换成角标字符并直接上屏该字符
- [x] **「分词」实体键**：字母页 Z 行最左（Shift 右侧）新增 `KeyAction.Separator` 键（label `'`），有 composing 时插入 `PINYIN_SEPARATOR`，否则原样上屏 `'`；同时移除了 M1.5 候选栏里的 `'` 按钮。语音入口改为长按空格，键盘中没有独立 `KeyAction.Mic`。
- 已知取舍：模式面板/角标符号的具体文案、面板锚点位置是本次施工时按体验直觉定的（PLAN 没给死规格），真机用起来别扭的话再调
- 验收：`./gradlew assembleDebug` + `testDebugUnitTest` 均过；真机验收（误触是否消失、长拼音候选翻找是否轻松、文本框内是否不再出现拼音串、角标长按是否顺手）待有数据线后进行

## M1.7 — 三轮真机反馈：视觉与标点打磨（2026-07-18）

**工具栏**
- [x] 彩色 emoji 图标（🎨😀🎤等）全部换成单色线性抽象 icon（Material Symbols 风格，与设置齿轮统一）；「收起▾」去文字只留图标；五个入口平均分布整行。实现：新增 `ToolbarIcons.kt`，五个 `Canvas` 手绘线性图标（symbols/palette/smiley/gear/chevron），不引入 material-icons-extended 依赖（那个库几千个图标只用五个太浪费，违背"依赖能少则少"）；`ToolbarRow` 改 `Arrangement.SpaceEvenly` 五等分，`ToolbarIcon` 去掉 `wide`/文字分支

**键位布局（符合实体键盘走向）**
- [x] 第二行 a-l 居中，左右各留约半键间隙，不贴边。实现：新增 `KeyAction.Spacer`（无背景无点击的占位键），两端各插 weight=0.5 的 spacer，9 键+1 总 weight=10 与首行对齐
- [x] 第三行字母同理错位排列；左端 `'` 分词键移到右端（m 与退格之间）。`⇧` 仍在最左、`⌫` 仍在最右，中间 `'` 挪到 m 和 `⌫` 之间
- [x] 底行「中文」空格键文字居中（原本就是 `Box(contentAlignment=Center)`，天然居中，无需改动）；**删除话筒键**（`KeyAction.Mic` 从布局与枚举中整体移除，空格 weight 从 2.5f 补到 3.4f 填满话筒键腾出的空间）

**Shift 与标点**
- [x] shift 激活态键帽变浅灰（单击=浅灰、锁定=常亮+下标），一眼可辨。实现：`JadeTheme.shiftOnce` 提供浅灰色；`JadeKey` 新增 `shiftLocked` 参数区分两种"开"状态，锁定态额外叠加 `TextDecoration.Underline`
- [x] 标点全半角跟模式走：英文态/数字态/shift 态一律半角（`.` `,`），仅中文态全角（`。` `，`）。实现：`CHINESE_PUNCTUATION` 转换分支加了 `page == Letters && shift == Off` 条件，数字页和 shift 激活态落到 else 分支走原始 ASCII
- [x] 单击 shift 时若有 composing 拼音串：按原字母直接转英文上屏（拼音转英文快捷路径）。`KeyAction.Shift` 分支里 composing 非空时调用 `flushComposingAsLiteral()` 而不是切换 shift 状态

**工程便利**
- [x] `AA无线装机.bat`：`gradlew assembleDebug` + 无线 adb（`adb pair`/`adb connect` 交互式引导）+ `adb install -r`，一键出包装机，不需数据线；沿用现有 `AA打包安装到手机.bat` 的 gradle/adb/JAVA_HOME 解析逻辑
- [x] 键盘核心组件挂 `@Preview`（各模式/主题参数化），Android Studio 里直接预览 UI 不用装机。实现：`ImeRoot` 新增 `initialMode`/`initialPage` 可选参数（默认值不变，仅供 Preview 用），文件末尾 4 个 `@Preview`（浅色中文/深色中文/浅色英文/浅色符号页）；`KeyboardPage`/`InputMode` 枚举从 `private` 改 `internal`（Preview 需要引用这两个类型）
- 验收：`./gradlew assembleDebug testDebugUnitTest` 均过（23 条单测全绿）；真机验收（布局对齐舒服、标点半全角正确、shift 状态可辨、bat 双击完成装机、Android Studio 里 Preview 面板能看到 4 张预览图）待有数据线/无线调试环境后进行

## M2 — 便利功能（**用户已点名开工：emoji 优先**）

- [x] Emoji 面板：分类 + 最近使用（搜索未做，PLAN 标注可选，先跳过）。实现：`emoji/EmojiCatalog.kt` 约 400 条精选 emoji（10 分类），`emoji/EmojiRepository.kt` SharedPreferences 存最近 40 条 MRU；`ime/ui/EmojiPanel.kt` 整版替换工具栏+键位区（同高度不改变 IME 窗口尺寸），顶部 tab 横滑切分类，`ToolbarRow` 的 emoji 入口从 Toast 占位改为真正开面板
- [x] 颜文字/自定义短语页，设置页可增删。实现：`phrase/KaomojiCatalog.kt` 内置颜文字（固定不可编辑）；`data/UserDataDatabase.kt` 新增 `custom_phrase` 表 + `PhraseRepository`，`SettingsActivity` 加“自定义短语”卡片（增/删，`OutlinedTextField`+列表）；颜文字/短语作为 EmojiPanel 的两个额外 tab，不占用 M1.7 刚固定的工具栏五格
- [x] 剪贴板：监听 + 历史面板（50条、固定、清空、敏感字段豁免）。实现：`data/UserDataDatabase.kt` 新增 `clipboard_entry` 表（`pinned`/`timestamp`）+ `ClipboardRepository`（去重置顶、超 50 条淘汰最旧未固定项、`清空`只清未固定）；`JadeImeService` 全程注册 `ClipboardManager.OnPrimaryClipChangedListener`（不只面板开着时），敏感字段命中时跳过记录；EmojiPanel 新增“剪贴板”tab，条目可点粘贴/★固定/✕删除
- [x] 空格滑动移动光标；数字/URL/密码 inputType 自动切布局。实现：空格键手写 `awaitEachGesture` 手势（`awaitFirstDown`+`withTimeoutOrNull` 判长按、按 touchSlop 的 3 倍步长触发一次光标移动，累计 delta 支持连续滑动）——tap/长按语音/滑动移动光标三选一在同一手势循环里判定，不用两个 `pointerInput` 互相抢事件；`ime/SensitiveInput.kt` 的 `deriveFieldConstraint()` 按 `EditorInfo.inputType` 分类，数字/电话/日期时间转英文+符号页，URL/密码/邮箱转英文，`JadeImeService.onStartInputView` 里派生并靠自增 `fieldGeneration` 触发 `ImeRoot` 里的 `LaunchedEffect` 重新应用（不锁死，用户仍可手动切回中文）
- [x] 密码字段 sensitive 标记（为 M3 铺垫）。`ime/SensitiveInput.kt` 的 `EditorInfo?.isSensitiveField()` 覆盖 password/visible password/web password/email/web email/number password 六种 variation，剪贴板监听已经在用，M3 的 `StatsCollector`/用户词库可直接复用不必重新判断
- 验收：`./gradlew assembleDebug testDebugUnitTest` 均过；真机验收（emoji/颜文字/短语/剪贴板四个 tab 好用、密码框自动转英文、数字框自动转数字符号页、空格滑动光标顺手）待有数据线/无线调试环境后进行

## M2.5 — 输入体验与候选质量（2026-08-01 用户反馈：输入不顺、候选不合意、键帽偏硬偏小）

- [x] **全局候选解码**：保留 `PinyinEngine` 边界，在 `engine/` 内用 12 路 Beam Search 取代最长匹配贪心；候选按完整消费长度和统一分数排序，整句候选不再无条件抢占第一
- [x] **会话级偏好与隐私收紧**：词频采用对数饱和增益，仅保留当前 IME 会话；不再从 Room 读写自动造词、拼音或词频明文，旧数据不擅自删除
- [x] **确认动作一致性**：中文 composing 下按标点先确认首选候选再提交标点；按 Done/回车时则将当前拼音原样上屏、不选首选候选且不执行 Editor action，只有没有拼音时才执行 Editor action/换行
- [x] **拼音中间编辑**：长按拼音回显可定位内部光标，随后输入字母/分隔符可在该位置插入，退格删除光标前的字母
- [x] **键帽手感第一轮**：默认键盘高 252dp → 268dp，默认间隙 4dp → 3dp，按下时键帽下沉 1dp 且阴影收缩；`keyboardHeightScale` 接入真实渲染
- [x] **全拼防简拼误解码**：Beam 裁枝改用与最终排序一致的平均分，并对句内简拼路径施加置信惩罚；完整拼音 `zheshouganzhenbucuo` 不再被拆成 `zh + e + sh + ougan + zh + en + bucuo`
- [x] **保守整句漏字纠错**：原串没有完整全拼路径时，尝试一次元音增删后走纯全拼 Beam；候选栏明确标「纠」，不静默上屏。覆盖 `wojedekeyi` → 「我觉得可以」，不让简拼回退出「我金额的可一」
- [x] **2026-08-11 P0 候选流水线**：候选解码 latest-only，旧 revision 在 Trie/Beam/纠错检查点可取消；排序热路径改用静态 entry-id，不做 SHA/十六进制构造；单字母简拼在进入排序前有界收集；刷新期间不展示旧候选
- [x] **2026-08-11 P0 候选分层与 v2 造词**：显式候选来源层级保护合法单音节精确项；新增 `candidate_memory_v2` 增量迁移和组件 ID 词记忆，覆盖 `叶`＋`瑄` → `叶瑄`/`yexuan`/`yx`，词库版本不匹配 fail closed；静态词库只读审计脚本位于 `tools/check_lexicon_facts.py`
- [ ] **候选延迟与后悔指标**：将候选计算迁出 Compose 主线程，记录无明文的候选计算耗时、首选确认、非首选选择和提交后快速退格计数
- [ ] **真机三轮验收**：聊天、搜索、长句输入各一轮；记录主观手感和上述聚合计数，按结果调 Beam 宽度/间隙/高度
- [ ] **后续滑动输入**：独立实现轨迹采样、键位命中概率和拼音路径解码；不与当前点按纠错共用状态机

验收：`./gradlew assembleDebug testDebugUnitTest` 通过；真机确认候选首选更符合预期、标点/回车不中断中文输入、键帽不再显得拥挤或生硬。

## M3 — 统计与上报 ✦ 与 assistant/Emerald 联动

上报架构已按三仓实际源码修订（见 DESIGN 3.7）：**IME 只推 Emerald-presence 一个目标**，assistant 无 HTTP 服务器、走文件拉取。

- [ ] `StatsCollector`：按 DESIGN 3.7 事件模型计数（严禁记录明文），Room 落盘
- [ ] 设置页统计看板（今日键数/字数/退格率/时长，简单列表即可）
- [ ] `DailyReporter`：WorkManager 每日 23:50 聚合，POST 单一 payload（schema 见 DESIGN 3.7）到 `{base}/sensor/ime`，Bearer token 鉴权；本地留 30 天，未确认日期随下次上报补传
- [ ] 设置页：base URL + token 配置、总开关（上报默认关）
- [ ] **Emerald-presence 侧**（D:\ai\Emerald-presence 施工）：`admin/routers/sensor.py` 新增 `POST /sensor/ime`（scope `sensor.write`，照抄 `/sensor/push` 模式）：① portrait 摘要写 user profile `ime_today`/`ime_log`（参照 `phone_sensor_today`/`phone_sensor_log`，log 留 30 条），供 prompt_builder 让角色感知；② 全量 payload 落盘 `data/ime_stats/{date}.json`（留 90 天）；复用现有敏感窗口关键词过滤思路
- [ ] **assistant 侧**（D:\ai\assistant 施工）：新增 `app/tracker/ime_stats.py` 的 `collect_ime_stats(day, path)` 读取 Emerald-presence 的 `data/ime_stats/{day}.json`（路径进 settings，同 `obsidian_vault_path` 模式，缺文件优雅降级）；daemon 汇入 summary `data['ime']`（与 PC 端 `keys` 分开，不混计）；`personal_report.py` 日报加"手机输入法"段落
- 验收：日报出现手机打字统计；角色对话中能自然提及当日输入画像

## M4 — 语音输入

- [ ] 集成 sherpa-onnx（官方 Android 包），streaming zipformer 中英双语 int8 模型打包 assets
- [ ] 语音入口 = **长按空格**（2026-07-18 定，键盘上无独立话筒键）：长按进入听写态（空格键变波形动画），再按停止；partial 实时显示，final commit
- [ ] 录音权限引导；模型懒加载 + 闲置 60s 释放
- [ ] 语音使用计入统计
- 验收：离线状态语音输入中文可用，延迟可接受

## M5 — 主题皮肤 ✦ mod 友好组件化（**用户已点名：紧随 M2 emoji 开工，先于 M3/M4**）

设计目标升级（2026-07-18）：**皮肤 = 纯数据文件，其他 agent 不碰 Kotlin 代码就能做美化 mod**。

- [x] **UI 组件化前置**：颜色/圆角/间距全部改成 `theme/JadeTheme.kt` 数据类的 token，不再有硬编码 `Color(0x...)`/`RoundedCornerShape(7.dp)`。实现方式是"参数线程 + CompositionLocal 双保险"而不是纯 CompositionLocal：`ImeRoot` 顶层用 `themeRepository.resolveActive()` 解出当前 `JadeTheme`，`CompositionLocalProvider(LocalJadeTheme provides theme)` 下发一份供以后新组件直接读，同时仍把 `theme` 当参数一路传给 `JadeKey`/`ToolbarRow`/`EmojiPanel` 等（组件树只有 2-3 层，参数传递比逐层读 CompositionLocal 更直白，PLAN 原文想要的"不碰 Kotlin 就能换色"这个目标两种写法都满足）。删除了旧的 `ime/ui/KeyboardPalette.kt`
- [x] Snab 主题引擎：JSON schema（DESIGN 3.5 字段 + PLAN 扩展 `bgImage`/`bgBlur`(0-25)/`bgDim`，`toolbar/candidates/keys/panel` 分区域覆写字段已解析）。`theme/SnabThemeJson.kt` 用 kotlinx.serialization 解析、颜色字符串走 `Color.parseColor`；**老实标注**：目前只有 `keys` 区域覆写真正接入渲染，`toolbar`/`candidates`/`panel`/`keyTextSecondary`/`candidateText`/`fontScale`/`keySound`/`keyboardHeightScale` 解析但未接渲染管线，`docs/theming.md` 里列了这份缺口清单，不是当能用的功能收尾
- [x] 布局也 JSON 化：`layout/LayoutJson.kt` + `layout/LayoutRepository.kt`，`filesDir/layouts/letters.json`/`symbols.json` 存在则覆写对应页的行列/键宽权重(`weight`)/label/value/corner/action，字段直接对应 `KeySpec`/`KeyAction`；不认识的 `action` 值退化成 `Text` 而不是整份失败。**局限**：只能重新摆放/改宽既有 `KeyAction` 语义的键，不能定义新行为；且不是热切换，要重新弹出键盘窗口才生效（主题是热切换，布局不是，这点在 docs 里写清楚了）
- [x] 内置 4 主题（亮/暗/护眼绿/粉，`theme/BuiltInThemes.kt`）；「主题与皮肤」独立页提供横向色板选择 + 选中态描边 + 「跟随系统」选项，选中通过 `ThemeRepository.selectedId`（`StateFlow`）即时推给正在开着的键盘窗口，不用重开；「导入主题 JSON」按钮走系统文件选择器拷贝进 `filesDir/themes/`。普通设置和主题页共享持久化日/夜外观开关。**没做**：简易可视化编辑器（PLAN 提到但这轮没开工，目前改主题只能手写/编辑 JSON 文件）
- [x] `docs/theming.md`：主题 schema 表格 + 完整示例主题（墨玉/Tokyo Night 配色）+ 布局 schema 表格 + 已知缺口清单，写给不碰 Kotlin 的人/agent 看
- 验收：`./gradlew assembleDebug testDebugUnitTest` 均过；真机验收（4 内置主题切换即时生效、背景图+模糊+蒙层效果、导入一份手写主题 JSON、布局 JSON 覆写重新摆键位）待有数据线/无线调试环境后进行

## M6 — PC 后端联动（可选增强）

- [ ] `server/asr_server.py`：sherpa-onnx 或 FunASR，WebSocket 收 16kHz PCM 流，回 `{"type":"partial|final","text":...}`
- [ ] app 侧 `RemoteAsr`：设置页配地址，语音模式本地/远程切换，断连自动回落本地
- [ ] （可选）远程整句润色：候选栏"✨"键把当前拼音串/句子发 PC 由 LLM 重排——留到用了再说
- 验收：局域网内远程识别准确率明显高于本地

## M-later 池（不排期）

9键 T9、双拼、模糊音、英文补全、Viterbi 整句/librime 升级、手写、一键把当前想说的话发给角色（"传音"键）、统计看板图表化、主题商店式分享。

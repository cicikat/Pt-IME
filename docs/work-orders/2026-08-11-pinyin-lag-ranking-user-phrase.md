# 工单：拼音卡顿、候选分层与用户造词修复

- 日期：2026-08-11
- 状态：代码施工完成，自动化通过，真机验收待用户
- 优先级：P0
- 仓库：`D:\ai\IME`
- 目标模块：`engine/`、`data/`、`ime/ui/`、`tools/`
- 性质：上游引擎与候选流水线修复，不是 UI 调分

## 1. 用户反馈与已确认现象

### 1.1 中文输入新增明显停顿

上次加入持久候选记忆后，连续输入中文出现“一顿一顿”的体感。静态代码审计确认当前每次按键都会向单线程候选队列追加一次完整解码；旧 revision 虽然不会回写 UI，但仍会把 Trie 查询、Beam、纠错、排序和记忆哈希全部跑完。

当前记忆查询又位于排序 comparator 内。首次遇到一个候选时会执行 SHA-256，并逐字节格式化为十六进制字符串。单字母简拼可能产生数百个候选，例如当前词库中 `initials = x` 有 543 条，容易造成 CPU 峰值、队列积压和常驻缓存增长。

### 1.2 `xuan` 候选顺序错误

当前静态词库中 `pinyin_key = xuan` 的前列为：

`选、玄、宣、旋、轩、悬、喧、璇、眩、炫、萱……`

实机截图却出现了：

`谖、铉、弦、许按、续按、需按、须按、虚按……`

其中“许按、续按、需按”等来自把合法单音节 `xuan` 错拆为 `xu + an`。当前置信度规则只保护“完整多字词条”，未保护合法单音节的精确单字，因此假整句可能压过正确同音字。

“弦”不是 `xuan` 精确候选。当前 UI 在新 revision 计算期间保留旧候选文字，但没有变灰或加载提示，可能造成拼音块已显示 `xuan`、候选仍来自旧输入的视觉错配。

### 1.3 `叶瑄` 无法学习

当前词库包含：

- `叶 -> ye`
- `瑄 -> xuan`

但不包含：

- `叶瑄 -> yexuan`
- `叶瑄 -> yx`

当前实现只记住单个已存在候选的哈希，不会把一次输入事务中连续选择的“叶”与“瑄”合成为用户词。因此重复输入不会生成“叶瑄”，简拼 `yx` 也只能返回静态词库中的“一些、影响、游戏、一下、也许……”等词。

## 2. 施工目标

1. 连续快速输入时只计算最新 composing revision，过期解码不得继续占用候选队列。
2. 候选刷新期间不得把旧候选伪装成当前拼音的结果。
3. 合法单音节精确候选必须排在任何拆分句之前。
4. 保留既有 `shihuai -> 释怀/使坏`、整句 Beam 和保守纠错回归能力。
5. 一次完整输入中依次选择“叶”“瑄”后，立即学成用户词“叶瑄”。
6. 学成后：`yexuan` 第一候选为“叶瑄”；`yx` 中“叶瑄”位于第二候选。
7. 用户词记忆跨输入会话和进程重启保留，同时不落盘用户输入或候选明文。

## 3. 强制边界

### 3.1 隐私红线

- 任何日志、Room 表、偏好文件都不得保存用户输入、候选词或完整用户词明文。
- password、邮箱及其他敏感 `inputType` 不统计、不学习、不写候选记忆。
- 性能日志只允许记录 revision、候选数、阶段耗时、是否取消；不得记录拼音或候选内容。

### 3.2 数据保护

- 现有 `candidate_memory`、`learned_word`、`user_freq` 表不得删除或清空。
- 旧 SHA-256 `candidate_memory` 停止参与排序，但保留原表不读不写。
- 新增 v2 记忆表必须使用 Room 增量迁移；禁止 destructive migration 处理本次升级。
- 不迁移、不删除真实剪贴板、短语或其他 userdata。

### 3.3 架构与范围

- UI 仍只依赖 `PinyinEngine` 接口，不得耦合 Trie 或 Room。
- 不接入 librime、云端模型、网络词库或新的第三方依赖。
- 不在本工单替换 rime-ice 词库，也不扩展语音、统计、皮肤等模块。
- 不以全局调大/调小一个 score 常量代替候选来源分层。

## 4. 施工顺序

严格按以下顺序施工。每完成一个施工阶段（A/B/C/D），执行一次 `testDebugUnitTest` 和 `assembleDebug`；若同步到 `PLAN.md` 的独立勾选项，则仍按项目约定逐项构建。失败时先修复，不得带红进入下一阶段。

### A. P0：候选流水线去积压

- [x] 将候选刷新改为 latest-only：新 composing revision 到来时，取消尚未开始的旧任务；正在执行的旧任务必须能在 Trie、Beam、纠错等有界检查点尽快退出。
- [x] 引擎解码不得依赖 UI 线程；候选状态回写仍只允许发生在当前 revision 与当前 raw pinyin 同时匹配时。
- [x] 移除 comparator 内的 SHA-256、`String.format` 和其他候选身份构造；排序 comparator 必须是无分配或近似无分配的纯字段比较。
- [x] 合并同一次 `input()` 内重复的完整路径检查与 Beam 结果，禁止为了 `hasCompleteFullPinyinPath` 对同一输入重复解码。
- [x] 在进入全量排序前按候选来源做有界收集；不得因为一个单字母简拼对数百条无关候选全部计算持久记忆键。
- [x] 候选未 ready 时清空旧候选，或明确降低透明度并显示加载态；不得继续以正常颜色展示旧 revision。

本项验收：

- 快速连续输入 `xuan` 时，`x`、`xu`、`xua` 的过期任务不会阻塞最终 `xuan`。
- 使用只记录耗时的调试日志，在用户真机上连续输入固定脚本 20 次：候选解码 P95 不超过 32 ms，单次最大值不超过 80 ms，且无可见队列追赶。
- 候选文字与 composing 拼音不存在跨 revision 错配。

### B. P0：候选来源分层

- [x] 为候选建立显式来源/置信度类型，至少区分：单音节精确、完整多字词、纯全拼整句、保守纠错、全拼前缀、简拼、用户词。
- [x] 使用合法拼音音节判断：当完整输入本身是合法单音节且存在精确词条时，禁止 `xu + an` 一类拆分句进入精确候选之前。
- [x] `xuan` 默认前六位固定回归为：`选、玄、宣、旋、轩、悬`。
- [x] `许按、续按、需按` 等拆分句不得出现在 `xuan` 首屏候选中。
- [x] 保持 `shihuai` 默认前两位为 `释怀、使坏`，并保持完整词条优先于 `是坏`。
- [x] 保持 `zheshouganzhenbucuo -> 这手感真不错`、`wojedekeyi -> 纠 我觉得可以` 等已有回归。

本项验收：新增真实词库审计脚本或等价只读检查，验证 `lexicon.db` 中 `xuan/yexuan/yx` 的事实；新增 JVM 固定 fixture 回归覆盖上述顺序，不依赖真机肉眼猜测。

### C. P0：低成本、可造词的 v2 记忆

- [x] `LexiconEntry` 保留静态词库行 ID；普通静态候选使用该 ID 参与内存记忆查询，不在候选热路径计算哈希。
- [x] 为一次 composing 保存原始拼音事务与用户依次选择的静态词条 ID；只有拼音被完整消费并成功 commit 后才能完成造词。
- [x] 连续选择至少两个组件时生成用户组合词；从组件恢复 word、canonical pinyin 与 initials，不保存三者明文。
- [x] 新增 `candidate_memory_v2`/`learned_phrase_v2` 或等价表，保存静态词条 ID 或组件 ID 序列、次数、最近使用逻辑时钟和词库版本指纹。
- [x] 词库版本不匹配时 fail closed：忽略无法安全恢复的 v2 行，不猜测、不错误映射，也不删除原记录。
- [x] 全拼精确命中：选择一次后立即升至第一。
- [x] 简拼精确命中：保留默认第一候选，把最近记忆用户词固定在第二；若用户词本来已是第一则不重复。
- [x] 输入事务被 reset、切换语言、提交原始字母、取消或进入敏感字段时，丢弃未完成的造词链。
- [x] 持久写入失败不得阻塞候选/UI，不得让 IME 崩溃；同一候选的异步旧写入不得覆盖新次数和新时钟。

`叶瑄` 硬回归：

1. 初始词库中无“叶瑄”。
2. 输入 `yexuan`，依次选择“叶”“瑄”。
3. 无需第二次选择，当前进程立刻得到完整用户词。
4. 再输入 `yexuan`，“叶瑄”为第一候选。
5. 输入 `yx`，“叶瑄”为第二候选。
6. 重建引擎/模拟进程重启后，第 4、5 步仍成立。
7. 在敏感字段重复同样步骤，不产生任何 v2 记忆行。

### D. 清理与文档同步

- [x] 更新 `DESIGN.md`：候选来源分层、latest-only 解码、v2 组件 ID 造词和旧表兼容策略。
- [x] 更新 `PLAN.md`：JVM 与 debug 构建通过后勾选代码项；静态词库检查脚本已加入，真机验收保持单独未完成状态。
- [x] 删除或改写已经与新语义冲突的旧测试，例如“永不自动造词”；不得为了让测试通过而保留错误产品行为。
- [x] 检查只修改本工单涉及文件，不提交或覆盖仓库中其他并行改动。

## 5. 自动化验证清单

至少覆盖：

- `xuan` 精确单音节优先，前六位固定。
- `xuan` 的 `xu + an` 拆分不进首屏。
- 新 revision 使旧 revision 终止且不能回写。
- 排序热路径不调用 SHA/hex formatter。
- `shihuai`、长句 Beam、漏元音纠错既有回归。
- 选一次后全拼第一、简拼第二。
- `叶 + 瑄` 生成 `叶瑄/yexuan/yx`。
- v2 记忆重建引擎后仍生效。
- password/邮箱字段不学习。
- Room 旧版本到新版本迁移保留既有表与数据。

推荐验证命令：

```powershell
$env:JAVA_HOME='D:\soft3\AndroidStudio\jbr'
& 'C:\Users\10434\.gradle\wrapper\dists\gradle-8.12-all\ejduaidbjup3bmmkhw3rie4zb\gradle-8.12\bin\gradle.bat' testDebugUnitTest --no-daemon --console=plain
& 'C:\Users\10434\.gradle\wrapper\dists\gradle-8.12-all\ejduaidbjup3bmmkhw3rie4zb\gradle-8.12\bin\gradle.bat' assembleDebug --no-daemon --console=plain
```

## 6. 真机验收

自动化与 APK 构建完成不代表本工单完成。必须在用户自己的手机上安装后由用户验收：

1. 快速连续输入一段中文，不再出现按键后候选分批追赶或“一顿一顿”。
2. 展开候选并快速输入 `xuan`，拼音块与候选始终属于同一 revision。
3. `xuan` 首屏以正常同音字为主，不出现“许按、续按、需按”抢位。
4. 完成一次“叶”＋“瑄”的选择后，立即验证 `yexuan` 第一、`yx` 第二。
5. 关闭并重新打开输入法进程后再次验证记忆仍在。
6. 在密码和邮箱字段验证不会产生学习副作用。

只有自动化检查、debug APK 构建、真机流畅度和上述固定用例全部通过，且用户明确说可以，才可把工单状态改为完成。

## 7. 预期改动文件

预计涉及但不限于：

- `app/src/main/java/com/chacha/jadeime/engine/PinyinEngine.kt`
- `app/src/main/java/com/chacha/jadeime/engine/TrieLexiconEngine.kt`
- `app/src/main/java/com/chacha/jadeime/data/LexiconDatabase.kt`
- `app/src/main/java/com/chacha/jadeime/data/PinyinRepository.kt`
- `app/src/main/java/com/chacha/jadeime/data/UserDataDatabase.kt`
- `app/src/main/java/com/chacha/jadeime/ime/ui/ImeRoot.kt`
- `app/src/test/java/com/chacha/jadeime/engine/TrieLexiconEngineTest.kt`
- 必要的 data/IME UI 测试
- `tools/` 下只读词库审计脚本
- `DESIGN.md`
- `PLAN.md`

若施工中发现必须修改上述范围外的业务模块，应先停止并说明原因，不得静默扩大范围。

# 皮肤 / 布局 JSON 化（Snab 主题引擎）

给不碰 Kotlin 代码的 agent/用户看的：怎么做一套新皮肤、怎么改键位布局。对应 PLAN.md M5、
DESIGN.md 3.5。

## 装到手机上

1. 把 JSON 文件放进：
   - 主题：`filesDir/themes/<你的名字>.json`
   - 布局：`filesDir/layouts/letters.json`（字母页）/ `filesDir/layouts/numeric.json`（九宫数字页）/
     `filesDir/layouts/symbols.json`（横排符号页）
2. 两种装法：
   - 设置页 →「主题」卡片 →「导入主题 JSON」，选文件，自动拷到 `filesDir/themes/`。布局
     JSON 目前没有导入按钮（还没做设置页 UI），开发调试用 `adb push` 最方便：
     ```
     adb push my_theme.json /data/data/com.chacha.jadeime/files/themes/
     adb push letters.json /data/data/com.chacha.jadeime/files/layouts/
     ```
     （debug 包才能这样直接 push；release 签名包需要走应用内导入。）
3. 主题选好后立即生效，不用重开键盘（`ThemeRepository.selectedId` 是 StateFlow，正在开着的
   键盘窗口会自动重组）。布局 JSON 目前是**每次打开键盘窗口时读一次**，改完文件要重新弹出
   键盘（切到另一个 app 的输入框再切回来）才会生效，还没做到主题那样的热切换。

## 主题 JSON schema

字段名对应 `SnabThemeJson`（`app/src/main/java/com/chacha/jadeime/theme/SnabThemeJson.kt`），
和 DESIGN.md 3.5 的示例一致：

```json
{
  "name": "墨玉",
  "isDark": true,
  "keyboardBg": "#1A1B26",
  "keyBg": "#24283B",
  "keyBgPressed": "#414868",
  "keyText": "#C0CAF5",
  "keyTextSecondary": "#565F89",
  "accent": "#7AA2F7",
  "candidateText": "#C0CAF5",
  "keyCornerRadius": 8,
  "keyGap": 4,
  "fontScale": 1.0,
  "bgImage": null,
  "bgBlur": 0,
  "bgDim": 0.0,
  "keySound": null,
  "keyboardHeightScale": 1.0,
  "keys": { "bg": "#24283B", "text": "#C0CAF5" }
}
```

| 字段 | 类型 | 说明 |
|---|---|---|
| `name` | string | 显示在设置页主题列表里的名字 |
| `isDark` | bool | 影响解析失败时的兜底色（亮色兜底 vs 暗色兜底），不影响别的逻辑 |
| `keyboardBg` | 颜色 | 键盘整体背景（无 `bgImage` 时生效） |
| `keyBg` / `keyBgPressed` | 颜色 | 普通键的默认色 / 按下色 |
| `keyText` | 颜色 | 键帽文字颜色 |
| `keyTextSecondary` | 颜色 | 目前未接入渲染，解析但不使用（诚实标注，别当成能用的字段） |
| `accent` | 颜色 | 高亮色：shift 锁定态、候选栏选中态等 |
| `candidateText` | 颜色 | 目前未接入渲染 |
| `keyCornerRadius` | 整数 0-24 | 键帽圆角，单位 dp |
| `keyGap` | 整数 0-16 | 键与键、行与行的间距，单位 dp |
| `fontScale` | 浮点 | 目前解析但未接入渲染 |
| `bgImage` | string / null | 背景图**绝对路径**（如 `/data/data/com.chacha.jadeime/files/themes/bg.jpg`），设为 null 用纯色 `keyboardBg` |
| `bgBlur` | 整数 0-25 | 背景图模糊半径，单位 dp。**Android 12（API 31）以下没有效果**（`Modifier.blur` 依赖 `RenderEffect`，minSdk 29 的机器会看到清晰图，不会崩） |
| `bgDim` | 浮点 0-1 | 背景图上叠的黑色蒙层透明度，越大越暗，保证文字可读 |
| `keySound` | string / null | 目前解析但未接入（按键音效是 M-later 池的事） |
| `keyboardHeightScale` | 浮点 | 目前解析但未接入 |
| `toolbar` / `candidates` / `keys` / `panel` | `{ "bg": 颜色?, "text": 颜色? }` | 分区域覆写。**只有 `keys` 生效**（覆盖键帽默认背景色 `keySpecial`、键帽文字色），`toolbar`/`candidates`/`panel` 目前只解析、不渲染 |

颜色格式：`#RRGGBB` 或 `#AARRGGBB`，就是 Android `Color.parseColor` 认的格式。写错格式（比如
拼错的字符串）不会崩，会静默退回该字段的默认色，`adb logcat` 里能看到一行
`SnabThemeJson: invalid color literal ...` 方便排查。

字段全部可省略（除了 `name`/`keyboardBg`/`keyBg`/`keyBgPressed`/`keyText`/`accent` 这几个没有
默认值的必填项），省略的可选字段走上表的默认值。

## 布局 JSON schema

字段对应 `KeySpecJson`（`app/src/main/java/com/chacha/jadeime/layout/LayoutJson.kt`），文件整体是
"行的数组"，每行是"键的数组"：

```json
[
  [
    { "label": "q", "action": "Text", "value": "q", "corner": "1" },
    { "label": "w", "action": "Text", "value": "w", "corner": "2" }
  ],
  [
    { "label": "⇧", "action": "Shift", "weight": 1.35 },
    { "label": "", "action": "Spacer", "weight": 0.5 }
  ]
]
```

每个键对象：

| 字段 | 类型 | 说明 |
|---|---|---|
| `label` | string | 键帽显示文字 |
| `action` | string | 见下方 `KeyAction` 取值；写错/不认识的值会退化成 `"Text"`，不会让整个布局解析失败 |
| `value` | string? | `action` 为 `"Text"` 时，实际输入/上屏的字符；省略则用 `label` |
| `weight` | number | 键宽权重，同一行内按比例分配；默认 `1.0` |
| `showsPopup` | bool? | 按下时是否弹放大气泡；省略时 `Text` 键默认 `true`，其它默认 `false` |
| `corner` | string? | 长按弹出/直接上屏的角标字符，省略则没有角标 |

`action` 可选值（区分大小写，照抄）：
`Text` `Shift` `Backspace` `Space` `Enter` `Symbols` `Numeric` `Letters` `LangToggle` `Separator` `Spacer`

**没做的部分，别当成 bug 去找**：改布局 JSON 不会改变按键的*行为*逻辑（比如 `Space`
键在中文态选中候选、`Separator` 键插入拼音分隔符这些逻辑是写死在 `ImeRoot.kt` 里的，JSON
只能调整这些键的位置/宽度/label/corner，不能凭空发明新行为）。也没有校验"一份可用的键盘至少
要有几个 Space/Enter 键"，写一份缺胳膊少腿的布局 JSON 会得到一份缺胳膊少腿但不崩溃的键盘。

## 完整示例主题

保存为 `filesDir/themes/moyu.json`（对应上面 DESIGN.md 3.5 的"墨玉"配色，Tokyo Night 风格）：

```json
{
  "name": "墨玉",
  "isDark": true,
  "keyboardBg": "#1A1B26",
  "keyBg": "#24283B",
  "keyBgPressed": "#414868",
  "keyText": "#C0CAF5",
  "accent": "#7AA2F7",
  "keyCornerRadius": 10,
  "keyGap": 5
}
```

## 已知缺口（如实标注，下一轮 M5 再补）

- `toolbar`/`candidates`/`panel` 区域覆写只解析不生效
- `keyTextSecondary`/`candidateText`/`fontScale`/`keySound`/`keyboardHeightScale` 只解析不生效
- 布局 JSON 不支持热切换（需重新弹出键盘窗口）、没有设置页导入入口（只能 `adb push` 或手动放文件）
- 基础编辑器已提供背景、裁剪、按键透明度和字体；其他高级样式仍使用主题包。

## 基础美化（2026-09-11）

“主题与皮肤”顶部显示键盘预览，下方依次为配色、背景、按键、字体与主题包。

- 背景支持系统 ImageDecoder 能解码的静态图片、GIF、动态 WebP，导入上限 32 MB。拖动/双指或滑块缩放，保存可见裁剪区域。位置使用归一化坐标，图像始终铺满视口；渲染裁剪在键盘边界内，保留原动图。静态图解码长边最多 1600，动图最多 1024，隐藏键盘时停止动画。
- 按键不透明度只改变按键底色，不影响文字。背景压暗有独立滑块。调整自动保存并同步到键盘，背景裁剪使用显式保存。
- 支持 TTF/OTF 字体导入、选择，提供跟随主题和系统字体。文件在后台复制、验证和加载，失败时回退系统字体。
- 自定义外观作为本机覆盖项，切换配色不会丢失；“恢复主题背景”移除背景覆盖。基础外观覆盖目前不包含在主题包导出中。
- 本机根目录可选的 `zpix.ttf` 自动加入 Debug 构建的字体选项；Release 不捆绑个人字体文件。Zpix 作者许可说明见 https://github.com/SolidZORO/zpix-pixel-font ，本地原文件不作修改。

参考：FlorisBoard 的主题资源分为图片、字体和样式（https://docs.florisboard.org/themes/assets）；本项目使用 Android ImageDecoder / AnimatedImageDrawable，无新增图片加载依赖。

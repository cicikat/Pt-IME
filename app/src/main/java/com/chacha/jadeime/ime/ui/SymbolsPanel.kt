package com.chacha.jadeime.ime.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chacha.jadeime.theme.JadeTheme

internal data class SymbolCategory(val label: String, val values: List<String>)

internal object SymbolCatalog {
    private fun s(v: String) = v.split(" ")
    val categories = listOf(
        SymbolCategory("常用", s("！ ？ ， 。 、 ： ； … —— · ~ @ # & * + - = / %")),
        SymbolCategory("中文", s("， 。 、 ； ： ！ ？ …… —— 「 」 『 』 （ ） 【 】 《 》 〈 〉 〔 〕 〖 〗 〘 〙 〚 〛 ￥ ※ 〒")),
        SymbolCategory("英文", s("! ? , . ; : ' \" ` ~ @ # $ % ^ & * _ - + = / \\ | < > ( ) [ ] { }")),
        SymbolCategory("数学", s("＋ － × ÷ ± ＝ ≠ ≈ ≤ ≥ ∑ ∏ √ ∞ ∫ ∂ ∆ ∇ π α β γ θ µ ° ′ ″")),
        SymbolCategory("货币", s("￥ $ € £ ¥ ₹ ₽ ₩ ₺ ฿ ₫ ₴ ₦ ₡ ₲ ₵ ₭ ₮ ₱ ₪ ₠")),
        SymbolCategory("箭头", s("← → ↑ ↓ ↖ ↗ ↘ ↙ ↔ ↕ ⇐ ⇒ ⇑ ⇓ ⇔ ➜ ➤ ➡ ⬅ ⬆ ⬇")),
        SymbolCategory("括号", s("( ) [ ] { } ＜ ＞ 〈 〉 《 》 「 」 『 』 【 】 〖 〗 〘 〙 ⟨ ⟩")),
        SymbolCategory("单位", s("℃ ℉ ° ‰ ‱ № ℡ ™ © ® ℅ ℓ ㎜ ㎝ ㎞ ㎡ ㏄")),
        SymbolCategory("星标", s("★ ☆ ✦ ✧ ✨ ✩ ✪ ✫ ✬ ✭ ✮ ✯ ✰ ✓ ✔ ✕ ✖ ✗ ✘ ❤ ♥ ♡ ☀ ☁ ☂ ☃")),
    )
}

@Composable
internal fun SymbolsPanel(theme: JadeTheme, onPick: (String) -> Unit, onDelete: () -> Unit, onClose: () -> Unit, modifier: Modifier = Modifier, repository: com.chacha.jadeime.ime.SymbolRepository? = null) {
    var selected by rememberSaveable { mutableIntStateOf(0) }
    val recent = remember(repository) { repository?.recent().orEmpty() }
    val categories = remember(recent) {
        listOf(SymbolCategory("常用", (recent + SymbolCatalog.categories.first().values).distinct().take(40))) + SymbolCatalog.categories.drop(1)
    }
    Column(modifier.fillMaxWidth()) {
        LazyRow(Modifier.fillMaxWidth().height(36.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), contentPadding = PaddingValues(horizontal = 6.dp)) {
            items(categories.size) { i ->
                Box(Modifier.background(if (i == selected) theme.keyActive else Color.Transparent, RoundedCornerShape(14.dp)).pointerInput(i) { detectTapGestures { selected = i } }.padding(horizontal = 12.dp, vertical = 7.dp)) { Text(categories[i].label, color = theme.text, fontSize = 13.sp) }
            }
        }
        LazyVerticalGrid(columns = GridCells.Fixed(8), modifier = Modifier.fillMaxWidth().weight(1f), contentPadding = PaddingValues(6.dp), horizontalArrangement = Arrangement.spacedBy(2.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            items(categories[selected].values, key = { it }) { value -> Box(Modifier.fillMaxWidth().height(38.dp).pointerInput(value) { detectTapGestures { repository?.record(value); onPick(value) } }, contentAlignment = Alignment.Center) { Text(value, color = theme.text, fontSize = 20.sp) } }
        }
        PanelActionRow(theme, onClose, onDelete)
    }
}

@Composable
internal fun PanelActionRow(theme: JadeTheme, onClose: () -> Unit, onDelete: () -> Unit) {
    Row(Modifier.fillMaxWidth().height(44.dp).padding(horizontal = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Spacer(Modifier.weight(1f))
        JadeKey(KeySpec("⌫", KeyAction.Backspace), "⌫", false, theme, Modifier.width(64.dp), onDelete, null, null, {})
    }
}

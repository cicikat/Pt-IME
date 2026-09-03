package com.chacha.jadeime.ime.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chacha.jadeime.data.ClipboardEntryRow
import com.chacha.jadeime.emoji.EmojiCategory
import com.chacha.jadeime.theme.JadeTheme

private const val RECENT_TAB_INDEX = 0

/**
 * Full-height emoji/kaomoji/phrase picker (PLAN M2 "Emoji 面板：分类 + 最近使用" and
 * "颜文字/自定义短语页"), swapped in for the toolbar+key rows while open -- same
 * footprint as the rest of the keyboard, so the IME window never resizes when this
 * opens or closes. Kaomoji and custom phrases ride along as two extra tabs rather
 * than a second panel/toolbar entry -- M1.7 just fixed the toolbar at five icons.
 */
@Composable
internal fun EmojiPanel(
    categories: List<EmojiCategory>,
    recent: List<String>,
    kaomojis: List<String>,
    phrases: List<String>,
    clipboardEntries: List<ClipboardEntryRow>,
    theme: JadeTheme,
    onPick: (item: String, isEmoji: Boolean) -> Unit,
    onPasteClipboard: (String) -> Unit,
    onToggleClipboardPin: (ClipboardEntryRow) -> Unit,
    onDeleteClipboardEntry: (ClipboardEntryRow) -> Unit,
    onClearClipboard: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectedTab by rememberSaveable { mutableIntStateOf(RECENT_TAB_INDEX) }
    val kaomojiTab = 1 + categories.size
    val phraseTab = kaomojiTab + 1
    val clipboardTab = phraseTab + 1
    val isEmojiTab = selectedTab != kaomojiTab && selectedTab != phraseTab && selectedTab != clipboardTab
    val tabLabels = remember(categories) {
        listOf("最近") + categories.map { it.label } + listOf("颜文字", "短语", "剪贴板")
    }
    val items = when (selectedTab) {
        RECENT_TAB_INDEX -> recent
        kaomojiTab -> kaomojis
        phraseTab -> phrases
        clipboardTab -> emptyList() // clipboard has its own row model, rendered separately below
        else -> categories.getOrNull(selectedTab - 1)?.emojis.orEmpty()
    }
    val emptyMessage = when (selectedTab) {
        RECENT_TAB_INDEX -> "暂无最近使用"
        phraseTab -> "暂无短语，去设置里添加"
        clipboardTab -> "暂无剪贴板历史"
        else -> ""
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().height(36.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LazyRow(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                contentPadding = PaddingValues(horizontal = 6.dp),
            ) {
                items(tabLabels.size) { index ->
                    EmojiTab(
                        label = tabLabels[index],
                        selected = index == selectedTab,
                        theme = theme,
                        onClick = { selectedTab = index },
                    )
                }
            }
            if (selectedTab == clipboardTab && clipboardEntries.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .pointerInput(onClearClipboard) {
                            detectTapGestures(onTap = { onClearClipboard() })
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text("清空", fontSize = 12.sp, color = theme.text.copy(alpha = 0.7f), modifier = Modifier.padding(horizontal = 8.dp))
                }
            }
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .pointerInput(onClose) { detectTapGestures(onTap = { onClose() }) },
                contentAlignment = Alignment.Center,
            ) {
                IconKeyboardSwitch(theme.text, Modifier.size(18.dp))
            }
        }
        if (selectedTab == clipboardTab && clipboardEntries.isNotEmpty()) {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                items(clipboardEntries, key = { it.id }) { entry ->
                    ClipboardRow(
                        entry = entry,
                        theme = theme,
                        onPaste = { onPasteClipboard(entry.content) },
                        onTogglePin = { onToggleClipboardPin(entry) },
                        onDelete = { onDeleteClipboardEntry(entry) },
                    )
                }
            }
        } else if (items.isEmpty()) {
            Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                Text(text = emptyMessage, fontSize = 13.sp, color = theme.text.copy(alpha = 0.5f))
            }
        } else if (selectedTab == phraseTab) {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                items(items) { phrase ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .pointerInput(phrase) {
                                detectTapGestures(onTap = { onPick(phrase, false) })
                            },
                    ) {
                        Text(
                            text = phrase,
                            fontSize = 16.sp,
                            color = theme.text,
                            maxLines = 1,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 10.dp),
                        )
                    }
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(if (selectedTab == kaomojiTab) 3 else 7),
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                items(items) { item ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(38.dp)
                            .pointerInput(item) {
                                detectTapGestures(onTap = { onPick(item, isEmojiTab) })
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = item,
                            fontSize = if (selectedTab == kaomojiTab) 13.sp else 22.sp,
                            color = theme.text,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ClipboardRow(
    entry: ClipboardEntryRow,
    theme: JadeTheme,
    onPaste: () -> Unit,
    onTogglePin: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(entry.id) { detectTapGestures(onTap = { onPaste() }) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = entry.content,
            fontSize = 15.sp,
            color = theme.text,
            maxLines = 1,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 6.dp, vertical = 8.dp),
        )
        Box(
            modifier = Modifier
                .size(30.dp)
                .pointerInput(onTogglePin) { detectTapGestures(onTap = { onTogglePin() }) },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                if (entry.pinned) "★" else "☆",
                fontSize = 15.sp,
                color = if (entry.pinned) theme.text else theme.text.copy(alpha = 0.35f),
            )
        }
        Box(
            modifier = Modifier
                .size(30.dp)
                .pointerInput(onDelete) { detectTapGestures(onTap = { onDelete() }) },
            contentAlignment = Alignment.Center,
        ) {
            Text("✕", fontSize = 13.sp, color = theme.text.copy(alpha = 0.5f))
        }
    }
}

@Composable
private fun EmojiTab(
    label: String,
    selected: Boolean,
    theme: JadeTheme,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxHeight()
            .pointerInput(onClick) { detectTapGestures(onTap = { onClick() }) },
        color = if (selected) theme.keyActive else Color.Transparent,
        shape = RoundedCornerShape(14.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = label,
                fontSize = 13.sp,
                color = theme.text,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
    }
}

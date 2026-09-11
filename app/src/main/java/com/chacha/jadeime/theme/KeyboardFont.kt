package com.chacha.jadeime.theme

import android.graphics.Typeface
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun rememberKeyboardFont(path: String?): FontFamily {
    val context = LocalContext.current.applicationContext
    var family by remember(path) { mutableStateOf<FontFamily>(FontFamily.Default) }
    LaunchedEffect(path) {
        family = withContext(Dispatchers.IO) {
            runCatching {
                when {
                    path.isNullOrEmpty() -> FontFamily.Default
                    path.startsWith("asset:") -> FontFamily(Typeface.createFromAsset(context.assets, path.removePrefix("asset:")))
                    else -> FontFamily(Typeface.createFromFile(path))
                }
            }.getOrDefault(FontFamily.Default)
        }
    }
    return family
}

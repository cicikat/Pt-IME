package com.chacha.jadeime.layout

import android.content.Context
import android.util.Log
import com.chacha.jadeime.ime.ui.KeySpec
import com.chacha.jadeime.ime.ui.KeyboardLayouts
import java.io.File
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

private const val TAG = "LayoutRepository"
private const val LAYOUTS_DIR = "layouts"

/**
 * `filesDir/layouts/letters.json` / `numeric.json` / `symbols.json` override the built-in
 * [KeyboardLayouts] rows when present (PLAN M5 layout JSON-ization). No file ->
 * default Kotlin layout; a malformed file logs and also falls back to
 * default -- a broken mod layout file degrades to "unchanged", not "keyboard won't draw".
 */
class LayoutRepository(context: Context) {
    private val dir = File(context.applicationContext.filesDir, LAYOUTS_DIR)
    private val json = Json { ignoreUnknownKeys = true }

    internal fun loadLetters(): List<List<KeySpec>> = loadOrDefault("letters.json", KeyboardLayouts.letters)

    internal fun loadNumeric(): List<List<KeySpec>> = loadOrDefault("numeric.json", KeyboardLayouts.numeric)

    internal fun loadSymbols(): List<List<KeySpec>> = loadOrDefault("symbols.json", KeyboardLayouts.symbols)

    private fun loadOrDefault(fileName: String, default: List<List<KeySpec>>): List<List<KeySpec>> {
        val file = File(dir, fileName)
        if (!file.exists()) return default
        return try {
            val rows = json.decodeFromString<List<List<KeySpecJson>>>(file.readText())
            rows.toKeySpecs()
                // Old layouts can contain the removed “中/En” key. Keep the
                // layout loadable, but never render that obsolete control again.
                .map { row ->
                    row.map { key ->
                        if (key.action == com.chacha.jadeime.ime.ui.KeyAction.LangToggle) {
                            KeySpec("", com.chacha.jadeime.ime.ui.KeyAction.Spacer, weight = key.weight, showsPopup = false)
                        } else key
                    }
                }
                .ifEmpty { default }
        } catch (error: Exception) {
            Log.e(TAG, "failed to parse layout json $fileName, falling back to default", error)
            default
        }
    }
}

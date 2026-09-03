package com.chacha.jadeime.settings

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.OpenableColumns
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.chacha.jadeime.ServiceLocator
import com.chacha.jadeime.settings.ui.SettingsRoot

/** Ordinary Activity host for the app's two settings destinations. */
class SettingsActivity : ComponentActivity() {
    private var imeEnabled by mutableStateOf(false)
    private var themeImportTick by mutableIntStateOf(0)
    private var page by mutableStateOf(SettingsPage.General)
    private lateinit var appearanceRepository: SettingsAppearanceRepository

    private val importThemeLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@registerForActivityResult
        val text = runCatching {
            contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
        }.getOrNull() ?: return@registerForActivityResult
        val fileName = queryDisplayName(uri) ?: "theme_${System.currentTimeMillis()}.json"
        if (ServiceLocator.themeRepository.importThemeFile(fileName, text)) themeImportTick++
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ServiceLocator.init(applicationContext)
        page = SettingsPage.fromIntent(intent)
        appearanceRepository = SettingsAppearanceRepository(applicationContext)
        setContent {
            val darkMode by appearanceRepository.darkMode.collectAsState()
            SettingsRoot(
                page = page,
                imeEnabled = imeEnabled,
                darkMode = darkMode,
                themeImportTick = themeImportTick,
                onToggleDarkMode = appearanceRepository::toggle,
                onOpenTheme = { page = SettingsPage.Theme },
                onBackToGeneral = { page = SettingsPage.General },
                onOpenImeSettings = ::openImeSettings,
                onShowPicker = ::showInputMethodPicker,
                onImportTheme = { importThemeLauncher.launch("application/json") },
            )
        }
    }

    override fun onResume() {
        super.onResume()
        imeEnabled = isJadeBoardEnabled()
    }

    private fun openImeSettings() = startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))

    private fun showInputMethodPicker() = inputMethodManager().showInputMethodPicker()

    private fun isJadeBoardEnabled(): Boolean =
        inputMethodManager().enabledInputMethodList.any { it.packageName == packageName }

    private fun inputMethodManager(): InputMethodManager =
        getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager

    private fun queryDisplayName(uri: android.net.Uri): String? =
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }

    companion object {
        private const val EXTRA_PAGE = "com.chacha.jadeime.settings.page"

        fun intent(context: Context, page: SettingsPage = SettingsPage.General): Intent =
            Intent(context, SettingsActivity::class.java).putExtra(EXTRA_PAGE, page.name)

        internal fun pageFromIntent(intent: Intent?): SettingsPage =
            intent?.getStringExtra(EXTRA_PAGE)
                ?.let { value -> SettingsPage.entries.find { it.name == value } }
                ?: SettingsPage.General
    }
}

enum class SettingsPage {
    General,
    Theme;

    companion object {
        fun fromIntent(intent: Intent?): SettingsPage = SettingsActivity.pageFromIntent(intent)
    }
}

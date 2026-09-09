package com.chacha.jadeime.settings

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.OpenableColumns
import android.provider.Settings
import android.content.pm.PackageManager
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
import com.chacha.jadeime.theme.ThemePackageRepository

/** Ordinary Activity host for the app's two settings destinations. */
class SettingsActivity : ComponentActivity() {
    private var imeEnabled by mutableStateOf(false)
    private var themeImportTick by mutableIntStateOf(0)
    private var page by mutableStateOf(SettingsPage.General)
    private lateinit var appearanceRepository: SettingsAppearanceRepository
    private lateinit var packageRepository: ThemePackageRepository

    private val importThemeLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@registerForActivityResult
        val bytes = runCatching { contentResolver.openInputStream(uri)?.use { it.readBytes() } }.getOrNull() ?: return@registerForActivityResult
        val fileName = queryDisplayName(uri) ?: "theme_${System.currentTimeMillis()}.json"
        val ok = if (fileName.endsWith(".zip", true)) packageRepository.importZip(bytes, fileName).isSuccess
        else ServiceLocator.themeRepository.importThemeFile(fileName, bytes.toString(Charsets.UTF_8))
        if (ok) themeImportTick++
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ServiceLocator.init(applicationContext)
        packageRepository = ThemePackageRepository(applicationContext)
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
                onOpenRecent = { page = SettingsPage.Recent },
                onBackToGeneral = { page = SettingsPage.General },
                onOpenImeSettings = ::openImeSettings,
                onShowPicker = ::showInputMethodPicker,
                onImportTheme = { importThemeLauncher.launch("*/*") },
                onOpenAudioSettings = ::openAudioSettings,
            )
        }
    }

    override fun onResume() {
        super.onResume()
        imeEnabled = isJadeBoardEnabled()
    }

    private fun openImeSettings() = startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))

    private fun showInputMethodPicker() = inputMethodManager().showInputMethodPicker()
    private fun openAudioSettings() = startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.parse("package:$packageName")))

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
    Recent,
    Theme;

    companion object {
        fun fromIntent(intent: Intent?): SettingsPage = SettingsActivity.pageFromIntent(intent)
    }
}

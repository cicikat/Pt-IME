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
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Ordinary Activity host for the app's two settings destinations. */
class SettingsActivity : ComponentActivity() {
    private var imeEnabled by mutableStateOf(false)
    private var themeImportTick by mutableIntStateOf(0)
    private var page by mutableStateOf(SettingsPage.General)
    private lateinit var appearanceRepository: SettingsAppearanceRepository
    private var importingTheme = false

    private val importThemeLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@registerForActivityResult
        if (importingTheme) return@registerForActivityResult
        importingTheme = true
        lifecycleScope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    val bytes = contentResolver.openInputStream(uri)?.use { input ->
                        val output = java.io.ByteArrayOutputStream()
                        val buffer = ByteArray(8192)
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            require(output.size() + count <= 12 * 1024 * 1024)
                            output.write(buffer, 0, count)
                        }
                        output.toByteArray()
                    } ?: return@runCatching false
                    val fileName = queryDisplayName(uri) ?: "theme_${System.currentTimeMillis()}.json"
                    if (fileName.endsWith(".zip", true)) ServiceLocator.themeRepository.importThemePackage(bytes, fileName) != null
                    else ServiceLocator.themeRepository.importThemeFile(fileName, bytes.toString(Charsets.UTF_8))
                }.getOrDefault(false)
            }
            if (ok) themeImportTick++
            android.widget.Toast.makeText(this@SettingsActivity, if (ok) "主题已导入，请选择配色" else "导入失败，请检查主题格式（ZIP / JSON，12 MB 内）", android.widget.Toast.LENGTH_LONG).show()
            importingTheme = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ServiceLocator.init(applicationContext)
        page = savedInstanceState?.getString("page")?.let { value -> SettingsPage.entries.find { it.name == value } } ?: SettingsPage.fromIntent(intent)
        appearanceRepository = SettingsAppearanceRepository(applicationContext)
        setContent {
            val darkMode by appearanceRepository.darkMode.collectAsState()
            SettingsRoot(
                page = page,
                imeEnabled = imeEnabled,
                darkMode = darkMode,
                themeImportTick = themeImportTick,
                onToggleDarkMode = appearanceRepository::toggle,
                onNavigate = { page = it },
                onBack = { if (page == SettingsPage.General) finish() else page = SettingsPage.General },
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

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("page", page.name)
        super.onSaveInstanceState(outState)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        page = SettingsPage.fromIntent(intent)
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

enum class SettingsPage(val title: String) {
    General("Pt JadeBoard"),
    Recent("最近输入"),
    Theme("主题与皮肤"),
    Sync("数据回传"),
    Phrases("常用短语"),
    Emoji("Emoji 快捷词");

    companion object {
        fun fromIntent(intent: Intent?): SettingsPage = SettingsActivity.pageFromIntent(intent)
    }
}

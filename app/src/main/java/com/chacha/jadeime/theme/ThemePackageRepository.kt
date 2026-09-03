package com.chacha.jadeime.theme

import android.content.Context
import java.io.File
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlinx.serialization.json.Json

/** Validates and installs pt-theme.zip packages atomically. */
class ThemePackageRepository(context: Context) {
    private val root = File(context.applicationContext.filesDir, "themes")
    private val json = Json { ignoreUnknownKeys = true }
    fun importZip(bytes: ByteArray, fileName: String): Result<String> = runCatching {
        require(bytes.size <= 12 * 1024 * 1024) { "theme package exceeds 12 MiB" }
        val id = File(fileName).nameWithoutExtension.replace(Regex("[^A-Za-z0-9._-]"), "_").ifBlank { "theme" }
        val staging = File(root, ".staging_$id"); staging.deleteRecursively(); staging.mkdirs()
        var total = 0L; var found = false
        ZipInputStream(bytes.inputStream()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (entry.isDirectory) continue
                val out = File(staging, entry.name)
                require(out.canonicalPath.startsWith(staging.canonicalPath + File.separator)) { "invalid package path" }
                if (entry.name == "theme.json") found = true
                out.parentFile?.mkdirs()
                var count = 0L
                out.outputStream().use { sink ->
                    val buffer = ByteArray(8192)
                    while (true) { val n = zip.read(buffer); if (n <= 0) break; count += n; total += n; require(count <= 8 * 1024 * 1024 && total <= 12 * 1024 * 1024) { "theme resource too large" }; sink.write(buffer, 0, n) }
                }
            }
        }
        require(found) { "theme.json missing" }
        val theme = json.decodeFromString(SnabThemeJson.serializer(), File(staging, "theme.json").readText())
        require(theme.formatVersion == 1) { "unsupported theme format" }
        require(theme.minAppVersion <= 1) { "theme requires newer app" }
        theme.font?.let { require(it.endsWith(".ttf", true) || it.endsWith(".otf", true)) { "unsupported font" } }
        val target = File(root, id); target.deleteRecursively(); staging.renameTo(target); "user_$id"
    }.onFailure { File(root, ".staging_${File(fileName).nameWithoutExtension}").deleteRecursively() }

    fun export(id: String): ByteArray? = File(root, id.removePrefix("user_")).takeIf { it.isDirectory }?.let { dir ->
        java.io.ByteArrayOutputStream().also { output -> ZipOutputStream(output).use { zip -> dir.walkTopDown().filter(File::isFile).forEach { file -> zip.putNextEntry(java.util.zip.ZipEntry(file.relativeTo(dir).path.replace('\\', '/'))); file.inputStream().use { it.copyTo(zip) }; zip.closeEntry() } } }.toByteArray()
    }
}

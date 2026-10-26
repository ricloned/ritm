package com.ritm.app.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.FileProvider
import com.ritm.app.BuildConfig
import com.ritm.app.data.Store
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Обновления из GitHub Releases: проверка, скачивание APK, установка.
 * Номер сборки берётся из тега вида v1.5.0-build12 и сравнивается с versionCode.
 */
object Updater {
    data class Release(
        val versionName: String,
        val build: Int,
        val notes: String,
        val apkName: String,
        val apiUrl: String,
        val browserUrl: String,
        val size: Long,
    )

    sealed class State {
        data object Idle : State()
        data object Checking : State()
        data object UpToDate : State()
        data class Available(val release: Release) : State()
        data class Downloading(val release: Release) : State()
        data class Ready(val release: Release, val file: File) : State()
        data class Error(val message: String) : State()
    }

    var state by mutableStateOf<State>(State.Idle)
        private set
    var progress by mutableFloatStateOf(0f)
        private set
    /** Скрыта ли плашка для этой сборки. */
    var dismissedBuild by mutableStateOf(0)

    private const val PREFS = "updater"
    private const val CHECK_INTERVAL = 6 * 60 * 60 * 1000L

    val latest: Release?
        get() = when (val s = state) {
            is State.Available -> s.release
            is State.Downloading -> s.release
            is State.Ready -> s.release
            else -> null
        }

    private fun apiBase(): String {
        val repo = Store.settings.updateRepo.trim().trim('/')
            .removePrefix("https://github.com/").removePrefix("github.com/").removeSuffix(".git")
        return "https://api.github.com/repos/$repo"
    }

    private fun open(url: String, accept: String): HttpURLConnection {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 20_000
        c.readTimeout = 60_000
        c.instanceFollowRedirects = false
        c.setRequestProperty("Accept", accept)
        c.setRequestProperty("User-Agent", "Ritm-Android")
        c.setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
        val token = Store.settings.githubToken.trim()
        if (token.isNotEmpty() && url.contains("api.github.com")) {
            c.setRequestProperty("Authorization", "Bearer $token")
        }
        return c
    }

    private fun httpError(code: Int): String = when (code) {
        401 -> "GitHub-токен неверный или истёк"
        403 -> "GitHub ограничил запросы, попробуй позже"
        404 -> "Релизы не найдены. Если репозиторий приватный — добавь GitHub-токен в настройках"
        else -> "Ошибка GitHub: $code"
    }

    /** Проверка при запуске — не чаще раза в 6 часов. */
    fun autoCheck(ctx: Context) {
        val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        dismissedBuild = prefs.getInt("dismissed", 0)
        cleanup(ctx)
        val last = prefs.getLong("lastCheck", 0)
        if (System.currentTimeMillis() - last < CHECK_INTERVAL) {
            // покажем то, что нашли в прошлый раз
            val build = prefs.getInt("knownBuild", 0)
            if (build > BuildConfig.VERSION_CODE && state is State.Idle) check(ctx)
            return
        }
        check(ctx)
    }

    fun check(ctx: Context) {
        if (state is State.Checking || state is State.Downloading) return
        state = State.Checking
        Store.scope.launch {
            state = try {
                val r = withContext(Dispatchers.IO) { fetchLatest() }
                ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                    .putLong("lastCheck", System.currentTimeMillis())
                    .putInt("knownBuild", r?.build ?: 0)
                    .apply()
                if (r != null && r.build > BuildConfig.VERSION_CODE) State.Available(r) else State.UpToDate
            } catch (e: Exception) {
                State.Error(e.message ?: "Не удалось проверить обновления")
            }
        }
    }

    private fun fetchLatest(): Release? {
        val c = open("${apiBase()}/releases?per_page=10", "application/vnd.github+json")
        try {
            val code = c.responseCode
            if (code != 200) throw IOException(httpError(code))
            val arr = JSONArray(c.inputStream.bufferedReader().use { it.readText() })
            var best: Release? = null
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                if (o.optBoolean("draft")) continue
                val r = parseRelease(o) ?: continue
                if (best == null || r.build > best.build) best = r
            }
            return best
        } catch (e: IOException) {
            throw IOException(e.message ?: "Нет связи с GitHub")
        } finally {
            c.disconnect()
        }
    }

    private fun parseRelease(o: JSONObject): Release? {
        val tag = o.optString("tag_name")
        val build = Regex("""build(\d+)""").find(tag)?.groupValues?.get(1)?.toIntOrNull() ?: return null
        val name = Regex("""v?(\d+(?:\.\d+)*)""").find(tag)?.groupValues?.get(1) ?: tag
        val assets = o.optJSONArray("assets") ?: return null
        for (i in 0 until assets.length()) {
            val a = assets.optJSONObject(i) ?: continue
            val fileName = a.optString("name")
            if (!fileName.endsWith(".apk")) continue
            return Release(
                versionName = name,
                build = build,
                notes = o.optString("body").trim(),
                apkName = fileName,
                apiUrl = a.optString("url"),
                browserUrl = a.optString("browser_download_url"),
                size = a.optLong("size"),
            )
        }
        return null
    }

    fun dismiss(ctx: Context) {
        val r = latest ?: return
        dismissedBuild = r.build
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putInt("dismissed", r.build).apply()
    }

    private fun updatesDir(ctx: Context) = File(ctx.cacheDir, "updates").apply { mkdirs() }

    /** Удаляем старые скачанные APK. */
    private fun cleanup(ctx: Context) {
        try {
            updatesDir(ctx).listFiles()?.forEach { it.delete() }
        } catch (_: Exception) {
        }
    }

    fun download(ctx: Context) {
        val r = latest ?: return
        if (state is State.Downloading) return
        if (state is State.Ready) {
            install(ctx)
            return
        }
        state = State.Downloading(r)
        progress = 0f
        Store.scope.launch {
            state = try {
                val file = withContext(Dispatchers.IO) { downloadApk(ctx, r) }
                State.Ready(r, file)
            } catch (e: Exception) {
                State.Error(e.message ?: "Не удалось скачать обновление")
            }
            if (state is State.Ready) install(ctx)
        }
    }

    private fun downloadApk(ctx: Context, r: Release): File {
        val target = File(updatesDir(ctx), "Ritm-${r.versionName}-build${r.build}.apk")
        val tmp = File(target.path + ".part")
        // через API (работает и для приватного репо), редирект на хранилище — уже без токена
        var url = r.apiUrl.ifBlank { r.browserUrl }
        var accept = "application/octet-stream"
        var hops = 0
        while (true) {
            val c = open(url, accept)
            val code = c.responseCode
            if (code in 300..399) {
                val loc = c.getHeaderField("Location") ?: throw IOException("Ошибка загрузки: пустой редирект")
                c.disconnect()
                url = loc
                if (++hops > 5) throw IOException("Слишком много перенаправлений")
                continue
            }
            if (code != 200) {
                c.disconnect()
                throw IOException(httpError(code))
            }
            try {
                val total = c.contentLengthLong.takeIf { it > 0 } ?: r.size
                var read = 0L
                var lastP = -1f
                c.inputStream.use { input ->
                    tmp.outputStream().use { out ->
                        val buf = ByteArray(64 * 1024)
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            out.write(buf, 0, n)
                            read += n
                            if (total > 0) {
                                val p = (read.toFloat() / total).coerceIn(0f, 1f)
                                if (p - lastP >= 0.01f) {
                                    lastP = p
                                    Store.scope.launch { progress = p }
                                }
                            }
                        }
                    }
                }
            } finally {
                c.disconnect()
            }
            break
        }
        if (tmp.length() < 100_000) {
            tmp.delete()
            throw IOException("Скачанный файл повреждён")
        }
        target.delete()
        if (!tmp.renameTo(target)) throw IOException("Не удалось сохранить файл")
        return target
    }

    fun canInstall(ctx: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O || ctx.packageManager.canRequestPackageInstalls()

    fun openInstallPermission(ctx: Context) {
        val i = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${ctx.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            ctx.startActivity(i)
        } catch (_: Exception) {
        }
    }

    fun install(ctx: Context) {
        val s = state as? State.Ready ?: return
        if (!s.file.exists()) {
            state = State.Available(s.release)
            return
        }
        if (!canInstall(ctx)) {
            openInstallPermission(ctx)
            return
        }
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", s.file)
        val i = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            ctx.startActivity(i)
        } catch (e: Exception) {
            state = State.Error("Не удалось открыть установщик")
        }
    }
}

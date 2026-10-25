package com.ritm.app.ui.settings

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.ritm.app.BuildConfig
import com.ritm.app.ai.AiClient
import com.ritm.app.data.AppSettings
import com.ritm.app.data.Store
import com.ritm.app.notify.Reminders
import com.ritm.app.ui.Pill
import com.ritm.app.ui.SectionTitle
import com.ritm.app.ui.SoftCard
import com.ritm.app.ui.hm
import com.ritm.app.ui.pickTime
import kotlinx.coroutines.launch
import java.time.LocalTime

private data class Preset(val name: String, val url: String, val model: String)

private val PRESETS = listOf(
    Preset("OpenRouter", "https://openrouter.ai/api/v1", "openai/gpt-4o-mini"),
    Preset("OpenAI", "https://api.openai.com/v1", "gpt-4o-mini"),
    Preset("DeepSeek", "https://api.deepseek.com/v1", "deepseek-chat"),
    Preset("Claude", "https://api.anthropic.com", "claude-haiku-5-5"),
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    var s by remember { mutableStateOf(Store.settings) }
    var showKey by remember { mutableStateOf(false) }
    var testing by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<String?>(null) }
    var canNotify by remember { mutableStateOf(Reminders.canNotify(ctx)) }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        canNotify = granted
    }

    fun commit(n: AppSettings) {
        s = n
        Store.updateSettings(n)
    }

    Scaffold(
        containerColor = cs.background,
        topBar = {
            TopAppBar(
                title = { Text("Настройки", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Назад") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = cs.background),
            )
        }
    ) { pad ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(pad)
                .consumeWindowInsets(pad)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ---------- ИИ ----------
            SectionTitle("Искусственный интеллект")
            SoftCard {
                Text(
                    "Подойдёт любой OpenAI-совместимый сервис (OpenRouter, OpenAI, DeepSeek и др.) или Claude от Anthropic.",
                    style = MaterialTheme.typography.bodySmall,
                    color = cs.onSurfaceVariant
                )
                Spacer(Modifier.height(10.dp))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PRESETS.forEach { p ->
                        Pill(p.name, s.baseUrl.trim().trimEnd('/') == p.url) {
                            commit(s.copy(baseUrl = p.url, model = p.model))
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = s.baseUrl,
                    onValueChange = { commit(s.copy(baseUrl = it)) },
                    label = { Text("Адрес API (сайт)") },
                    placeholder = { Text("https://openrouter.ai/api/v1") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = s.apiKey,
                    onValueChange = { commit(s.copy(apiKey = it.trim())) },
                    label = { Text("API-ключ") },
                    placeholder = { Text("sk-...") },
                    singleLine = true,
                    visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showKey = !showKey }) {
                            Icon(if (showKey) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility, "Показать ключ")
                        }
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = s.model,
                    onValueChange = { commit(s.copy(model = it.trim())) },
                    label = { Text("Модель") },
                    placeholder = { Text("openai/gpt-4o-mini") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                )
                Spacer(Modifier.height(12.dp))
                FilledTonalButton(
                    onClick = {
                        scope.launch {
                            testing = true
                            testResult = null
                            testResult = try {
                                val r = AiClient.complete("Отвечай очень коротко.", "Скажи «работает» и одно доброе пожелание.", maxTokens = 100)
                                "✅ $r"
                            } catch (e: Exception) {
                                "❌ ${e.message}"
                            } finally {
                                testing = false
                            }
                        }
                    },
                    enabled = !testing,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    if (testing) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                    }
                    Text("Проверить подключение")
                }
                testResult?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, style = MaterialTheme.typography.bodySmall)
                }
            }

            // ---------- Уведомления ----------
            SectionTitle("Уведомления")
            SoftCard {
                if (!canNotify && Build.VERSION.SDK_INT >= 33) {
                    Text(
                        "Уведомления выключены — напоминания не будут приходить.",
                        style = MaterialTheme.typography.bodySmall,
                        color = cs.error
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = { permission.launch(Manifest.permission.POST_NOTIFICATIONS) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp)
                    ) { Text("Разрешить уведомления") }
                    HorizontalDivider(Modifier.padding(vertical = 12.dp), color = cs.outlineVariant)
                }
                Text(
                    "Задачи со временем напоминают о себе ровно в указанное время.",
                    style = MaterialTheme.typography.bodySmall,
                    color = cs.onSurfaceVariant
                )
                HorizontalDivider(Modifier.padding(vertical = 12.dp), color = cs.outlineVariant)
                ToggleTimeRow(
                    title = "Утренний план на день",
                    subtitle = "Список задач на сегодня",
                    enabled = s.digestEnabled,
                    time = LocalTime.of(s.digestHour, s.digestMinute),
                    onToggle = { commit(s.copy(digestEnabled = it)) },
                    onPickTime = {
                        pickTime(ctx, LocalTime.of(s.digestHour, s.digestMinute)) { t ->
                            commit(s.copy(digestHour = t.hour, digestMinute = t.minute))
                        }
                    },
                )
                HorizontalDivider(Modifier.padding(vertical = 12.dp), color = cs.outlineVariant)
                ToggleTimeRow(
                    title = "Напоминание о тренировке",
                    subtitle = "Только в дни, когда есть упражнения",
                    enabled = s.workoutReminder,
                    time = LocalTime.of(s.workoutHour, s.workoutMinute),
                    onToggle = { commit(s.copy(workoutReminder = it)) },
                    onPickTime = {
                        pickTime(ctx, LocalTime.of(s.workoutHour, s.workoutMinute)) { t ->
                            commit(s.copy(workoutHour = t.hour, workoutMinute = t.minute))
                        }
                    },
                )
                Spacer(Modifier.height(12.dp))
                OutlinedButton(
                    onClick = { Reminders.showDigest(ctx) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp)
                ) { Text("Показать тестовое уведомление") }
            }

            // ---------- О приложении ----------
            SectionTitle("О приложении")
            SoftCard {
                Text("Ритм", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    "Версия ${BuildConfig.VERSION_NAME} (сборка ${BuildConfig.VERSION_CODE})",
                    style = MaterialTheme.typography.bodySmall,
                    color = cs.onSurfaceVariant
                )
                Text(
                    "Все данные хранятся только на телефоне.",
                    style = MaterialTheme.typography.bodySmall,
                    color = cs.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ToggleTimeRow(
    title: String,
    subtitle: String,
    enabled: Boolean,
    time: LocalTime,
    onToggle: (Boolean) -> Unit,
    onPickTime: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
        }
        if (enabled) {
            Pill(time.hm(), selected = false, modifier = Modifier.padding(end = 8.dp)) { onPickTime() }
        }
        Switch(
            checked = enabled,
            onCheckedChange = onToggle,
            colors = SwitchDefaults.colors(checkedTrackColor = cs.primary)
        )
    }
}

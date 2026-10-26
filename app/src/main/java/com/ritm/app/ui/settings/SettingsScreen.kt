package com.ritm.app.ui.settings

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.TextButton
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import com.ritm.app.ui.UiState
import com.ritm.app.update.UpdateSection
import com.ritm.app.ui.shortRu
import com.ritm.app.ui.theme.THEMES
import com.ritm.app.ui.theme.previewColor
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

            // ---------- Тема ----------
            SectionTitle("Тема оформления")
            SoftCard {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("fixed" to "Одна тема", "daily" to "Новая каждый день", "launch" to "Новая при заходе").forEach { (mode, label) ->
                        Pill(label, s.themeMode == mode) { commit(s.copy(themeMode = mode)) }
                    }
                }
                Spacer(Modifier.height(14.dp))
                val activeIndex = Store.themeIndexFor(UiState.today)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    THEMES.forEachIndexed { i, t ->
                        Column(
                            Modifier
                                .clip(RoundedCornerShape(14.dp))
                                .clickable { commit(s.copy(themeMode = "fixed", themeIndex = i)) }
                                .padding(4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(previewColor(t))
                                    .border(
                                        width = if (i == activeIndex) 3.dp else 0.dp,
                                        color = if (i == activeIndex) cs.onSurface else Color.Transparent,
                                        shape = CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) { Text(t.emoji) }
                            Spacer(Modifier.height(4.dp))
                            Text(
                                t.name,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (i == activeIndex) cs.primary else cs.onSurfaceVariant
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    when (s.themeMode) {
                        "daily" -> "Каждый день — следующая тема из семи. Нажми на цвет, чтобы закрепить одну."
                        "launch" -> "При каждом заходе в приложение тема меняется. Нажми на цвет, чтобы закрепить одну."
                        else -> "Нажми на цвет, чтобы выбрать тему."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = cs.onSurfaceVariant
                )
            }

            // ---------- Питание ----------
            SectionTitle("Норма питания в день")
            SoftCard {
                Text(
                    "С этими цифрами сравнивается итог дня во вкладке «Питание».",
                    style = MaterialTheme.typography.bodySmall,
                    color = cs.onSurfaceVariant
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GoalField("Ккал", s.kcalGoal, Modifier.weight(1.2f)) { commit(s.copy(kcalGoal = it)) }
                    GoalField("Белки", s.proteinGoal, Modifier.weight(1f)) { commit(s.copy(proteinGoal = it)) }
                    GoalField("Жиры", s.fatGoal, Modifier.weight(1f)) { commit(s.copy(fatGoal = it)) }
                    GoalField("Угл.", s.carbsGoal, Modifier.weight(1f)) { commit(s.copy(carbsGoal = it)) }
                }
            }

            // ---------- Мои продукты ----------
            SectionTitle("КБЖУ: поиск и мои продукты")
            SoftCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Искать продукты в интернете", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                        Text(
                            if (com.ritm.app.ai.AiClient.webSearchAvailable())
                                "Для брендов (Теос, Савушкин…) ИИ найдёт точный состав. Запрос чуть дольше и немного дороже."
                            else "Работает с OpenRouter и Claude. С текущим сервисом ИИ считает по своим знаниям.",
                            style = MaterialTheme.typography.bodySmall,
                            color = cs.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = s.foodWebSearch,
                        onCheckedChange = { commit(s.copy(foodWebSearch = it)) },
                        colors = SwitchDefaults.colors(checkedTrackColor = cs.primary)
                    )
                }
                HorizontalDivider(Modifier.padding(vertical = 12.dp), color = cs.outlineVariant)
                MyFoodsList()
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
                TestButton { Reminders.showDigest(ctx) }
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
                TestButton { Reminders.showWorkout(ctx, force = true) }
                HorizontalDivider(Modifier.padding(vertical = 12.dp), color = cs.outlineVariant)
                ToggleTimeRow(
                    title = "Отчёт недели 📸",
                    subtitle = "Вес и фото во вкладке «Прогресс»",
                    enabled = s.weeklyReminder,
                    time = LocalTime.of(s.weeklyHour, s.weeklyMinute),
                    onToggle = { commit(s.copy(weeklyReminder = it)) },
                    onPickTime = {
                        pickTime(ctx, LocalTime.of(s.weeklyHour, s.weeklyMinute)) { t ->
                            commit(s.copy(weeklyHour = t.hour, weeklyMinute = t.minute))
                        }
                    },
                )
                if (s.weeklyReminder) {
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        for (d in 1..7) {
                            Pill(
                                java.time.DayOfWeek.of(d).shortRu(),
                                s.weeklyDay == d,
                                modifier = Modifier.weight(1f)
                            ) { commit(s.copy(weeklyDay = d)) }
                        }
                    }
                }
                TestButton { Reminders.showWeekly(ctx, force = true) }
                HorizontalDivider(Modifier.padding(vertical = 12.dp), color = cs.outlineVariant)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Сфоткать еду 🍽️", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                        Text(
                            "Нажатие на уведомление сразу открывает камеру. Не приходит, если ты фоткал еду последние 1,5 часа.",
                            style = MaterialTheme.typography.bodySmall,
                            color = cs.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = s.foodReminders,
                        onCheckedChange = { commit(s.copy(foodReminders = it)) },
                        colors = SwitchDefaults.colors(checkedTrackColor = cs.primary)
                    )
                }
                if (s.foodReminders) {
                    Spacer(Modifier.height(10.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        s.foodTimes.forEachIndexed { i, m ->
                            Row(
                                Modifier
                                    .clip(RoundedCornerShape(50))
                                    .background(cs.primaryContainer),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    LocalTime.of(m / 60, m % 60).hm(),
                                    modifier = Modifier
                                        .clickable {
                                            pickTime(ctx, LocalTime.of(m / 60, m % 60)) { t ->
                                                val list = s.foodTimes.toMutableList()
                                                list[i] = t.hour * 60 + t.minute
                                                commit(s.copy(foodTimes = list.distinct().sorted()))
                                            }
                                        }
                                        .padding(start = 14.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = cs.onPrimaryContainer
                                )
                                Icon(
                                    Icons.Rounded.Close,
                                    contentDescription = "Убрать",
                                    tint = cs.onPrimaryContainer,
                                    modifier = Modifier
                                        .clip(CircleShape)
                                        .clickable { commit(s.copy(foodTimes = s.foodTimes.filterIndexed { j, _ -> j != i })) }
                                        .padding(start = 2.dp, end = 10.dp, top = 6.dp, bottom = 6.dp)
                                        .size(16.dp)
                                )
                            }
                        }
                        if (s.foodTimes.size < Reminders.MAX_FOOD_TIMES) {
                            Pill("+ время", selected = false) {
                                pickTime(ctx, LocalTime.of(12, 0)) { t ->
                                    commit(s.copy(foodTimes = (s.foodTimes + (t.hour * 60 + t.minute)).distinct().sorted()))
                                }
                            }
                        }
                    }
                }
                TestButton { Reminders.showFood(ctx, force = true) }
            }

            // ---------- О приложении ----------
            SectionTitle("Обновления")
            SoftCard {
                UpdateSection()
                HorizontalDivider(Modifier.padding(vertical = 12.dp), color = cs.outlineVariant)
                OutlinedTextField(
                    value = s.updateRepo,
                    onValueChange = { commit(s.copy(updateRepo = it.trim())) },
                    label = { Text("Репозиторий GitHub") },
                    placeholder = { Text("ricloned/ritm") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                )
                Spacer(Modifier.height(8.dp))
                var showToken by remember { mutableStateOf(false) }
                OutlinedTextField(
                    value = s.githubToken,
                    onValueChange = { commit(s.copy(githubToken = it.trim())) },
                    label = { Text("GitHub-токен (для приватного репо)") },
                    placeholder = { Text("github_pat_...") },
                    singleLine = true,
                    visualTransformation = if (showToken) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showToken = !showToken }) {
                            Icon(if (showToken) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility, "Показать")
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                )
                Text(
                    "Нужен, только если репозиторий приватный. GitHub → Settings → Developer settings → Fine-grained tokens: доступ только к репо ritm, право Contents: Read-only.",
                    style = MaterialTheme.typography.labelSmall,
                    color = cs.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp)
                )
                Spacer(Modifier.height(8.dp))
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
private fun MyFoodsList() {
    val cs = MaterialTheme.colorScheme
    var editing by remember { mutableStateOf<com.ritm.app.data.MyFood?>(null) }
    var adding by remember { mutableStateOf(false) }
    var expanded by remember { mutableStateOf(false) }
    val list = Store.myFoods
    Text("Мои продукты · ${list.size}", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
    Text(
        "КБЖУ на 100 г. ИИ берёт цифры отсюда в первую очередь. Продукт попадает сюда сам, когда ты поправляешь его цифры в приёме пищи.",
        style = MaterialTheme.typography.bodySmall,
        color = cs.onSurfaceVariant
    )
    Spacer(Modifier.height(6.dp))
    (if (expanded) list else list.take(5)).forEach { f ->
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .clickable { editing = f }
                .padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(f.name, style = MaterialTheme.typography.bodyMedium)
                Text(
                    "${Math.round(f.kcal)} ккал · Б ${Math.round(f.protein * 10) / 10.0} · Ж ${Math.round(f.fat * 10) / 10.0} · У ${Math.round(f.carbs * 10) / 10.0}",
                    style = MaterialTheme.typography.labelSmall,
                    color = cs.onSurfaceVariant
                )
            }
            IconButton(onClick = { Store.deleteMyFood(f.name) }) {
                Icon(Icons.Rounded.Close, "Удалить", tint = cs.onSurfaceVariant)
            }
        }
    }
    Row {
        if (list.size > 5) {
            TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) "Свернуть" else "Показать все") }
        }
        TextButton(onClick = { adding = true }) { Text("+ Добавить продукт") }
    }
    if (adding) MyFoodDialog(null) { adding = false }
    editing?.let { f -> MyFoodDialog(f) { editing = null } }
}

@Composable
private fun MyFoodDialog(initial: com.ritm.app.data.MyFood?, onClose: () -> Unit) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    fun t(d: Double?) = d?.let { (Math.round(it * 10) / 10.0).toString().removeSuffix(".0") } ?: ""
    var kcal by remember { mutableStateOf(t(initial?.kcal)) }
    var p by remember { mutableStateOf(t(initial?.protein)) }
    var f by remember { mutableStateOf(t(initial?.fat)) }
    var c by remember { mutableStateOf(t(initial?.carbs)) }
    fun num(v: String) = v.replace(',', '.').filter { it.isDigit() || it == '.' }.take(6)
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onClose,
        title = { Text(if (initial == null) "Новый продукт" else "Продукт") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    label = { Text("Название") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp),
                )
                Text("На 100 г:", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(
                        Triple("Ккал", kcal) { v: String -> kcal = num(v) },
                        Triple("Б", p) { v: String -> p = num(v) },
                        Triple("Ж", f) { v: String -> f = num(v) },
                        Triple("У", c) { v: String -> c = num(v) },
                    ).forEach { (label, value, set) ->
                        OutlinedTextField(
                            value = value, onValueChange = set,
                            label = { Text(label) }, singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp),
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    Store.saveMyFood(
                        com.ritm.app.data.MyFood(
                            name.trim(),
                            kcal.toDoubleOrNull() ?: 0.0,
                            p.toDoubleOrNull() ?: 0.0,
                            f.toDoubleOrNull() ?: 0.0,
                            c.toDoubleOrNull() ?: 0.0,
                        ),
                        oldName = initial?.name,
                    )
                    onClose()
                },
                enabled = name.isNotBlank() && kcal.toDoubleOrNull() != null
            ) { Text("Сохранить") }
        },
        dismissButton = { TextButton(onClick = onClose) { Text("Отмена") } },
    )
}

@Composable
private fun GoalField(label: String, value: Int, modifier: Modifier, onValue: (Int) -> Unit) {
    var text by remember(label) { mutableStateOf(value.toString()) }
    OutlinedTextField(
        value = text,
        onValueChange = { v ->
            text = v.filter { it.isDigit() }.take(5)
            text.toIntOrNull()?.let(onValue)
        },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
    )
}

@Composable
private fun TestButton(onClick: () -> Unit) {
    TextButton(onClick = onClick, contentPadding = PaddingValues(horizontal = 0.dp, vertical = 4.dp)) {
        Text("Проверить это уведомление", style = MaterialTheme.typography.labelMedium)
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

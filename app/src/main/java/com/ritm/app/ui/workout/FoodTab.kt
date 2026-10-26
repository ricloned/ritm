package com.ritm.app.ui.workout

import android.content.ActivityNotFoundException
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import com.ritm.app.data.FoodEntry
import com.ritm.app.data.FoodItem
import com.ritm.app.data.Store
import com.ritm.app.ui.EmptyState
import com.ritm.app.ui.SoftCard
import com.ritm.app.ui.UiState
import com.ritm.app.ui.appendDictated
import com.ritm.app.ui.dayTitle
import com.ritm.app.ui.fmtNum
import com.ritm.app.ui.pickDate
import com.ritm.app.ui.rememberDictation
import com.ritm.app.ui.swipeHorizontal
import com.ritm.app.ui.timeText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private fun r0(d: Double): String = fmtNum(Math.round(d).toDouble())
private fun r1(d: Double): String = fmtNum(Math.round(d * 10) / 10.0)

private fun macroLine(e: FoodEntry): String =
    "${r0(e.kcal)} ккал · Б ${r0(e.protein)} · Ж ${r0(e.fat)} · У ${r0(e.carbs)}"

@Composable
fun FoodTab() {
    val editId = UiState.foodEditId
    val editing = editId?.let { id -> Store.food.find { it.id == id } }
    if (editing != null) {
        BackHandler { closeEditor(editing.id) }
        FoodEditor(editing)
        return
    } else if (editId != null) {
        UiState.foodEditId = null
    }

    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    val date = UiState.foodDate
    val today = UiState.today
    var pendingPath by rememberSaveable { mutableStateOf<String?>(null) }
    val entries = Store.food.filter { it.date == date }.sortedBy { it.time }
    val dayKcal = entries.sumOf { it.kcal }

    fun newFile(): File {
        val dir = File(ctx.filesDir, "food").apply { mkdirs() }
        return File(dir, "food_${System.currentTimeMillis()}.jpg")
    }

    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val p = pendingPath
        if (p != null) {
            if (ok && File(p).length() > 0) {
                UiState.foodEditId = Store.addFood(UiState.foodDate, p)
            } else File(p).delete()
        }
        pendingPath = null
    }

    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            val target = newFile()
            val forDate = UiState.foodDate
            scope.launch {
                val ok = withContext(Dispatchers.IO) {
                    try {
                        ctx.contentResolver.openInputStream(uri)?.use { input ->
                            target.outputStream().use { input.copyTo(it) }
                        }
                        target.length() > 0
                    } catch (_: Exception) {
                        false
                    }
                }
                if (ok) UiState.foodEditId = Store.addFood(forDate, target.absolutePath)
                else Toast.makeText(ctx, "Не удалось загрузить фото", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun takePhoto() {
        val f = newFile()
        pendingPath = f.absolutePath
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", f)
        try {
            camera.launch(uri)
        } catch (_: ActivityNotFoundException) {
            pendingPath = null
            Toast.makeText(ctx, "Камера не найдена", Toast.LENGTH_SHORT).show()
        }
    }

    // открыли из уведомления «сфоткай еду» — сразу камера
    LaunchedEffect(UiState.openFoodCamera) {
        if (UiState.openFoodCamera) {
            UiState.openFoodCamera = false
            takePhoto()
        }
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier
            .fillMaxSize()
            .swipeHorizontal(
                onPrev = { UiState.foodDate = UiState.foodDate.minusDays(1) },
                onNext = { if (UiState.foodDate.isBefore(UiState.today)) UiState.foodDate = UiState.foodDate.plusDays(1) },
            ),
        contentPadding = PaddingValues(16.dp, 4.dp, 16.dp, 24.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(cs.surface)
                    .padding(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { UiState.foodDate = date.minusDays(1) }) { Icon(Icons.Rounded.ChevronLeft, "Назад") }
                Column(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { pickDate(ctx, date) { if (!it.isAfter(today)) UiState.foodDate = it } }
                        .padding(vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(date.dayTitle(), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text(
                        when {
                            entries.isEmpty() -> "ничего не записано"
                            dayKcal > 0 -> "приёмов: ${entries.size} · ${r0(dayKcal)} ккал"
                            else -> "приёмов пищи: ${entries.size}"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = cs.onSurfaceVariant
                    )
                }
                IconButton(onClick = { UiState.foodDate = date.plusDays(1) }, enabled = date.isBefore(today)) {
                    Icon(Icons.Rounded.ChevronRight, "Вперёд")
                }
            }
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = { takePhoto() },
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Icon(Icons.Rounded.CameraAlt, null)
                        Spacer(Modifier.width(6.dp))
                        Text("Сфоткать")
                    }
                    FilledTonalButton(
                        onClick = {
                            gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Icon(Icons.Rounded.PhotoLibrary, null)
                        Spacer(Modifier.width(6.dp))
                        Text("Из галереи")
                    }
                }
                TextButton(
                    onClick = { UiState.foodEditId = Store.addFood(date, "") },
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                ) {
                    Icon(Icons.Rounded.EditNote, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Записать без фото")
                }
            }
        }
        if (entries.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                EmptyState(
                    "🍽️",
                    if (date == today) "Сегодня ещё ничего" else "За этот день нет записей",
                    "Сфоткай еду и напиши, что в ней, например «рис 100г сухой, курица 200г». ИИ посчитает КБЖУ. Дни листаются свайпом."
                )
            }
        }
        items(entries, key = { it.id }) { e -> FoodCard(e) { UiState.foodEditId = e.id } }
        if (entries.isNotEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) { DayTotals(entries) }
        }
    }
}

private fun closeEditor(id: Long) {
    val e = Store.food.find { it.id == id }
    // ушёл из записи, не посчитав, — считаем сами в фоне
    if (e != null && !e.analyzed && e.items.isEmpty() && id !in Store.foodAnalyzing &&
        Store.settings.apiKey.isNotBlank() && (e.photoPath.isNotBlank() || e.note.isNotBlank())
    ) {
        Store.analyzeFood(id)
    }
    // пустая запись без фото и текста не нужна
    if (e != null && e.photoPath.isBlank() && e.note.isBlank() && e.items.isEmpty()) Store.deleteFood(id)
    UiState.foodEditId = null
}

// ======================= Карточка приёма пищи =======================

@Composable
private fun FoodCard(e: FoodEntry, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val analyzing = e.id in Store.foodAnalyzing
    val error = Store.foodErrors[e.id]
    Column(
        Modifier
            .clip(RoundedCornerShape(18.dp))
            .background(cs.surface)
            .clickable { onClick() }
    ) {
        Box {
            if (e.photoPath.isNotBlank()) {
                AsyncImage(
                    model = File(e.photoPath),
                    contentDescription = e.title.ifBlank { "Еда" },
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .background(cs.surfaceVariant)
                )
            } else {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .background(cs.primaryContainer),
                    contentAlignment = Alignment.Center
                ) { Text("📝", fontSize = 40.sp) }
            }
            Text(
                e.time.timeText(),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color.Black.copy(alpha = 0.45f))
                    .padding(horizontal = 8.dp, vertical = 2.dp),
                color = Color.White,
                style = MaterialTheme.typography.labelMedium
            )
        }
        Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            val title = e.title.ifBlank { e.note }.ifBlank { "Приём пищи" }
            Text(
                title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            when {
                analyzing -> Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(12.dp), strokeWidth = 1.5.dp, color = cs.primary)
                    Spacer(Modifier.width(6.dp))
                    Text("считаю КБЖУ…", style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
                }
                e.hasNutrition -> {
                    Text("${r0(e.kcal)} ккал", style = MaterialTheme.typography.labelLarge, color = cs.primary, fontWeight = FontWeight.SemiBold)
                    Text(
                        "Б ${r0(e.protein)} · Ж ${r0(e.fat)} · У ${r0(e.carbs)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = cs.onSurfaceVariant
                    )
                }
                error != null -> Text("не посчитано, нажми", style = MaterialTheme.typography.labelSmall, color = cs.error)
                else -> Text("нажми, чтобы посчитать", style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
            }
        }
    }
}

// ======================= Итог за день =======================

@Composable
private fun DayTotals(entries: List<FoodEntry>) {
    val cs = MaterialTheme.colorScheme
    val s = Store.settings
    val kcal = entries.sumOf { it.kcal }
    val p = entries.sumOf { it.protein }
    val f = entries.sumOf { it.fat }
    val c = entries.sumOf { it.carbs }
    val notCounted = entries.count { !it.hasNutrition }
    SoftCard(color = cs.primaryContainer) {
        Text("Итого за день", style = MaterialTheme.typography.labelLarge, color = cs.onPrimaryContainer)
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                r0(kcal),
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                color = cs.onPrimaryContainer
            )
            Spacer(Modifier.width(6.dp))
            Text(
                "/ ${s.kcalGoal} ккал",
                style = MaterialTheme.typography.titleSmall,
                color = cs.onPrimaryContainer.copy(alpha = 0.8f),
                modifier = Modifier.padding(bottom = 6.dp)
            )
            Spacer(Modifier.weight(1f))
            val left = s.kcalGoal - kcal
            Text(
                if (left >= 0) "осталось ${r0(left)}" else "перебор ${r0(-left)}",
                style = MaterialTheme.typography.labelLarge,
                color = if (left >= 0) cs.onPrimaryContainer else cs.error,
                modifier = Modifier.padding(bottom = 6.dp)
            )
        }
        Bar(kcal, s.kcalGoal.toDouble())
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MacroBlock("Белки", p, s.proteinGoal, Modifier.weight(1f))
            MacroBlock("Жиры", f, s.fatGoal, Modifier.weight(1f))
            MacroBlock("Углеводы", c, s.carbsGoal, Modifier.weight(1f))
        }
        if (notCounted > 0) {
            Spacer(Modifier.height(8.dp))
            Text(
                "Не посчитано приёмов: $notCounted — открой их и нажми «Посчитать».",
                style = MaterialTheme.typography.labelSmall,
                color = cs.onPrimaryContainer.copy(alpha = 0.8f)
            )
        }
    }
}

@Composable
private fun Bar(value: Double, goal: Double) {
    val cs = MaterialTheme.colorScheme
    val frac = if (goal <= 0) 0f else (value / goal).toFloat()
    LinearProgressIndicator(
        progress = { frac.coerceIn(0f, 1f) },
        modifier = Modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(RoundedCornerShape(50)),
        color = if (frac > 1.05f) cs.error else cs.primary,
        trackColor = cs.surface,
    )
}

@Composable
private fun MacroBlock(title: String, value: Double, goal: Int, modifier: Modifier) {
    val cs = MaterialTheme.colorScheme
    Column(modifier) {
        Text(title, style = MaterialTheme.typography.labelSmall, color = cs.onPrimaryContainer.copy(alpha = 0.8f))
        Text(
            "${r0(value)} / $goal г",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = cs.onPrimaryContainer
        )
        Spacer(Modifier.height(4.dp))
        Bar(value, goal.toDouble())
    }
}

// ======================= Редактор приёма пищи =======================

@Composable
private fun FoodEditor(e: FoodEntry) {
    val cs = MaterialTheme.colorScheme
    val analyzing = e.id in Store.foodAnalyzing
    val error = Store.foodErrors[e.id]
    var confirmDelete by remember { mutableStateOf(false) }
    val dictate = rememberDictation("Что в тарелке и сколько грамм?") { r ->
        val cur = Store.food.find { it.id == e.id }?.note ?: ""
        Store.updateFoodNote(e.id, appendDictated(cur, r))
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp, 4.dp, 16.dp, 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(key = "top") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { closeEditor(e.id) }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Назад") }
                Column(Modifier.weight(1f)) {
                    Text("Приём пищи", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Text(
                        "${e.date.dayTitle()} · ${e.time.timeText()}",
                        style = MaterialTheme.typography.bodySmall,
                        color = cs.onSurfaceVariant
                    )
                }
                IconButton(onClick = { confirmDelete = true }) {
                    Icon(Icons.Rounded.DeleteOutline, "Удалить", tint = cs.onSurfaceVariant)
                }
            }
        }
        if (e.photoPath.isNotBlank()) {
            item(key = "photo") {
                AsyncImage(
                    model = File(e.photoPath),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(cs.surfaceVariant)
                )
            }
        }
        item(key = "comment") {
            SoftCard {
                Text("Что тут?", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(
                    "Распиши состав с граммами (крупы и макароны — в сухом виде) или просто название блюда. Если оставить пустым, ИИ определит еду по фото.",
                    style = MaterialTheme.typography.bodySmall,
                    color = cs.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = e.note,
                    onValueChange = { Store.updateFoodNote(e.id, it) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 96.dp),
                    placeholder = { Text("рис 100г, куриное филе 200г, масло 5г") },
                    shape = RoundedCornerShape(14.dp),
                    trailingIcon = {
                        IconButton(onClick = dictate) { Icon(Icons.Rounded.Mic, "Надиктовать") }
                    },
                )
                Spacer(Modifier.height(10.dp))
                Button(
                    onClick = { Store.analyzeFood(e.id) },
                    enabled = !analyzing,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    if (analyzing) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = cs.onPrimary)
                    } else {
                        Icon(Icons.Rounded.AutoAwesome, null)
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        when {
                            analyzing -> "Считаю…"
                            e.analyzed -> "Пересчитать КБЖУ"
                            else -> "Посчитать КБЖУ"
                        }
                    )
                }
                if (e.analyzed && e.items.isNotEmpty() && !analyzing) {
                    Text(
                        "Пересчёт заменит продукты ниже.",
                        style = MaterialTheme.typography.labelSmall,
                        color = cs.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                if (error != null) {
                    Spacer(Modifier.height(6.dp))
                    Text(error, style = MaterialTheme.typography.bodySmall, color = cs.error)
                }
            }
        }

        if (e.items.isNotEmpty() || e.analyzed) {
            item(key = "title") {
                OutlinedTextField(
                    value = e.title,
                    onValueChange = { Store.updateFoodTitle(e.id, it) },
                    label = { Text("Название") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                )
            }
            item(key = "sum") {
                SoftCard(color = cs.primaryContainer) {
                    Text("Всего в этом приёме", style = MaterialTheme.typography.labelLarge, color = cs.onPrimaryContainer)
                    Text(
                        macroLine(e),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = cs.onPrimaryContainer
                    )
                }
            }
        }
        items(e.items, key = { it.id }) { item -> FoodItemRow(e.id, item) }
        item(key = "addItem") {
            TextButton(onClick = { Store.addFoodItem(e.id) }) {
                Icon(Icons.Rounded.Add, null, Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("Добавить продукт вручную")
            }
        }
        item(key = "done") {
            FilledTonalButton(
                onClick = { closeEditor(e.id) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(14.dp)
            ) { Text("Готово") }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Удалить приём пищи?") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    UiState.foodEditId = null
                    Store.deleteFood(e.id)
                }) { Text("Удалить", color = cs.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Отмена") } },
        )
    }
}

@Composable
private fun FoodItemRow(entryId: Long, item: FoodItem) {
    val cs = MaterialTheme.colorScheme
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(cs.surface)
            .padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) {
                BasicTextField(
                    value = item.name,
                    onValueChange = { Store.updateFoodItem(entryId, item.copy(name = it)) },
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = cs.onSurface, fontWeight = FontWeight.Medium),
                    cursorBrush = SolidColor(cs.primary),
                    singleLine = true,
                    decorationBox = { inner ->
                        Box {
                            if (item.name.isEmpty()) {
                                Text("Продукт", style = MaterialTheme.typography.bodyLarge, color = cs.onSurfaceVariant.copy(alpha = 0.5f))
                            }
                            inner()
                        }
                    },
                )
            }
            IconButton(onClick = { Store.removeFoodItem(entryId, item.id) }, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Rounded.Close, "Убрать", Modifier.size(18.dp), tint = cs.onSurfaceVariant)
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.padding(end = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            NumField("г", item.grams, item.id, Modifier.weight(1.1f)) { g ->
                // поменял граммы — КБЖУ пересчитываются пропорционально
                val k = if (item.grams > 0) g / item.grams else 1.0
                Store.updateFoodItem(
                    entryId,
                    if (item.grams > 0) item.copy(
                        grams = g,
                        kcal = item.kcal * k,
                        protein = item.protein * k,
                        fat = item.fat * k,
                        carbs = item.carbs * k,
                    ) else item.copy(grams = g)
                )
            }
            NumField("ккал", item.kcal, item.id, Modifier.weight(1.2f)) { Store.updateFoodItem(entryId, item.copy(kcal = it)) }
            NumField("Б", item.protein, item.id, Modifier.weight(1f)) { Store.updateFoodItem(entryId, item.copy(protein = it)) }
            NumField("Ж", item.fat, item.id, Modifier.weight(1f)) { Store.updateFoodItem(entryId, item.copy(fat = it)) }
            NumField("У", item.carbs, item.id, Modifier.weight(1f)) { Store.updateFoodItem(entryId, item.copy(carbs = it)) }
        }
    }
}

/** Числовое поле: держит свой текст, пока его редактируют, и отдаёт число наружу. */
@Composable
private fun NumField(label: String, value: Double, key: Long, modifier: Modifier, onValue: (Double) -> Unit) {
    val cs = MaterialTheme.colorScheme
    val shown = if (value == 0.0) "" else r1(value)
    var text by remember(key, label) { mutableStateOf(shown) }
    // значение поменялось снаружи (пересчёт по граммам) — обновляем текст
    val parsed = text.replace(',', '.').toDoubleOrNull() ?: 0.0
    if (Math.abs(parsed - value) > 0.05) text = shown
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        BasicTextField(
            value = text,
            onValueChange = { v ->
                val clean = v.replace(',', '.').filter { it.isDigit() || it == '.' }.take(7)
                text = clean
                onValue(clean.toDoubleOrNull() ?: 0.0)
            },
            textStyle = MaterialTheme.typography.bodyMedium.copy(
                color = cs.onSurface,
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.SemiBold
            ),
            cursorBrush = SolidColor(cs.primary),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            decorationBox = { inner ->
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(cs.surfaceVariant)
                        .padding(vertical = 8.dp, horizontal = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (text.isEmpty()) Text("0", style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant.copy(alpha = 0.4f))
                    inner()
                }
            },
        )
        Text(label, style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
    }
}

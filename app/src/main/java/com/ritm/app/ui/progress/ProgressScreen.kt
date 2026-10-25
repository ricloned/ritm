package com.ritm.app.ui.progress

import android.content.ActivityNotFoundException
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CompareArrows
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import com.ritm.app.data.BodyCheck
import com.ritm.app.data.Store
import com.ritm.app.ui.CheckDraft
import com.ritm.app.ui.EmptyState
import com.ritm.app.ui.Pill
import com.ritm.app.ui.SoftCard
import com.ritm.app.ui.UiState
import com.ritm.app.ui.fmtNum
import com.ritm.app.ui.pickDate
import com.ritm.app.ui.shortText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

private const val MAX_PHOTOS = 4

private val FULL_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMMM yyyy", com.ritm.app.ui.RU)

private fun deltaText(d: Double, unit: String): String {
    val sign = if (d > 0) "+" else if (d < 0) "−" else "±"
    return "$sign${fmtNum(kotlin.math.abs(Math.round(d * 10) / 10.0))} $unit"
}

private fun newDraft(): CheckDraft = CheckDraft(
    id = null,
    date = LocalDate.now(),
    weight = "",
    waist = "",
    note = "",
    photos = emptyList(),
    originalPhotos = emptyList(),
)

private fun editDraft(c: BodyCheck) = CheckDraft(
    id = c.id,
    date = c.date,
    weight = c.weight?.let { fmtNum(it) } ?: "",
    waist = c.waist?.let { fmtNum(it) } ?: "",
    note = c.note,
    photos = c.photos,
    originalPhotos = c.photos,
)

@Composable
fun ProgressScreen() {
    val editor = UiState.progressEditor
    if (editor != null) {
        BackHandler { cancelEditor() }
        CheckEditor(editor)
        return
    }
    if (UiState.progressCompare) {
        BackHandler { UiState.progressCompare = false }
        CompareView()
        return
    }

    LaunchedEffect(UiState.progressNew) {
        if (UiState.progressNew) {
            UiState.progressNew = false
            UiState.progressEditor = newDraft()
        }
    }

    val cs = MaterialTheme.colorScheme
    val checks = Store.checks.sortedByDescending { it.date }
    var viewing by remember { mutableStateOf<BodyCheck?>(null) }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(key = "summary") { SummaryCard(checks) }
        item(key = "buttons") {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = { UiState.progressEditor = newDraft() },
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(Icons.Rounded.Add, null)
                    Spacer(Modifier.width(6.dp))
                    Text("Отчёт недели")
                }
                FilledTonalButton(
                    onClick = {
                        val sorted = Store.checks.sortedBy { it.date }
                        if (UiState.compareA == null || Store.checks.none { it.id == UiState.compareA }) {
                            UiState.compareA = sorted.firstOrNull()?.id
                        }
                        if (UiState.compareB == null || Store.checks.none { it.id == UiState.compareB }) {
                            UiState.compareB = sorted.lastOrNull()?.id
                        }
                        UiState.progressCompare = true
                    },
                    enabled = checks.size >= 2,
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(Icons.Rounded.CompareArrows, null)
                    Spacer(Modifier.width(6.dp))
                    Text("Сравнить")
                }
            }
        }
        if (checks.isEmpty()) {
            item(key = "empty") {
                EmptyState(
                    "📸",
                    "Пока нет замеров",
                    "Раз в неделю, утром натощак: вес, талия и фото в одном месте и при одном свете. Так прогресс будет честно видно."
                )
            }
        }
        items(checks, key = { it.id }) { c ->
            val prev = checks.firstOrNull { it.date.isBefore(c.date) }
            CheckCard(c, prev) { viewing = c }
        }
    }

    viewing?.let { c ->
        CheckViewer(
            check = c,
            onDismiss = { viewing = null },
            onEdit = {
                viewing = null
                UiState.progressEditor = editDraft(c)
            },
            onDelete = {
                viewing = null
                Store.deleteCheck(c.id)
            },
        )
    }
}

private fun cancelEditor() {
    val d = UiState.progressEditor ?: return
    // фото, снятые в этом сеансе и не сохранённые, — удаляем
    d.photos.filter { it !in d.originalPhotos }.forEach { Store.deleteFile(it) }
    UiState.progressEditor = null
}

// ======================= Сводка и график =======================

@Composable
private fun SummaryCard(checksDesc: List<BodyCheck>) {
    val cs = MaterialTheme.colorScheme
    val withWeight = checksDesc.filter { it.weight != null }.sortedBy { it.date }
    val last = withWeight.lastOrNull()
    val first = withWeight.firstOrNull()
    val prev = withWeight.getOrNull(withWeight.size - 2)
    val lastW = last?.weight
    val prevW = prev?.weight
    val firstW = if (first != null && last != null && first.id != last.id) first.weight else null
    SoftCard(color = cs.primaryContainer) {
        if (lastW == null) {
            Text("Вес пока не записан", style = MaterialTheme.typography.titleMedium, color = cs.onPrimaryContainer)
            return@SoftCard
        }
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                fmtNum(lastW),
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                color = cs.onPrimaryContainer
            )
            Spacer(Modifier.width(6.dp))
            Text("кг", style = MaterialTheme.typography.titleMedium, color = cs.onPrimaryContainer, modifier = Modifier.padding(bottom = 6.dp))
            Spacer(Modifier.weight(1f))
            Column(horizontalAlignment = Alignment.End) {
                if (prevW != null) {
                    Text(
                        "за неделю ${deltaText(lastW - prevW, "кг")}",
                        style = MaterialTheme.typography.labelLarge,
                        color = cs.onPrimaryContainer
                    )
                }
                if (firstW != null) {
                    Text(
                        "с начала ${deltaText(lastW - firstW, "кг")}",
                        style = MaterialTheme.typography.labelLarge,
                        color = cs.onPrimaryContainer.copy(alpha = 0.8f)
                    )
                }
            }
        }
        if (withWeight.size >= 2) {
            Spacer(Modifier.height(12.dp))
            WeightChart(withWeight.map { it.weight ?: 0.0 })
            Spacer(Modifier.height(4.dp))
            Row {
                Text(
                    withWeight.first().date.shortText(),
                    style = MaterialTheme.typography.labelSmall,
                    color = cs.onPrimaryContainer.copy(alpha = 0.7f),
                    modifier = Modifier.weight(1f)
                )
                Text(
                    withWeight.last().date.shortText(),
                    style = MaterialTheme.typography.labelSmall,
                    color = cs.onPrimaryContainer.copy(alpha = 0.7f)
                )
            }
        }
    }
}

@Composable
private fun WeightChart(values: List<Double>) {
    val line = MaterialTheme.colorScheme.primary
    val dot = MaterialTheme.colorScheme.surface
    val min = values.minOrNull() ?: 0.0
    val max = values.maxOrNull() ?: 0.0
    val range = (max - min).takeIf { it > 0.0001 } ?: 1.0
    Row(verticalAlignment = Alignment.CenterVertically) {
        Canvas(
            Modifier
                .weight(1f)
                .height(110.dp)
        ) {
            val pad = 8.dp.toPx()
            val w = size.width - pad * 2
            val h = size.height - pad * 2
            val pts = values.mapIndexed { i, v ->
                val x = pad + if (values.size == 1) w / 2 else w * i / (values.size - 1)
                val y = pad + h - (h * ((v - min) / range)).toFloat()
                Offset(x, y)
            }
            val path = Path()
            pts.forEachIndexed { i, p -> if (i == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y) }
            drawPath(path, color = line, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round))
            pts.forEach {
                drawCircle(color = line, radius = 5.dp.toPx(), center = it)
                drawCircle(color = dot, radius = 2.5.dp.toPx(), center = it)
            }
        }
        Spacer(Modifier.width(8.dp))
        Column(Modifier.height(110.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Text(fmtNum(Math.round(max * 10) / 10.0), style = MaterialTheme.typography.labelSmall)
            Text(fmtNum(Math.round(min * 10) / 10.0), style = MaterialTheme.typography.labelSmall)
        }
    }
}

// ======================= Карточка замера =======================

@Composable
private fun CheckCard(c: BodyCheck, prev: BodyCheck?, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val workouts = Store.logs.count { l ->
        !l.date.isAfter(c.date) && (prev == null || l.date.isAfter(prev.date)) &&
            (prev != null || !l.date.isBefore(c.date.minusDays(6)))
    }
    SoftCard(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(c.date.format(FULL_DATE), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    "тренировок за неделю: $workouts",
                    style = MaterialTheme.typography.bodySmall,
                    color = cs.onSurfaceVariant
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                c.weight?.let { w ->
                    Text("${fmtNum(w)} кг", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    prev?.weight?.let { pw ->
                        Text(deltaText(w - pw, "кг"), style = MaterialTheme.typography.labelMedium, color = cs.primary)
                    }
                }
                c.waist?.let { wa ->
                    Text(
                        "талия ${fmtNum(wa)} см" + (prev?.waist?.let { " (${deltaText(wa - it, "см")})" } ?: ""),
                        style = MaterialTheme.typography.labelSmall,
                        color = cs.onSurfaceVariant
                    )
                }
            }
        }
        if (c.photos.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                c.photos.take(MAX_PHOTOS).forEach { p ->
                    AsyncImage(
                        model = File(p),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(3f / 4f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(cs.surfaceVariant)
                    )
                }
                repeat((MAX_PHOTOS - c.photos.size).coerceAtLeast(0)) { Spacer(Modifier.weight(1f)) }
            }
        }
        if (c.note.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(c.note, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
        }
    }
}

// ======================= Просмотр =======================

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CheckViewer(check: BodyCheck, onDismiss: () -> Unit, onEdit: () -> Unit, onDelete: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    var confirmDelete by remember { mutableStateOf(false) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(12.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(cs.surface)
                .padding(14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(check.date.format(FULL_DATE), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        listOfNotNull(
                            check.weight?.let { "${fmtNum(it)} кг" },
                            check.waist?.let { "талия ${fmtNum(it)} см" },
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.bodyMedium,
                        color = cs.onSurfaceVariant
                    )
                }
                IconButton(onClick = onDismiss) { Icon(Icons.Rounded.Close, "Закрыть") }
            }
            if (check.photos.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                val pager = rememberPagerState { check.photos.size }
                HorizontalPager(state = pager, modifier = Modifier.fillMaxWidth()) { page ->
                    AsyncImage(
                        model = File(check.photos[page]),
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(3f / 4f)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color.Black)
                    )
                }
                if (check.photos.size > 1) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        repeat(check.photos.size) { i ->
                            Box(
                                Modifier
                                    .padding(3.dp)
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(if (i == pager.currentPage) cs.primary else cs.outlineVariant)
                            )
                        }
                    }
                }
            }
            if (check.note.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(check.note, style = MaterialTheme.typography.bodyMedium)
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onEdit, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Rounded.Edit, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Изменить")
                }
                OutlinedButton(onClick = { confirmDelete = true }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Rounded.DeleteOutline, null, Modifier.size(18.dp), tint = cs.error)
                    Spacer(Modifier.width(6.dp))
                    Text("Удалить", color = cs.error)
                }
            }
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Удалить замер?") },
            text = { Text("Замер и его фото будут удалены.") },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; onDelete() }) { Text("Удалить", color = cs.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Отмена") } },
        )
    }
}

// ======================= Редактор замера =======================

@Composable
private fun CheckEditor(d: CheckDraft) {
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    var pendingPath by rememberSaveable { mutableStateOf<String?>(null) }

    fun update(f: (CheckDraft) -> CheckDraft) {
        UiState.progressEditor?.let { UiState.progressEditor = f(it) }
    }

    fun newFile(): File {
        val dir = File(ctx.filesDir, "body").apply { mkdirs() }
        return File(dir, "body_${System.currentTimeMillis()}.jpg")
    }

    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val p = pendingPath
        if (p != null) {
            if (ok && File(p).length() > 0) update { it.copy(photos = it.photos + p) } else File(p).delete()
        }
        pendingPath = null
    }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            val target = newFile()
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
                if (ok) update { it.copy(photos = it.photos + target.absolutePath) }
                else Toast.makeText(ctx, "Не удалось загрузить фото", Toast.LENGTH_SHORT).show()
            }
        }
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp, 4.dp, 16.dp, 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { cancelEditor() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Назад") }
                Text(
                    if (d.id == null) "Отчёт недели" else "Изменить замер",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
        item {
            OutlinedButton(onClick = { pickDate(ctx, d.date) { nd -> update { it.copy(date = nd) } } }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Rounded.CalendarMonth, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(d.date.format(FULL_DATE))
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = d.weight,
                    onValueChange = { v -> update { it.copy(weight = v.replace(',', '.').filter { c -> c.isDigit() || c == '.' }.take(6)) } },
                    label = { Text("Вес, кг") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp),
                )
                OutlinedTextField(
                    value = d.waist,
                    onValueChange = { v -> update { it.copy(waist = v.replace(',', '.').filter { c -> c.isDigit() || c == '.' }.take(6)) } },
                    label = { Text("Талия, см") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp),
                )
            }
        }
        item {
            Text(
                "Фото (до $MAX_PHOTOS): спереди, сбоку, сзади — в одном месте, при одном свете и в одной позе.",
                style = MaterialTheme.typography.bodySmall,
                color = cs.onSurfaceVariant
            )
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                d.photos.forEach { p ->
                    Box(
                        Modifier
                            .weight(1f)
                            .aspectRatio(3f / 4f)
                    ) {
                        AsyncImage(
                            model = File(p),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(12.dp))
                                .background(cs.surfaceVariant)
                        )
                        Box(
                            Modifier
                                .align(Alignment.TopEnd)
                                .padding(4.dp)
                                .size(26.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.5f))
                                .clickable {
                                    if (p !in d.originalPhotos) Store.deleteFile(p)
                                    update { it.copy(photos = it.photos - p) }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Rounded.Close, "Убрать", Modifier.size(16.dp), tint = Color.White)
                        }
                    }
                }
                repeat((MAX_PHOTOS - d.photos.size).coerceAtLeast(0)) { Spacer(Modifier.weight(1f)) }
            }
        }
        if (d.photos.size < MAX_PHOTOS) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    FilledTonalButton(
                        onClick = {
                            val f = newFile()
                            pendingPath = f.absolutePath
                            val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", f)
                            try {
                                camera.launch(uri)
                            } catch (_: ActivityNotFoundException) {
                                pendingPath = null
                                Toast.makeText(ctx, "Камера не найдена", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(Icons.Rounded.CameraAlt, null)
                        Spacer(Modifier.width(6.dp))
                        Text("Сфоткать")
                    }
                    FilledTonalButton(
                        onClick = { gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(Icons.Rounded.PhotoLibrary, null)
                        Spacer(Modifier.width(6.dp))
                        Text("Из галереи")
                    }
                }
            }
        }
        item {
            OutlinedTextField(
                value = d.note,
                onValueChange = { v -> update { it.copy(note = v) } },
                label = { Text("Заметка (самочувствие, питание…)") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                shape = RoundedCornerShape(14.dp),
            )
        }
        item {
            Button(
                onClick = {
                    val w = d.weight.toDoubleOrNull()
                    if (w == null && d.photos.isEmpty()) {
                        Toast.makeText(ctx, "Добавь вес или фото", Toast.LENGTH_SHORT).show()
                    } else {
                    Store.saveCheck(
                        BodyCheck(
                            id = d.id ?: Store.newId(),
                            date = d.date,
                            weight = w,
                            waist = d.waist.toDoubleOrNull(),
                            photos = d.photos,
                            note = d.note.trim(),
                        )
                    )
                    UiState.progressEditor = null
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                shape = RoundedCornerShape(16.dp)
            ) { Text("Сохранить") }
        }
    }
}

// ======================= Сравнение =======================

@Composable
private fun CompareView() {
    val cs = MaterialTheme.colorScheme
    val sorted = Store.checks.sortedBy { it.date }
    val a = sorted.firstOrNull { it.id == UiState.compareA } ?: sorted.firstOrNull()
    val b = sorted.firstOrNull { it.id == UiState.compareB } ?: sorted.lastOrNull()
    var photoIndex by remember { mutableIntStateOf(0) }
    var overlay by remember { mutableStateOf(false) }
    var mix by remember { mutableFloatStateOf(0.5f) }
    val maxPhotos = maxOf(a?.photos?.size ?: 0, b?.photos?.size ?: 0)

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp, 4.dp, 16.dp, 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { UiState.progressCompare = false }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Назад") }
                Text("Сравнение", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            }
        }
        item { DatePickerRow("Было", sorted, a?.id) { UiState.compareA = it } }
        item { DatePickerRow("Стало", sorted, b?.id) { UiState.compareB = it } }

        if (a != null && b != null) {
            item {
                SoftCard(color = cs.primaryContainer) {
                    val days = ChronoUnit.DAYS.between(a.date, b.date)
                    Text(
                        "Разница за $days дн.",
                        style = MaterialTheme.typography.labelLarge,
                        color = cs.onPrimaryContainer
                    )
                    Spacer(Modifier.height(4.dp))
                    Row {
                        if (a.weight != null && b.weight != null) {
                            Column(Modifier.weight(1f)) {
                                Text("Вес", style = MaterialTheme.typography.labelSmall, color = cs.onPrimaryContainer)
                                Text(
                                    deltaText(b.weight - a.weight, "кг"),
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = cs.onPrimaryContainer
                                )
                                Text(
                                    "${fmtNum(a.weight)} → ${fmtNum(b.weight)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = cs.onPrimaryContainer
                                )
                            }
                        }
                        if (a.waist != null && b.waist != null) {
                            Column(Modifier.weight(1f)) {
                                Text("Талия", style = MaterialTheme.typography.labelSmall, color = cs.onPrimaryContainer)
                                Text(
                                    deltaText(b.waist - a.waist, "см"),
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = cs.onPrimaryContainer
                                )
                                Text(
                                    "${fmtNum(a.waist)} → ${fmtNum(b.waist)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = cs.onPrimaryContainer
                                )
                            }
                        }
                    }
                }
            }

            if (maxPhotos > 0) {
                item {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Pill("Рядом", !overlay) { overlay = false }
                        Pill("Наложение", overlay) { overlay = true }
                        if (maxPhotos > 1) {
                            repeat(maxPhotos) { i -> Pill("Фото ${i + 1}", photoIndex == i) { photoIndex = i } }
                        }
                    }
                }
                item {
                    val pa = a.photos.getOrNull(photoIndex) ?: a.photos.lastOrNull()
                    val pb = b.photos.getOrNull(photoIndex) ?: b.photos.lastOrNull()
                    if (!overlay) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ComparePhoto(pa, a.date.shortText(), Modifier.weight(1f))
                            ComparePhoto(pb, b.date.shortText(), Modifier.weight(1f))
                        }
                    } else {
                        Column {
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(3f / 4f)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(Color.Black)
                            ) {
                                if (pa != null) {
                                    AsyncImage(
                                        model = File(pa),
                                        contentDescription = null,
                                        contentScale = ContentScale.Fit,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                                if (pb != null) {
                                    AsyncImage(
                                        model = File(pb),
                                        contentDescription = null,
                                        contentScale = ContentScale.Fit,
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .graphicsLayer { alpha = mix }
                                    )
                                }
                            }
                            Slider(value = mix, onValueChange = { mix = it })
                            Row {
                                Text(a.date.shortText(), style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
                                Text(b.date.shortText(), style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.End)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ComparePhoto(path: String?, label: String, modifier: Modifier) {
    val cs = MaterialTheme.colorScheme
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(3f / 4f)
                .clip(RoundedCornerShape(14.dp))
                .background(cs.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            if (path != null) {
                AsyncImage(
                    model = File(path),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Text("нет фото", style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(label, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun DatePickerRow(title: String, checks: List<BodyCheck>, selected: Long?, onSelect: (Long) -> Unit) {
    val cs = MaterialTheme.colorScheme
    Column {
        Text(title, style = MaterialTheme.typography.labelLarge, color = cs.primary, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(checks, key = { it.id }) { c ->
                val sel = c.id == selected
                Column(
                    Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (sel) cs.primary else cs.surface)
                        .clickable { onSelect(c.id) }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        c.date.shortText(),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (sel) cs.onPrimary else cs.onSurface
                    )
                    Text(
                        c.weight?.let { "${fmtNum(it)} кг" } ?: "—",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (sel) cs.onPrimary else cs.onSurfaceVariant
                    )
                }
            }
        }
    }
}

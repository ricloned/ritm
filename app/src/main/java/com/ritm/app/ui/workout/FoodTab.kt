package com.ritm.app.ui.workout

import android.content.ActivityNotFoundException
import android.widget.Toast
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import com.ritm.app.ui.UiState
import com.ritm.app.ui.swipeHorizontal
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import com.ritm.app.data.FoodEntry
import com.ritm.app.data.Store
import com.ritm.app.ui.EmptyState
import com.ritm.app.ui.dayTitle
import com.ritm.app.ui.pickDate
import com.ritm.app.ui.timeText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate

@Composable
fun FoodTab() {
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    val date = UiState.foodDate
    val today = UiState.today
    var pendingPath by rememberSaveable { mutableStateOf<String?>(null) }
    var opened by remember { mutableStateOf<FoodEntry?>(null) }
    val entries = Store.food.filter { it.date == date }.sortedBy { it.time }

    fun newFile(): File {
        val dir = File(ctx.filesDir, "food").apply { mkdirs() }
        return File(dir, "food_${System.currentTimeMillis()}.jpg")
    }

    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val p = pendingPath
        if (p != null) {
            if (ok && File(p).length() > 0) Store.addFood(UiState.foodDate, p) else File(p).delete()
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
                if (ok) Store.addFood(forDate, target.absolutePath)
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
                        if (entries.isEmpty()) "ничего не записано" else "приёмов пищи: ${entries.size}",
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
        }
        if (entries.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                EmptyState(
                    "🍽️",
                    if (date == today) "Сегодня ещё ничего" else "За этот день нет фото",
                    "Фоткай каждый приём пищи. Напоминания можно настроить в настройках. Листай дни свайпом влево-вправо."
                )
            }
        }
        items(entries, key = { it.id }) { e ->
            Column(
                Modifier
                    .clip(RoundedCornerShape(18.dp))
                    .background(cs.surface)
                    .clickable { opened = e }
            ) {
                Box {
                    AsyncImage(
                        model = File(e.photoPath),
                        contentDescription = e.note.ifBlank { "Еда" },
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                            .background(cs.surfaceVariant)
                    )
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
                if (e.note.isNotBlank()) {
                    Text(
                        e.note,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }

    opened?.let { e ->
        var note by remember(e.id) { mutableStateOf(e.note) }
        AlertDialog(
            onDismissRequest = { opened = null },
            title = { Text("Приём пищи · ${e.time.timeText()}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    AsyncImage(
                        model = File(e.photoPath),
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 340.dp)
                            .clip(RoundedCornerShape(16.dp))
                    )
                    OutlinedTextField(
                        value = note,
                        onValueChange = { note = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Подпись, например: гречка с курицей") },
                        shape = RoundedCornerShape(14.dp),
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    Store.updateFoodNote(e.id, note.trim())
                    opened = null
                }) { Text("Сохранить") }
            },
            dismissButton = {
                TextButton(onClick = {
                    Store.deleteFood(e.id)
                    opened = null
                }) { Text("Удалить", color = cs.error) }
            },
        )
    }
}

package com.ritm.app.update

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ritm.app.BuildConfig

private fun sizeText(bytes: Long): String =
    if (bytes <= 0) "" else String.format(java.util.Locale.US, "%.1f МБ", bytes / 1024.0 / 1024.0)

/** Плашка сверху: «Доступна новая версия». */
@Composable
fun UpdateBanner(onOpenSettings: () -> Unit) {
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    val state = Updater.state
    val r = Updater.latest
    val visible = r != null && (r.build != Updater.dismissedBuild || state !is Updater.State.Available)
    var showNotes by remember { mutableStateOf(false) }

    AnimatedVisibility(visible = visible) {
        if (r == null) return@AnimatedVisibility
        Column(
            Modifier
                .padding(horizontal = 16.dp, vertical = 4.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(cs.primaryContainer)
                .padding(start = 14.dp, end = 4.dp, top = 8.dp, bottom = 8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.SystemUpdate, null, tint = cs.primary)
                Spacer(Modifier.width(10.dp))
                Column(
                    Modifier
                        .weight(1f)
                        .clickable { showNotes = !showNotes }
                ) {
                    Text(
                        "Доступна версия ${r.versionName}",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = cs.onPrimaryContainer
                    )
                    Text(
                        when (state) {
                            is Updater.State.Downloading -> "Скачиваю… ${(Updater.progress * 100).toInt()}%"
                            is Updater.State.Ready -> "Скачано — нажми «Установить»"
                            else -> if (r.notes.isNotBlank()) (if (showNotes) "скрыть изменения" else "что нового ▾") else "сборка ${r.build}"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = cs.onPrimaryContainer.copy(alpha = 0.8f)
                    )
                }
                when (state) {
                    is Updater.State.Downloading -> CircularProgressIndicator(
                        progress = { Updater.progress },
                        modifier = Modifier
                            .padding(horizontal = 12.dp)
                            .size(22.dp),
                        strokeWidth = 2.5.dp,
                        color = cs.primary,
                    )
                    is Updater.State.Ready -> Button(onClick = { Updater.install(ctx) }) { Text("Установить") }
                    else -> Button(onClick = { Updater.download(ctx) }) { Text("Обновить") }
                }
                if (state is Updater.State.Available) {
                    IconButton(onClick = { Updater.dismiss(ctx) }) {
                        Icon(Icons.Rounded.Close, "Позже", tint = cs.onPrimaryContainer.copy(alpha = 0.7f))
                    }
                } else {
                    Spacer(Modifier.width(8.dp))
                }
            }
            if (showNotes && r.notes.isNotBlank()) {
                Text(
                    r.notes,
                    modifier = Modifier
                        .padding(top = 8.dp, end = 10.dp)
                        .heightIn(max = 220.dp)
                        .verticalScroll(rememberScrollState()),
                    style = MaterialTheme.typography.bodySmall,
                    color = cs.onPrimaryContainer
                )
            }
            if (state is Updater.State.Ready && !Updater.canInstall(ctx)) {
                Text(
                    "Разреши «Установку из этого источника» для Ритма — откроются настройки, потом вернись и нажми «Установить».",
                    modifier = Modifier.padding(top = 6.dp, end = 10.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = cs.onPrimaryContainer
                )
            }
        }
    }
}

/** Раздел «Обновления» в настройках. */
@Composable
fun UpdateSection() {
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    val state = Updater.state
    val r = Updater.latest
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "Установлена версия ${BuildConfig.VERSION_NAME} (сборка ${BuildConfig.VERSION_CODE})",
            style = MaterialTheme.typography.bodyMedium
        )
        when (state) {
            is Updater.State.Checking -> Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
                Text("Проверяю…", style = MaterialTheme.typography.bodySmall)
            }
            is Updater.State.UpToDate -> Text("✅ У тебя последняя версия", style = MaterialTheme.typography.bodySmall, color = cs.primary)
            is Updater.State.Error -> Text("❌ ${state.message}", style = MaterialTheme.typography.bodySmall, color = cs.error)
            else -> {}
        }
        if (r != null) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(cs.primaryContainer)
                    .padding(12.dp)
            ) {
                Text(
                    "Новая версия ${r.versionName} (сборка ${r.build}) ${sizeText(r.size)}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = cs.onPrimaryContainer
                )
                if (r.notes.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        r.notes,
                        style = MaterialTheme.typography.bodySmall,
                        color = cs.onPrimaryContainer,
                        maxLines = 12,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(Modifier.height(8.dp))
                if (state is Updater.State.Downloading) {
                    LinearProgressIndicator(
                        progress = { Updater.progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(50)),
                    )
                } else {
                    Button(
                        onClick = { if (state is Updater.State.Ready) Updater.install(ctx) else Updater.download(ctx) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp)
                    ) { Text(if (state is Updater.State.Ready) "Установить" else "Скачать и установить") }
                }
            }
        }
        FilledTonalButton(
            onClick = { Updater.check(ctx) },
            enabled = state !is Updater.State.Checking && state !is Updater.State.Downloading,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp)
        ) { Text("Проверить обновления") }
        if (!Updater.canInstall(ctx)) {
            TextButton(onClick = { Updater.openInstallPermission(ctx) }) {
                Text("Разрешить установку обновлений", style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

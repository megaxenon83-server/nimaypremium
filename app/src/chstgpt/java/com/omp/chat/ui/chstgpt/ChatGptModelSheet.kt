package com.omp.chat.ui.chstgpt

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.omp.chat.data.ModelInfo

/**
 * Model picker built from the live `/api/models` list. Recently used models
 * come first, based on the real per-PC usage counts, never a hardcoded list.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatGptModelSheet(
    models: List<ModelInfo>,
    current: ModelInfo?,
    usageCounts: Map<String, Int>,
    modelsLoading: Boolean,
    onRefresh: () -> Unit,
    onSelect: (ModelInfo) -> Unit,
    onDismiss: () -> Unit
) {
    val c = LocalChatGptColors.current
    val strings = com.omp.chat.ui.LocalStrings.current
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheet,
        containerColor = c.surfaceElevated,
        contentColor = c.textPrimary,
        dragHandle = {
            Box(
                Modifier
                    .padding(top = 10.dp, bottom = 8.dp)
                    .size(width = 34.dp, height = 4.dp)
                    .clip(CircleShape)
                    .background(c.divider)
            )
        }
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 8.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(strings.models, style = MaterialTheme.typography.titleMedium, color = c.textPrimary)
                    Text(
                        current?.name ?: strings.noModels,
                        style = MaterialTheme.typography.labelSmall,
                        color = c.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                GptIconButton(
                    icon = Icons.Outlined.Refresh,
                    contentDescription = "Oppdater",
                    onClick = onRefresh,
                    tint = if (modelsLoading) c.textTertiary else c.textSecondary,
                    enabled = !modelsLoading
                )
            }

            if (models.isEmpty()) {
                Text(
                    strings.noModels,
                    style = MaterialTheme.typography.bodyMedium,
                    color = c.textSecondary,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp)
                )
            } else {
                val ranked = models
                    .filter { (usageCounts[it.key] ?: 0) > 0 }
                    .sortedByDescending { usageCounts[it.key] ?: 0 }
                    .map { it.key }
                    .toSet()
                val recent = models.filter { it.key in ranked }
                val others = models.filter { it.key !in ranked }

                LazyColumn(
                    modifier = Modifier.heightIn(max = 470.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    if (recent.isNotEmpty()) {
                        item(key = "recent-header") {
                            GptSheetSection(if (strings.lang == "en") "Recently used" else "Nylig brukt")
                        }
                        items(recent, key = { "recent-${it.key}" }) { model ->
                            GptModelRow(model, current, usageCounts[model.key] ?: 0) {
                                onSelect(model)
                            }
                        }
                    }
                    if (others.isNotEmpty()) {
                        item(key = "others-header") {
                            GptSheetSection(if (strings.lang == "en") "All models" else "Alle modeller")
                        }
                        items(others, key = { "all-${it.key}" }) { model ->
                            GptModelRow(model, current, usageCounts[model.key] ?: 0) {
                                onSelect(model)
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun GptSheetSection(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = LocalChatGptColors.current.textTertiary,
        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
    )
}

@Composable
private fun GptModelRow(model: ModelInfo, current: ModelInfo?, uses: Int, onClick: () -> Unit) {
    val c = LocalChatGptColors.current
    val isCurrent = current?.key == model.key
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (isCurrent) c.selected else androidx.compose.ui.graphics.Color.Transparent)
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            Modifier
                .size(30.dp)
                .clip(CircleShape)
                .background(c.surface),
            contentAlignment = Alignment.Center
        ) {
            Text(
                model.provider.take(1).uppercase().ifBlank { "M" },
                style = MaterialTheme.typography.labelMedium,
                color = c.textSecondary
            )
        }
        Column(Modifier.weight(1f)) {
            Text(
                model.name.ifBlank { model.id },
                style = MaterialTheme.typography.bodyMedium,
                color = c.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                buildString {
                    append(model.provider)
                    if (model.reasoning) append(" · reasoning")
                    if (model.supportsImages) append(" · bilder")
                    if (uses > 0) append(" · $uses×")
                },
                style = MaterialTheme.typography.labelSmall,
                color = c.textTertiary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (isCurrent) {
            Icon(
                Icons.Outlined.Check,
                contentDescription = null,
                tint = c.textPrimary,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

/** Simple, honest attachment hints: the actions come from the activity. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatGptAttachSheet(
    onPhoto: () -> Unit,
    onCamera: () -> Unit,
    onFile: () -> Unit,
    onVoice: () -> Unit,
    onDismiss: () -> Unit
) {
    val c = LocalChatGptColors.current
    val strings = com.omp.chat.ui.LocalStrings.current
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = c.surfaceElevated,
        dragHandle = {
            Box(
                Modifier
                    .padding(top = 10.dp, bottom = 8.dp)
                    .size(width = 34.dp, height = 4.dp)
                    .clip(CircleShape)
                    .background(c.divider)
            )
        }
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 12.dp, vertical = 4.dp)
        ) {
            GptAttachRow(Icons.Filled.Image, strings.photo, strings.attachFromGallery) { onDismiss(); onPhoto() }
            GptAttachRow(Icons.Filled.CameraAlt, strings.camera, strings.takePhoto) { onDismiss(); onCamera() }
            GptAttachRow(Icons.Filled.Folder, strings.file, strings.pickFile) { onDismiss(); onFile() }
            if (com.omp.chat.data.FeatureFlags.VoiceEnabled) {
                GptAttachRow(Icons.Filled.Mic, strings.voice, strings.recordVoice) { onDismiss(); onVoice() }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun GptAttachRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    val c = LocalChatGptColors.current
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(c.surface),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = c.textSecondary,
                modifier = Modifier.size(18.dp)
            )
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, color = c.textPrimary)
            Text(subtitle, style = MaterialTheme.typography.labelSmall, color = c.textSecondary)
        }
    }
}

@Composable
internal fun GptApprovalRow(
    label: String,
    description: String,
    selected: Boolean,
    destructive: Boolean,
    onClick: () -> Unit
) {
    val c = LocalChatGptColors.current
    val border = when {
        destructive -> c.error.copy(alpha = 0.5f)
        selected -> c.textPrimary
        else -> c.divider
    }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) c.selected else c.surface)
            .border(1.dp, border, RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(horizontal = 13.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                label,
                style = MaterialTheme.typography.bodyMedium,
                color = if (destructive) c.error else c.textPrimary
            )
            if (description.isNotBlank()) {
                Text(description, style = MaterialTheme.typography.labelSmall, color = c.textSecondary)
            }
        }
        if (selected) {
            Icon(Icons.Outlined.Check, contentDescription = null, tint = c.textPrimary, modifier = Modifier.size(17.dp))
        }
    }
}

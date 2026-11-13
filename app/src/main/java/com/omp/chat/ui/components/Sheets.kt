package com.omp.chat.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.omp.chat.data.ModelInfo
import com.omp.chat.data.TerminalEntry
import com.omp.chat.data.UiRequest
import com.omp.chat.ui.LocalStrings
import com.omp.chat.ui.theme.MonoStyle

// ---------------------------------------------------------------------------
// Approval / OMP questions
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApprovalSheet(
    request: UiRequest,
    onAnswer: (value: String?, confirmed: Boolean?, cancelled: Boolean) -> Unit,
    onAnswers: (List<Pair<String, List<String>>>) -> Unit,
    onDismiss: () -> Unit
) {
    val strings = LocalStrings.current
    val scheme = MaterialTheme.colorScheme
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var input by remember(request.id) { mutableStateOf(request.prefill) }
    var selected by remember(request.id) { mutableStateOf<Set<String>>(emptySet()) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = state,
        containerColor = scheme.surfaceContainer,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OmpAvatar(size = 28.dp)
                Column(Modifier.weight(1f)) {
                    Text(
                        strings.approvalTitle,
                        style = MaterialTheme.typography.titleMedium,
                        color = scheme.onSurface
                    )
                    Text(
                        request.title.ifBlank { strings.waitingApproval },
                        style = MaterialTheme.typography.labelMedium,
                        color = scheme.onSurfaceVariant
                    )
                }
            }

            if (request.message.isNotBlank()) {
                Surface(color = scheme.surfaceContainerHigh, shape = RoundedCornerShape(12.dp)) {
                    SelectionContainer {
                        Text(
                            request.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = scheme.onSurface,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
            }

            when (request.method) {
                "select" -> {
                    request.options.forEach { option ->
                        val deny = option.contains("deny", true) || option.contains("avslÃ¥", true)
                        OptionRow(
                            label = option,
                            selected = false,
                            destructive = deny,
                            onClick = { onAnswer(option, null, false) }
                        )
                    }
                }

                "confirm" -> {
                    OptionRow(
                        label = strings.approve,
                        selected = false,
                        onClick = { onAnswer(null, true, false) }
                    )
                    OptionRow(
                        label = strings.deny,
                        selected = false,
                        destructive = true,
                        onClick = { onAnswer(null, false, false) }
                    )
                }

                "input", "editor" -> {
                    OutlinedTextField(
                        value = input,
                        onValueChange = { input = it },
                        placeholder = { Text(request.placeholder.ifBlank { strings.yourAnswer }) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 96.dp),
                        shape = RoundedCornerShape(14.dp)
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                            Text(strings.cancel)
                        }
                        Button(
                            onClick = { onAnswer(input, null, false) },
                            modifier = Modifier.weight(1f),
                            enabled = input.isNotBlank()
                        ) {
                            Text(strings.submit)
                        }
                    }
                }

                "ask" -> {
                    val questions = request.questions
                    if (questions.isEmpty()) {
                        Text(strings.waitingApproval, color = scheme.onSurfaceVariant)
                        Button(onClick = { onAnswer("", null, false) }, modifier = Modifier.fillMaxWidth()) {
                            Text(strings.answer)
                        }
                    } else {
                        Column(
                            modifier = Modifier
                                .heightIn(max = 380.dp)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            questions.forEach { question ->
                                Text(
                                    question.header.ifBlank { question.question },
                                    style = MaterialTheme.typography.titleSmall,
                                    color = scheme.onSurface
                                )
                                question.options.forEach { option ->
                                    val isSelected = selected.contains("${question.id}:${option.label}")
                                    OptionRow(
                                        label = option.label,
                                        description = option.description,
                                        selected = isSelected,
                                        onClick = {
                                            selected = if (question.multi) {
                                                val key = "${question.id}:${option.label}"
                                                if (selected.contains(key)) selected - key else selected + key
                                            } else {
                                                setOf("${question.id}:${option.label}")
                                            }
                                        }
                                    )
                                }
                            }
                        }
                        Button(
                            onClick = {
                                val answers = questions.map { question ->
                                    question.id to selected
                                        .filter { it.startsWith("${question.id}:") }
                                        .map { it.substringAfter(':') }
                                }
                                onAnswers(answers)
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(strings.submit)
                        }
                    }
                }

                else -> {
                    Text(
                        request.message.ifBlank { request.title },
                        color = scheme.onSurfaceVariant
                    )
                    Button(onClick = { onAnswer(null, null, true) }, modifier = Modifier.fillMaxWidth()) {
                        Text(strings.close)
                    }
                }
            }

            Text(
                strings.approvalHint,
                style = MaterialTheme.typography.labelSmall,
                color = scheme.onSurfaceVariant.copy(alpha = 0.8f)
            )
        }
    }
}

@Composable
private fun OptionRow(
    label: String,
    selected: Boolean,
    description: String = "",
    destructive: Boolean = false,
    onClick: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val border = when {
        destructive -> scheme.error.copy(alpha = 0.45f)
        selected -> scheme.primary
        else -> scheme.outlineVariant
    }
    Surface(
        color = if (selected) scheme.primary.copy(alpha = 0.12f) else scheme.surfaceContainerHigh,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, border, RoundedCornerShape(14.dp))
            .clip(RoundedCornerShape(14.dp))
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    label,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (destructive) scheme.error else scheme.onSurface
                )
                if (description.isNotBlank()) {
                    Text(
                        description,
                        style = MaterialTheme.typography.labelSmall,
                        color = scheme.onSurfaceVariant
                    )
                }
            }
            if (selected) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = null,
                    tint = scheme.primary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Model picker
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelSheet(
    models: List<ModelInfo>,
    current: ModelInfo?,
    usageCounts: Map<String, Int> = emptyMap(),
    onSelect: (ModelInfo) -> Unit,
    onDismiss: () -> Unit
) {
    val strings = LocalStrings.current
    val scheme = MaterialTheme.colorScheme
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = scheme.surfaceContainer,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding()) {
            Text(
                strings.models,
                style = MaterialTheme.typography.titleMedium,
                color = scheme.onSurface,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
            )
            if (models.isEmpty()) {
                Text(
                    strings.noModels,
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant,
                    modifier = Modifier.padding(20.dp)
                )
            }
            LazyColumn(
                modifier = Modifier.heightIn(max = 460.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    horizontal = 12.dp,
                    vertical = 8.dp
                ),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                val frequentKeys = models
                    .filter { (usageCounts[it.key] ?: 0) > 0 }
                    .sortedByDescending { usageCounts[it.key] ?: 0 }
                    .take(5)
                    .map { it.key }
                    .toSet()
                val frequentModels = models.filter { it.key in frequentKeys }
                val otherModels = models.filter { it.key !in frequentKeys }

                if (frequentModels.isNotEmpty()) {
                    item {
                        Text(
                            "Mest brukte",
                            style = MaterialTheme.typography.labelMedium,
                            color = scheme.primary,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                    items(frequentModels, key = { it.key }) { model ->
                        ModelRow(model, current, scheme, strings, onSelect, onDismiss)
                    }
                }
                if (otherModels.isNotEmpty()) {
                    item {
                        Text(
                            "Andre tilgjengelige modeller",
                            style = MaterialTheme.typography.labelMedium,
                            color = scheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                    items(otherModels, key = { it.key }) { model ->
                        ModelRow(model, current, scheme, strings, onSelect, onDismiss)
                    }
                }
            }
            Spacer(Modifier.size(12.dp))
        }
    }
}

@Composable
private fun ModelRow(
    model: ModelInfo,
    current: ModelInfo?,
    scheme: androidx.compose.material3.ColorScheme,
    strings: com.omp.chat.ui.Strings,
    onSelect: (ModelInfo) -> Unit,
    onDismiss: () -> Unit
) {
    val isCurrent = current?.key == model.key
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (isCurrent) scheme.primary.copy(alpha = 0.1f) else scheme.surface)
            .clickable {
                onSelect(model)
                onDismiss()
            }
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                model.name.ifBlank { model.id },
                style = MaterialTheme.typography.bodyLarge,
                color = scheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            val meta = buildList {
                add(model.provider)
                model.contextWindow?.let { add("${shortNumber(it)} ctx") }
                if (model.supportsImages) add(strings.vision)
                if (model.reasoning) add(strings.reasoningTag)
            }.joinToString(" \u2022 ")
            Text(
                meta,
                style = MaterialTheme.typography.labelSmall,
                color = scheme.onSurfaceVariant
            )
        }
        if (isCurrent) {
            Icon(
                Icons.Default.Check,
                contentDescription = strings.currentModel,
                tint = scheme.primary,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Terminal
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TerminalSheet(
    entries: List<TerminalEntry>,
    terminalEnabled: Boolean,
    onRun: (String) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit
) {
    val strings = LocalStrings.current
    val scheme = MaterialTheme.colorScheme
    var command by remember { mutableStateOf("") }
    val scroll = rememberScrollState()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = scheme.surfaceContainer,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    OmpIcons.Terminal,
                    contentDescription = null,
                    tint = scheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    strings.terminal,
                    style = MaterialTheme.typography.titleMedium,
                    color = scheme.onSurface
                )
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onClear, enabled = entries.isNotEmpty()) {
                    Text(strings.clear)
                }
            }
            Text(
                strings.terminalWarn,
                style = MaterialTheme.typography.labelSmall,
                color = scheme.onSurfaceVariant
            )
            Spacer(Modifier.size(10.dp))

            Surface(
                color = scheme.surfaceContainerLowest,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 120.dp, max = 300.dp)
                    .border(1.dp, scheme.outlineVariant, RoundedCornerShape(12.dp))
            ) {
                Column(
                    Modifier
                        .verticalScroll(scroll)
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (entries.isEmpty()) {
                        Text(
                            "â€”",
                            style = MonoStyle,
                            color = scheme.onSurfaceVariant
                        )
                    }
                    entries.forEach { entry ->
                        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(
                                "$ ${entry.command}",
                                style = MonoStyle.copy(fontWeight = FontWeight.Medium),
                                color = scheme.primary
                            )
                            if (entry.running) {
                                Text(strings.running, style = MonoStyle, color = scheme.onSurfaceVariant)
                            } else {
                                SelectionContainer {
                                    Text(
                                        entry.output.ifBlank { "(ingen utdata)" },
                                        style = MonoStyle,
                                        color = if (entry.isError) scheme.error else scheme.onSurface
                                    )
                                }
                                Text(
                                    "${strings.exitCode}: ${entry.exitCode ?: "?"}",
                                    style = MonoStyle.copy(fontSize = 10.sp),
                                    color = scheme.onSurfaceVariant.copy(alpha = 0.7f)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.size(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = command,
                    onValueChange = { command = it },
                    placeholder = { Text(strings.terminalHint, style = MaterialTheme.typography.bodySmall) },
                    singleLine = true,
                    textStyle = MonoStyle,
                    shape = RoundedCornerShape(14.dp),
                    enabled = terminalEnabled,
                    modifier = Modifier.weight(1f)
                )
                Button(
                    onClick = {
                        if (command.isNotBlank()) {
                            onRun(command)
                            command = ""
                        }
                    },
                    enabled = terminalEnabled && command.isNotBlank(),
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Text(strings.run)
                }
            }
            if (!terminalEnabled) {
                Text(
                    strings.disabled,
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.error
                )
            }
            Spacer(Modifier.size(16.dp))
        }
    }
}

// ---------------------------------------------------------------------------
// Dialogs
// ---------------------------------------------------------------------------

@Composable
fun RenameDialog(initial: String, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    val strings = LocalStrings.current
    var value by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(strings.renameTitle) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                singleLine = true,
                placeholder = { Text(strings.nameHint) },
                shape = RoundedCornerShape(12.dp)
            )
        },
        confirmButton = {
            TextButton(onClick = { onSave(value.trim()) }, enabled = value.isNotBlank()) {
                Text(strings.save)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(strings.cancel) }
        }
    )
}

@Composable
fun DeleteDialog(title: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val strings = LocalStrings.current
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
        },
        title = { Text(strings.confirmDeleteTitle) },
        text = { Column {
            Text(title, fontWeight = FontWeight.Medium)
            Spacer(Modifier.size(6.dp))
            Text(strings.confirmDeleteText, style = MaterialTheme.typography.bodySmall)
        } },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(strings.delete, color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(strings.cancel) }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AttachSheet(
    onPhoto: () -> Unit,
    onCamera: () -> Unit,
    onFile: () -> Unit,
    onVoice: () -> Unit,
    onDismiss: () -> Unit
) {
    val strings = LocalStrings.current
    val scheme = MaterialTheme.colorScheme
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = scheme.surfaceContainer,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 4.dp)
        ) {
            AttachRow(OmpIcons.Image, strings.photo) { onDismiss(); onPhoto() }
            AttachRow(OmpIcons.Camera, strings.camera) { onDismiss(); onCamera() }
            AttachRow(OmpIcons.Folder, strings.file) { onDismiss(); onFile() }
            AttachRow(OmpIcons.Mic, strings.voice) { onDismiss(); onVoice() }
            Spacer(Modifier.size(12.dp))
        }
    }
}

@Composable
private fun AttachRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(
            Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
        }
        Text(label, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
    }
}

// ---------------------------------------------------------------------------
// Edit message dialog
// ---------------------------------------------------------------------------

@Composable
fun EditMessageDialog(initial: String, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    val strings = LocalStrings.current
    var value by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(strings.editMessage) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                modifier = Modifier.heightIn(min = 110.dp, max = 260.dp),
                shape = RoundedCornerShape(12.dp)
            )
        },
        confirmButton = {
            TextButton(onClick = { onSave(value.trim()) }, enabled = value.isNotBlank()) {
                Text(strings.editAndResend)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(strings.cancel) }
        }
    )
}
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.unit.dp
import com.omp.chat.data.UiRequest

/**
 * Approval / question sheet for the real OMP `ui_request` protocol. It supports
 * exactly the methods the bridge can send: select, confirm, input/editor and ask.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatGptApprovalSheet(
    request: UiRequest,
    onAnswer: (value: String?, confirmed: Boolean?, cancelled: Boolean) -> Unit,
    onAnswers: (List<Pair<String, List<String>>>) -> Unit,
    onDismiss: () -> Unit
) {
    val c = LocalChatGptColors.current
    val strings = com.omp.chat.ui.LocalStrings.current
    var input by remember(request.id) { mutableStateOf(request.prefill) }
    var selected by remember(request.id) { mutableStateOf<Set<String>>(emptySet()) }
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
                .padding(horizontal = 18.dp)
                .padding(bottom = 18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                NimayMark(size = 26)
                Column(Modifier.weight(1f)) {
                    Text(
                        strings.approvalTitle,
                        style = MaterialTheme.typography.titleMedium,
                        color = c.textPrimary
                    )
                    Text(
                        request.title.ifBlank { strings.waitingApproval },
                        style = MaterialTheme.typography.labelSmall,
                        color = c.textSecondary
                    )
                }
            }

            if (request.message.isNotBlank()) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(c.surface)
                        .border(1.dp, c.divider, RoundedCornerShape(12.dp))
                        .padding(12.dp)
                ) {
                    SelectionContainer {
                        Text(
                            request.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = c.textPrimary
                        )
                    }
                }
            }

            when (request.method) {
                "select" -> {
                    request.options.forEach { option ->
                        val deny = option.contains("deny", true) || option.contains("avslå", true)
                        GptApprovalRow(
                            label = option,
                            description = "",
                            selected = false,
                            destructive = deny,
                            onClick = { onAnswer(option, null, false) }
                        )
                    }
                }

                "confirm" -> {
                    GptApprovalRow(
                        label = strings.approve,
                        description = "",
                        selected = false,
                        destructive = false,
                        onClick = { onAnswer(null, true, false) }
                    )
                    GptApprovalRow(
                        label = strings.deny,
                        description = "",
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
                        GptTextAction(strings.cancel, c.textSecondary) { onDismiss() }
                        Spacer(Modifier.weight(1f))
                        GptFilledAction(strings.submit, enabled = input.isNotBlank()) {
                            onAnswer(input, null, false)
                        }
                    }
                }

                "ask" -> {
                    val questions = request.questions
                    if (questions.isEmpty()) {
                        Text(strings.waitingApproval, color = c.textSecondary)
                        GptFilledAction(strings.answer, enabled = true) { onAnswer("", null, false) }
                    } else {
                        Column(
                            modifier = Modifier
                                .heightIn(max = 360.dp)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            questions.forEach { question ->
                                Text(
                                    question.header.ifBlank { question.question },
                                    style = MaterialTheme.typography.titleSmall,
                                    color = c.textPrimary
                                )
                                question.options.forEach { option ->
                                    val key = "${question.id}:${option.label}"
                                    GptApprovalRow(
                                        label = option.label,
                                        description = option.description,
                                        selected = selected.contains(key),
                                        destructive = false,
                                        onClick = {
                                            selected = if (question.multi) {
                                                if (selected.contains(key)) selected - key else selected + key
                                            } else {
                                                setOf(key)
                                            }
                                        }
                                    )
                                }
                            }
                        }
                        GptFilledAction(strings.submit, enabled = true) {
                            val answers = questions.map { question ->
                                question.id to selected
                                    .filter { it.startsWith("${question.id}:") }
                                    .map { it.substringAfter(':') }
                            }
                            onAnswers(answers)
                        }
                    }
                }

                else -> {
                    Text(
                        request.message.ifBlank { request.title },
                        color = c.textSecondary,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    GptFilledAction(strings.close, enabled = true) { onAnswer(null, null, true) }
                }
            }

            Text(
                strings.approvalHint,
                style = MaterialTheme.typography.labelSmall,
                color = c.textTertiary
            )
        }
    }
}

@Composable
private fun GptTextAction(label: String, color: androidx.compose.ui.graphics.Color, onClick: () -> Unit) {
    TextButton(onClick = onClick) {
        Text(label, color = color, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun GptFilledAction(label: String, enabled: Boolean, onClick: () -> Unit) {
    val c = LocalChatGptColors.current
    Box(
        Modifier
            .fillMaxWidth()
            .height(44.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (enabled) c.textPrimary else c.surface)
            .clickable(enabled = enabled) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
            color = if (enabled) c.background else c.textTertiary
        )
    }
}

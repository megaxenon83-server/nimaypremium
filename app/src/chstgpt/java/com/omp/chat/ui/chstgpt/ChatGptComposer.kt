package com.omp.chat.ui.chstgpt

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.omp.chat.data.FeatureFlags
import com.omp.chat.ui.LocalStrings
import com.omp.chat.ui.components.Attachment

@Composable
fun ChatGptComposer(
    value: String,
    onValueChange: (String) -> Unit,
    streaming: Boolean,
    working: Boolean,
    attachments: List<Attachment>,
    enabled: Boolean,
    tokenText: String,
    onRemoveAttachment: (Int) -> Unit,
    onSend: () -> Unit,
    onQueue: () -> Unit,
    onStop: () -> Unit,
    onAttachClick: () -> Unit,
    onVoice: () -> Unit,
    modifier: Modifier = Modifier
) {
    val c = LocalChatGptColors.current
    val strings = LocalStrings.current
    val focus = LocalFocusManager.current
    val canSend = enabled && (value.isNotBlank() || attachments.isNotEmpty())

    Column(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .imePadding()
            .padding(start = 12.dp, end = 12.dp, bottom = 8.dp)
    ) {
        if (tokenText.isNotBlank()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 6.dp, end = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                Spacer(Modifier.weight(1f))
                Text(
                    tokenText,
                    style = MaterialTheme.typography.labelSmall,
                    color = c.textTertiary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        AnimatedVisibility(visible = attachments.isNotEmpty(), enter = fadeIn(), exit = fadeOut()) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(bottom = 8.dp)
            ) {
                itemsIndexed(attachments) { index, attachment ->
                    GptAttachmentChip(attachment) { onRemoveAttachment(index) }
                }
            }
        }

        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
                .background(c.surface)
                .padding(start = 6.dp, end = 6.dp, top = 5.dp, bottom = 5.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            GptIconButton(
                icon = Icons.Filled.Add,
                contentDescription = strings.attach,
                onClick = onAttachClick,
                size = 36,
                iconSize = 22,
                tint = c.textPrimary
            )

            Box(
                Modifier
                    .weight(1f)
                    .heightIn(min = 36.dp, max = 148.dp)
                    .padding(vertical = 7.dp, horizontal = 2.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                if (value.isEmpty()) {
                    Text(
                        strings.composerHint,
                        style = MaterialTheme.typography.bodyLarge,
                        color = c.textTertiary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    enabled = enabled,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        color = c.textPrimary,
                        lineHeight = 23.sp
                    ),
                    cursorBrush = androidx.compose.ui.graphics.SolidColor(c.textPrimary),
                    maxLines = 6,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                        imeAction = ImeAction.Default
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            if (value.isBlank() && attachments.isEmpty() && FeatureFlags.VoiceEnabled) {
                GptIconButton(
                    icon = Icons.Filled.Mic,
                    contentDescription = strings.voice,
                    onClick = onVoice,
                    size = 36,
                    iconSize = 21,
                    tint = c.textPrimary
                )
            }

            if (streaming || working) {
                if (canSend) {
                    GptPrimaryCircleButton(
                        icon = Icons.Filled.ArrowUpward,
                        contentDescription = "Kø",
                        onClick = onQueue,
                        size = 32,
                        iconSize = 17
                    )
                    Spacer(Modifier.width(6.dp))
                }
                GptPrimaryCircleButton(
                    icon = Icons.Filled.Stop,
                    contentDescription = strings.stop,
                    onClick = onStop,
                    size = 32,
                    iconSize = 15,
                    background = c.surfaceElevated,
                    tint = c.textPrimary
                )
            } else {
                GptPrimaryCircleButton(
                    icon = Icons.Filled.ArrowUpward,
                    contentDescription = strings.send,
                    onClick = {
                        focus.clearFocus()
                        onSend()
                    },
                    enabled = canSend,
                    size = 32,
                    iconSize = 18
                )
            }
        }
    }
}

@Composable
private fun GptAttachmentChip(attachment: Attachment, onRemove: () -> Unit) {
    val c = LocalChatGptColors.current
    val bitmap = remember(attachment.base64) {
        if (!attachment.isImage) null else try {
            val bytes = Base64.decode(attachment.base64.substringAfter("base64,"), Base64.DEFAULT)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
        } catch (_: Exception) {
            null
        }
    }
    Row(
        Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(c.surface)
            .padding(start = 6.dp, end = 2.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = attachment.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(9.dp))
            )
        } else {
            Box(
                Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(c.surfaceElevated),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    attachment.name.substringAfterLast('.', "").uppercase().take(3).ifBlank { "FIL" },
                    color = c.textSecondary,
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Text(
            attachment.name,
            style = MaterialTheme.typography.labelMedium,
            color = c.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.width(106.dp)
        )
        GptIconButton(
            icon = Icons.Filled.Close,
            contentDescription = LocalStrings.current.removeAttachment,
            onClick = onRemove,
            size = 30,
            iconSize = 15,
            tint = c.textSecondary
        )
    }
}

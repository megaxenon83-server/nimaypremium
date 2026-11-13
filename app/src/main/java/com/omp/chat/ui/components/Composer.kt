package com.omp.chat.ui.components

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Image
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.omp.chat.ui.LocalStrings
import com.omp.chat.ui.components.OmpIcons
import com.omp.chat.ui.theme.NimayAccent
import com.omp.chat.ui.theme.NimayDivider
import com.omp.chat.ui.theme.NimayError
import com.omp.chat.ui.theme.NimayInput
import com.omp.chat.ui.theme.NimayTextPrimary
import com.omp.chat.ui.theme.NimayTextSecondary
import com.omp.chat.ui.theme.NimayUserBubble

data class Attachment(
    val name: String,
    val mime: String,
    val base64: String
) {
    val isImage: Boolean get() = mime.startsWith("image/")
}

@Composable
fun Composer(
    value: String,
    onValueChange: (String) -> Unit,
    streaming: Boolean,
    attachments: List<Attachment>,
    enabled: Boolean = true,
    onRemoveAttachment: (Int) -> Unit,
    onSend: () -> Unit,
    onQueue: () -> Unit = {},
    onStop: () -> Unit,
    onAttachClick: () -> Unit,
    onVoiceClick: () -> Unit,
    onVoiceCallClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = LocalStrings.current
    val scheme = MaterialTheme.colorScheme
    val canSend = enabled && (value.isNotBlank() || attachments.isNotEmpty())

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        AnimatedVisibility(
            visible = attachments.isNotEmpty(),
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(bottom = 8.dp)
            ) {
                itemsIndexed(attachments) { index, attachment ->
                    AttachmentChip(attachment, onRemove = { onRemoveAttachment(index) })
                }
            }
        }

        Surface(
            color = NimayInput,
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, NimayDivider, RoundedCornerShape(24.dp))
        ) {
            Row(
                modifier = Modifier.padding(start = 4.dp, end = 4.dp, top = 3.dp, bottom = 3.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                IconButton(onClick = onAttachClick, modifier = Modifier.size(38.dp)) {
                    Icon(
                        OmpIcons.Attach,
                        contentDescription = strings.attach,
                        tint = NimayTextSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 38.dp)
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    if (value.isEmpty()) {
                        Text(
                            strings.composerHint,
                            style = MaterialTheme.typography.bodyLarge,
                            color = NimayTextSecondary
                        )
                    }
                    BasicTextField(
                        value = value,
                        onValueChange = onValueChange,
                        enabled = enabled,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = NimayTextPrimary),
                        cursorBrush = androidx.compose.ui.graphics.SolidColor(NimayAccent),
                        maxLines = 5,
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Sentences,
                            imeAction = ImeAction.Default
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 140.dp)
                    )
                }

                if (value.isBlank() && attachments.isEmpty() && com.omp.chat.data.FeatureFlags.VoiceEnabled) {
                    IconButton(onClick = onVoiceClick, modifier = Modifier.size(38.dp)) {
                        Icon(
                            OmpIcons.Mic,
                            contentDescription = strings.voice,
                            tint = NimayTextSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    IconButton(onClick = onVoiceCallClick, modifier = Modifier.size(38.dp)) {
                        Icon(
                            OmpIcons.Waveform,
                            contentDescription = strings.talesamtale,
                            tint = NimayAccent,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Spacer(Modifier.width(3.dp))

                if (streaming) {
                    SendStopButton(
                        streaming = true,
                        enabled = enabled,
                        onSend = onSend,
                        onStop = onStop,
                        modifier = Modifier.size(36.dp)
                    )
                    // Kø-knapp når OMP arbeider
                    if (canSend) {
                        Spacer(Modifier.width(4.dp))
                        TextButton(onClick = onQueue, modifier = Modifier.size(36.dp)) {
                            Text("Kø", color = NimayAccent, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                } else {
                    SendStopButton(
                        streaming = false,
                        enabled = enabled,
                        onSend = onSend,
                        onStop = onStop,
                        modifier = Modifier.size(36.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun AttachmentChip(attachment: Attachment, onRemove: () -> Unit) {
    Surface(
        color = NimayInput,
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.border(1.dp, NimayDivider, RoundedCornerShape(10.dp))
    ) {
        Row(
            modifier = Modifier.padding(start = 5.dp, end = 3.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val bitmap = remember(attachment.base64) {
                if (!attachment.isImage) null else try {
                    val bytes = Base64.decode(attachment.base64.substringAfter("base64,"), Base64.DEFAULT)
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
                } catch (error: Exception) {
                    null
                }
            }
            if (bitmap != null) {
                Image(
                    bitmap = bitmap,
                    contentDescription = attachment.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(8.dp))
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(NimayDivider),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        OmpIcons.Folder,
                        contentDescription = null,
                        tint = NimayTextSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            Spacer(Modifier.width(6.dp))
            Text(
                text = attachment.name,
                style = MaterialTheme.typography.labelSmall,
                color = NimayTextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.width(100.dp)
            )
            IconButton(onClick = onRemove, modifier = Modifier.size(28.dp)) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = LocalStrings.current.removeAttachment,
                    tint = NimayTextSecondary,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}

/** Click without the wide ripple, for compact circular action buttons. */
@Composable
private fun Modifier.clickableNoRipple(enabled: Boolean = true, onClick: () -> Unit): Modifier {
    val interaction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    return this.then(
        Modifier.clickable(
            enabled = enabled,
            indication = null,
            interactionSource = interaction,
            onClick = onClick
        )
    )
}

package com.omp.chat.ui.screens

import com.omp.chat.ui.theme.VariantColors;

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.omp.chat.data.RepoState
import com.omp.chat.data.UiRequest
import com.omp.chat.data.VoiceCallState
import com.omp.chat.data.VoicePhase
import com.omp.chat.ui.LocalStrings
import com.omp.chat.ui.components.ApprovalSheet
import com.omp.chat.ui.components.OmpIcons
import com.omp.chat.ui.components.StatusDot
import com.omp.chat.ui.components.shortModel
import com.omp.chat.ui.theme.VariantColors.accent
import com.omp.chat.ui.theme.VariantColors.divider
import com.omp.chat.ui.theme.VariantColors.error
import com.omp.chat.ui.theme.VariantColors.surfaceElevated
import com.omp.chat.ui.theme.VariantColors.textPrimary
import com.omp.chat.ui.theme.VariantColors.textSecondary

/**
 * Interaktiv samtalefall i ChatGPT-stil: stor pulsande orb i midten,
 * kontrollrad med mute, "Stopp OMP" og Avslutt, og godkjenningsark hvis
 * OMP ber om tillatelse midt i samtalen.
 */
@Composable
fun VoiceCallScreen(
    state: RepoState,
    call: VoiceCallState,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onHandsFree: (Boolean) -> Unit,
    onMute: (Boolean) -> Unit,
    onInterrupt: () -> Unit,
    onAnswer: (UiRequest, String?, Boolean?, Boolean) -> Unit,
    onAnswers: (UiRequest, List<Pair<String, List<String>>>) -> Unit,
    onBack: () -> Unit
) {
    val strings = LocalStrings.current
    val view = LocalView.current
    val level by animateFloatAsState(call.level.coerceIn(0f, 1f), tween(120), label = "callLevel")
    val working = call.phase == VoicePhase.Transcribing || call.phase == VoicePhase.Thinking
    val speaking = call.phase == VoicePhase.Speaking

    // En samtale er ubrukelig hvis skjermen slår seg av midt i en setning.
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
    ) {
        // -------------------------------------------------------------- header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(start = 8.dp, end = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack, modifier = Modifier.size(40.dp)) {
                Icon(
                    Icons.Default.ArrowBack,
                    contentDescription = strings.talesamtaleAvslutt,
                    tint = VariantColors.textPrimary,
                    modifier = Modifier.size(20.dp)
                )
            }
            Column(Modifier.weight(1f)) {
                Text(
                    strings.talesamtale,
                    style = MaterialTheme.typography.titleMedium,
                    color = VariantColors.textPrimary
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatusDot(online = state.connected, size = 6.dp)
                    Spacer(Modifier.width(5.dp))
                    Text(
                        text = when {
                            state.connecting -> strings.connecting
                            state.connected -> shortModel(state.chat.model?.name ?: "").ifBlank { strings.connected }
                            else -> strings.disconnected
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = VariantColors.textSecondary,
                        maxLines = 1
                    )
                }
            }
            if (call.turns > 0) {
                Text(
                    text = "${call.turns} ${if (call.turns == 1) strings.talesamtaleTurer else strings.talesamtaleTurerFlertall}",
                    style = MaterialTheme.typography.labelSmall,
                    color = VariantColors.textSecondary
                )
            }
        }

        // ------------------------------------------------------- orb + cards
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(20.dp))

            InteractiveOrb(
                active = call.active,
                muted = call.muted,
                phase = call.phase,
                level = if (call.muted || speaking) level else level,
                onStart = onStart,
                onStop = onStop,
                onToggleMute = { onMute(!call.muted) },
                onInterrupt = onInterrupt
            )

            Spacer(Modifier.height(16.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (working) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        strokeWidth = 2.dp,
                        color = VariantColors.accent
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    text = phaseText(call, strings),
                    style = MaterialTheme.typography.titleSmall,
                    color = when {
                        call.phase == VoicePhase.Error -> VariantColors.error
                        call.muted -> VariantColors.textSecondary
                        else -> VariantColors.textPrimary
                    },
                    textAlign = TextAlign.Center
                )
            }

            if (call.phase == VoicePhase.Thinking && call.elapsedMs > 3_000) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "${call.elapsedMs / 1000} s · ${strings.stoppOmpHint}",
                    style = MaterialTheme.typography.labelSmall,
                    color = VariantColors.textSecondary,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(Modifier.height(10.dp))
            LevelBars(level = level, active = call.active && !call.muted && !speaking)

            Spacer(Modifier.height(8.dp))
            Text(
                text = when {
                    call.phase == VoicePhase.Error -> strings.talesamtaleHjelp
                    !call.active -> strings.talesamtaleHjelp
                    call.muted -> strings.mikrofonAvHint
                    else -> strings.stoppOmpHint
                },
                style = MaterialTheme.typography.labelSmall,
                color = VariantColors.textSecondary,
                textAlign = TextAlign.Center
            )

            if (call.error.isNotBlank()) {
                Spacer(Modifier.height(16.dp))
                Surface(
                    color = VariantColors.error.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, VariantColors.error.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                ) {
                    Text(
                        text = call.error,
                        style = MaterialTheme.typography.bodyMedium,
                        color = VariantColors.error,
                        modifier = Modifier.padding(14.dp)
                    )
                }
            }

            if (call.transcript.isNotBlank()) {
                Spacer(Modifier.height(18.dp))
                SpeechCard(label = strings.talesamtaleDu, text = call.transcript, accent = false)
            }
            if (call.reply.isNotBlank()) {
                Spacer(Modifier.height(10.dp))
                SpeechCard(label = "OMP", text = call.reply, accent = true)
            }

            Spacer(Modifier.height(18.dp))
        }

        // ---------------------------------------------------------------- fot
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.navigationBarsPadding()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Mute mikrofon
                    ControlButton(
                        icon = if (call.muted) OmpIcons.MicOff else OmpIcons.Mic,
                        description = if (call.muted) strings.mikrofonPa else strings.mikrofonAv,
                        container = if (call.muted) VariantColors.error.copy(alpha = 0.18f) else VariantColors.surfaceElevated,
                        tint = if (call.muted) VariantColors.error else VariantColors.textPrimary,
                        enabled = call.active && !working && call.phase != VoicePhase.Error
                    ) { onMute(!call.muted) }

                    Spacer(Modifier.width(18.dp))

                    // Stopp OMP (synlig når OMP tenker/snakker)
                    if (working || speaking) {
                        ControlButton(
                            icon = OmpIcons.Stop,
                            description = strings.stoppOmp,
                            container = VariantColors.error.copy(alpha = 0.18f),
                            tint = VariantColors.error,
                            enabled = true
                        ) { onInterrupt() }

                        Spacer(Modifier.width(18.dp))
                    }

                    // Avslutt samtalen
                    ControlButton(
                        icon = OmpIcons.CallEnd,
                        description = strings.talesamtaleAvslutt,
                        container = VariantColors.error,
                        tint = Color.White,
                        enabled = call.active
                    ) {
                        onStop()
                        onBack()
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 20.dp, end = 12.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            strings.talesamtaleHandsfri,
                            style = MaterialTheme.typography.bodyMedium,
                            color = VariantColors.textPrimary
                        )
                        Text(
                            strings.talesamtaleHandsfriBeskrivelse,
                            style = MaterialTheme.typography.labelSmall,
                            color = VariantColors.textSecondary
                        )
                    }
                    Switch(
                        checked = call.handsFree,
                        onCheckedChange = onHandsFree
                    )
                }
            }
        }
    }

    state.request?.let { request ->
        ApprovalSheet(
            request = request,
            onAnswer = { value, confirmed, cancelled -> onAnswer(request, value, confirmed, cancelled) },
            onAnswers = { answers -> onAnswers(request, answers) },
            onDismiss = {}
        )
    }
}

private fun phaseText(call: VoiceCallState, strings: com.omp.chat.ui.Strings): String = when {
    !call.active && call.phase == VoicePhase.Idle -> strings.talesamtaleStart
    call.muted && call.phase == VoicePhase.Listening -> strings.mikrofonAv
    else -> when (call.phase) {
        VoicePhase.Idle -> strings.talesamtaleStart
        VoicePhase.Listening -> strings.talesamtaleLytter
        VoicePhase.Transcribing -> strings.talesamtaleTolker
        VoicePhase.Thinking -> strings.talesamtaleTenker
        VoicePhase.Speaking -> strings.talesamtaleSnakker
        VoicePhase.Error -> strings.talesamtalePrøvIgjen
    }
}

// ---------------------------------------------------------------------------
// Interaktiv orb (ChatGPT-stil)
// ---------------------------------------------------------------------------

@Composable
private fun InteractiveOrb(
    active: Boolean,
    muted: Boolean,
    phase: VoicePhase,
    level: Float,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onToggleMute: () -> Unit,
    onInterrupt: () -> Unit
) {
    val listening = active && phase == VoicePhase.Listening && !muted
    val thinking = active && (phase == VoicePhase.Thinking || phase == VoicePhase.Transcribing)
    val speaking = active && phase == VoicePhase.Speaking
    val error = phase == VoicePhase.Error

    // Pulsing/breathing
    val breathe = rememberInfiniteTransition(label = "orbBreath")
    val breath by breathe.animateFloat(
        initialValue = 0.97f,
        targetValue = when {
            speaking -> 1.06f
            listening -> 1.035f
            else -> 1f
        },
        animationSpec = infiniteRepeatable(
            animation = tween(if (speaking) 700 else 1_500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breathValue"
    )

    // Roterende sveip slik ChatGPT viser "tenker"
    val sweep = rememberInfiniteTransition(label = "orbSweep")
    val rotation by sweep.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(2_600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "sweepValue"
    )

    val coreColor = when {
        error -> VariantColors.error
        muted -> Color(0xFF5A6470)
        thinking -> VariantColors.accent.copy(alpha = 0.9f)
        speaking -> Color(0xFF2FA46A)
        else -> VariantColors.accent
    }

    Box(Modifier.size(250.dp), contentAlignment = Alignment.Center) {
        // Ytre glød som reagerer på lydnivå
        Canvas(Modifier.size((200f + level * 38f).dp)) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        coreColor.copy(alpha = if (active) 0.16f + level * 0.16f else 0.07f),
                        Color.Transparent
                    )
                )
            )
        }

        // Puls-ring i lyttemodus
        if (listening) {
            val pulse by breathe.animateFloat(
                initialValue = 0.82f,
                targetValue = (0.86f + level * 0.07f),
                animationSpec = infiniteRepeatable(
                    animation = tween(1_100, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "pulseRing"
            )
            Canvas(Modifier.size((210f * pulse).dp)) {
                drawCircle(
                    color = coreColor.copy(alpha = 0.30f),
                    style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
                )
            }
        }

        // Roterende sveip i "tenker"
        if (thinking) {
            Canvas(Modifier.rotate(rotation).size(206.dp)) {
                repeat(3) { i ->
                    drawCircle(
                        color = VariantColors.accent.copy(alpha = 0.5f - i * 0.16f),
                        radius = (103.dp - (i * 4).dp).toPx(),
                        style = Stroke(width = (3 - i).dp.toPx(), cap = StrokeCap.Round)
                    )
                }
            }
        }

        // Kjerne + ikon + trykk
        val onClick = when {
            !active || error -> onStart
            thinking || speaking -> onInterrupt
            else -> onToggleMute
        }
        val interaction = remember { MutableInteractionSource() }
        Surface(
            color = coreColor,
            shape = CircleShape,
            modifier = Modifier
                .size((118f * if (muted || error) 1f else breath).dp)
                .clip(CircleShape)
                .clickable(
                    interactionSource = interaction,
                    indication = null,
                    onClick = onClick
                )
        ) {
            Box(contentAlignment = Alignment.Center) {
                Canvas(Modifier.size(118.dp)) {
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.28f),
                                Color.Transparent
                            ),
                            center = Offset(size.width * 0.35f, size.height * 0.3f),
                            radius = size.minDimension * 0.85f
                        )
                    )
                }
                Icon(
                    imageVector = when {
                        !active -> OmpIcons.Phone
                        error -> OmpIcons.Phone
                        muted -> OmpIcons.MicOff
                        thinking -> OmpIcons.Stop
                        speaking -> OmpIcons.Waveform
                        else -> OmpIcons.Mic
                    },
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(46.dp)
                )
            }
        }
    }
}

@Composable
private fun ControlButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    container: Color,
    tint: Color,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    Surface(
        color = container,
        shape = CircleShape,
        modifier = Modifier
            .size(52.dp)
            .clip(CircleShape)
            .clickable(
                enabled = enabled,
                interactionSource = interaction,
                indication = null,
                onClick = onClick
            )
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                icon,
                contentDescription = description,
                tint = tint,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Level meter
// ---------------------------------------------------------------------------

@Composable
private fun LevelBars(level: Float, active: Boolean) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        repeat(7) { index ->
            val threshold = (index + 1) / 8f
            val lit = active && level >= threshold
            Box(
                Modifier
                    .width(4.dp)
                    .height((8 + index * 2).dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(if (lit) VariantColors.accent else VariantColors.divider)
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Transcript / answer card
// ---------------------------------------------------------------------------

@Composable
private fun SpeechCard(label: String, text: String, accent: Boolean) {
    Surface(
        color = VariantColors.surfaceElevated,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (accent) Modifier.border(1.dp, VariantColors.accent.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
                else Modifier
            )
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = if (accent) VariantColors.accent else VariantColors.textSecondary,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = VariantColors.textPrimary
            )
        }
    }
}

package com.omp.chat.ui.screens

import com.omp.chat.ui.theme.VariantColors;

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.omp.chat.ui.LocalStrings
import com.omp.chat.ui.components.OmpAvatar
import com.omp.chat.ui.components.OmpIcons
import com.omp.chat.ui.theme.MonoStyle
import com.omp.chat.ui.theme.VariantColors.accent
import com.omp.chat.ui.theme.VariantColors.background
import com.omp.chat.ui.theme.VariantColors.error
import com.omp.chat.ui.theme.VariantColors.surfaceElevated
import com.omp.chat.ui.theme.VariantColors.sidebar
import com.omp.chat.ui.theme.VariantColors.textPrimary
import com.omp.chat.ui.theme.VariantColors.textSecondary

@Composable
fun PairingScreen(
    connecting: Boolean,
    error: String,
    onScan: () -> Unit,
    onPair: (String, String) -> String?,
    onClearError: () -> Unit = {},
    onToast: (String) -> Unit = {},
    onGoToMain: () -> Unit = {}
) {
    val strings = LocalStrings.current
    val scheme = MaterialTheme.colorScheme
    var manual by remember { mutableStateOf(false) }
    var url by remember { mutableStateOf("") }
    var token by remember { mutableStateOf("") }
    var localError by remember { mutableStateOf("") }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(VariantColors.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 26.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(64.dp))

            Box(
                Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(VariantColors.accent.copy(alpha = 0.16f))
                    .border(1.dp, VariantColors.accent.copy(alpha = 0.35f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    OmpIcons.Sparkle,
                    contentDescription = null,
                    tint = VariantColors.accent,
                    modifier = Modifier.size(32.dp)
                )
            }

            Spacer(Modifier.height(26.dp))
            Text(
                strings.welcomeTitle,
                style = MaterialTheme.typography.headlineSmall,
                color = scheme.onBackground,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(10.dp))
            Text(
                strings.welcomeText,
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 4.dp)
            )

            Spacer(Modifier.height(28.dp))
            Button(
                onClick = onScan,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10A37F), contentColor = Color(0xFFFFFFFF))
            ) {
                Icon(OmpIcons.Chat, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(strings.scanQr, style = MaterialTheme.typography.labelLarge)
            }
            TextButton(onClick = { manual = !manual }) {
                Text(
                    strings.manualPair,
                    style = MaterialTheme.typography.labelLarge,
                    color = scheme.onSurfaceVariant
                )
            }

            AnimatedVisibility(
                visible = manual,
                enter = fadeIn() + expandVertically()
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = url,
                        onValueChange = { url = it },
                        label = { Text(strings.serverUrl) },
                        placeholder = { Text("192.168.1.20:7777") },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = token,
                        onValueChange = { token = it },
                        label = { Text(strings.accessToken) },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        textStyle = MonoStyle,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Button(
                        onClick = {
                            localError = onPair(url, token) ?: ""
                        },
                        enabled = url.isNotBlank() && token.isNotBlank() && !connecting,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(46.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10A37F), contentColor = Color(0xFFFFFFFF))
                    ) {
                        if (connecting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = Color.White
                            )
                        } else {
                            Text(strings.connect)
                        }
                    }
                }
            }

            if (connecting) {
                Spacer(Modifier.height(18.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = scheme.primary
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        strings.connecting,
                        style = MaterialTheme.typography.labelMedium,
                        color = scheme.onSurfaceVariant
                    )
                }
            }

            val message = localError.ifBlank { error }
            if (message.isNotBlank()) {
                Spacer(Modifier.height(16.dp))
                Surface(
                    color = scheme.errorContainer.copy(alpha = 0.55f),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            Icons.Default.Warning,
                            contentDescription = null,
                            tint = scheme.onErrorContainer,
                            modifier = Modifier.size(17.dp)
                        )
                        Column {
                            Text(
                                message,
                                style = MaterialTheme.typography.bodySmall,
                                color = scheme.onErrorContainer
                            )
                            Text(
                                strings.retryHint,
                                style = MaterialTheme.typography.labelSmall,
                                color = scheme.onErrorContainer.copy(alpha = 0.75f)
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TextButton(onClick = {
                    localError = ""
                    url = ""
                    token = ""
                    onClearError()
                    onToast("Nullstilt - prøv å skanne QR-koden på nytt")
                }) {
                    Text(
                        "Tilbake / prøv igjen",
                        style = MaterialTheme.typography.labelMedium,
                        color = scheme.onSurfaceVariant
                    )
                }
                TextButton(onClick = onGoToMain) {
                    Text(
                        "Gå til hovedskjerm",
                        style = MaterialTheme.typography.labelMedium,
                        color = scheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }
            }
            Spacer(Modifier.height(22.dp))
            Surface(
                color = scheme.surfaceContainerLow,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, scheme.outlineVariant, RoundedCornerShape(14.dp))
            ) {
                Column(Modifier.padding(14.dp)) {
                    Text(
                        "1 · " + strings.connectedPc,
                        style = MaterialTheme.typography.labelMedium,
                        color = scheme.onSurface
                    )
                    Spacer(Modifier.height(8.dp))
                    SelectionContainer {
                        Text(
                            "node bridge/omp-bridge.mjs",
                            style = MonoStyle,
                            color = scheme.primary
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "2 · " + strings.scanHint,
                        style = MaterialTheme.typography.labelMedium,
                        color = scheme.onSurface
                    )
                }
            }

            Spacer(Modifier.height(24.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                OmpAvatar(size = 18.dp, muted = true)
                Spacer(Modifier.width(7.dp))
                Text(
                    strings.tagline,
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
            Spacer(Modifier.height(28.dp))
        }
    }
}

@Composable
private fun SuggestionChip(text: String, onClick: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = CircleShape,
        modifier = Modifier
            .clip(CircleShape)
            .clickable { onClick() }
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp)
        )
    }
}

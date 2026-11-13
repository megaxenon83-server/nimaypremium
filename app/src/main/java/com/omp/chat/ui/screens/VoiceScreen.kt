package com.omp.chat.ui.screens

import com.omp.chat.ui.theme.VariantColors;

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.omp.chat.data.RepoState
import com.omp.chat.ui.LocalStrings
import com.omp.chat.ui.components.OmpIcons
import com.omp.chat.ui.components.SectionLabel
import com.omp.chat.ui.components.StatusDot
import com.omp.chat.ui.theme.MonoStyle
import com.omp.chat.ui.theme.VariantColors.accent
import com.omp.chat.ui.theme.VariantColors.divider
import com.omp.chat.ui.theme.VariantColors.error
import com.omp.chat.ui.theme.VariantColors.surfaceElevated
import com.omp.chat.ui.theme.VariantColors.sidebar
import com.omp.chat.ui.theme.VariantColors.textPrimary
import com.omp.chat.ui.theme.VariantColors.textSecondary

@Composable
fun VoiceScreen(
    state: RepoState,
    settings: com.omp.chat.data.AppSettings,
    onBack: () -> Unit,
    onTheme: (String) -> Unit,
    onDynamicColor: (Boolean) -> Unit,
    onFontScale: (Float) -> Unit,
    onLanguage: (String) -> Unit,
    onHaptics: (Boolean) -> Unit,
    onNotifications: (Boolean) -> Unit,
    onShowThinking: (Boolean) -> Unit,
    onSpeechLanguage: (String) -> Unit,
    onModel: (com.omp.chat.data.ModelInfo) -> Unit,
    onTest: () -> Unit,
    onDisconnect: () -> Unit,
    onScan: () -> Unit,
    onLoadLog: (onResult: (String) -> Unit) -> Unit
) {
    val strings = LocalStrings.current
    val scheme = MaterialTheme.colorScheme

    var voiceMenuOpen by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
    ) {
        // -------------------------------------------------------------- header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .padding(start = 16.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack, modifier = Modifier.size(40.dp)) {
                Icon(
                    Icons.Default.ArrowBack,
                    contentDescription = strings.chats,
                    tint = VariantColors.textPrimary,
                    modifier = Modifier.size(20.dp)
                )
            }
            Column(Modifier.weight(1f)) {
                Text(
                    strings.taleOgStemme,
                    style = MaterialTheme.typography.titleLarge,
                    color = VariantColors.textPrimary
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatusDot(online = state.connected, size = 6.dp)
                    Spacer(Modifier.width(5.dp))
                    Text(
                        text = when {
                            state.connecting -> strings.connecting
                            state.connected -> state.error.ifBlank { strings.connected }
                            else -> strings.disconnected
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = VariantColors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            IconButton(onClick = { voiceMenuOpen = true }, modifier = Modifier.size(40.dp)) {
                Icon(
                    Icons.Default.MoreVert,
                    contentDescription = strings.chatMenu,
                    tint = VariantColors.textSecondary,
                    modifier = Modifier.size(20.dp)
                )
            }
            DropdownMenu(
                expanded = voiceMenuOpen,
                onDismissRequest = { voiceMenuOpen = false }
            ) {
                DropdownMenuItem(
                    text = { Text(strings.settings) },
                    leadingIcon = { Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    onClick = { voiceMenuOpen = false; onDisconnect() }
                )
                DropdownMenuItem(
                    text = { Text(strings.disconnect, color = VariantColors.error) },
                    leadingIcon = { Icon(Icons.Default.Close, contentDescription = null, tint = VariantColors.error, modifier = Modifier.size(18.dp)) },
                    onClick = { voiceMenuOpen = false; onDisconnect() }
                )
            }
        }

        // ----------------------------------------------------------- taleprofil
        SectionLabel(strings.taleprofil, Modifier.padding(start = 16.dp, top = 16.dp, bottom = 8.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TaleprofilChip(
                label = strings.rask,
                selected = true,
                onClick = {}
            )
            TaleprofilChip(
                label = strings.balansert,
                selected = false,
                onClick = {}
            )
            TaleprofilChip(
                label = strings.besteStemmekvalitet,
                selected = false,
                onClick = {}
            )
            TaleprofilChip(
                label = strings.egendefinert,
                selected = false,
                onClick = {}
            )
        }

        // ----------------------------------------------------------- talemotor
        SectionLabel(strings.talemotor, Modifier.padding(start = 16.dp, top = 24.dp, bottom = 8.dp))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TaleMotorRad(
                label = strings.norskPiper,
                undertittel = strings.raskNorskTale + " · " + strings.cpu,
                status = strings.installert,
                statusFarge = VariantColors.accent,
                onClick = {}
            )
            TaleMotorRad(
                label = strings.chatterboxMultilingualV3,
                undertittel = strings.naturligNorsk + " · " + strings.testHastighet,
                status = strings.installer,
                statusFarge = VariantColors.textSecondary,
                onClick = {}
            )
            TaleMotorRad(
                label = strings.prat9bNf4,
                undertittel = strings.norskBokmal + " · ca. " + strings.sjekk6,
                status = strings.installer,
                statusFarge = VariantColors.textSecondary,
                onClick = {}
            )
            TaleMotorRad(
                label = strings.originalMicrosoftVibeVoice,
                undertittel = strings.primærtEngelsk + " · " + strings.kinesisk,
                status = strings.valgfri,
                statusFarge = VariantColors.textSecondary,
                onClick = {}
            )
        }

        // ----------------------------------------------------------- stemmevalg
        SectionLabel(strings.stemmevalg, Modifier.padding(start = 16.dp, top = 24.dp, bottom = 8.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            StemmeChip(
                label = strings.norskKvinne,
                selected = true,
                onClick = {}
            )
            StemmeChip(
                label = strings.norskMann,
                selected = false,
                onClick = {}
            )
        }

        // ----------------------------------------------------------- talegjenkjenning
        SectionLabel(strings.talegjenkjenning, Modifier.padding(start = 16.dp, top = 24.dp, bottom = 8.dp))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ValgRad(
                label = strings.automatisk,
                subtitle = strings.nbWhisperBase,
                selected = true,
                onClick = {}
            )
            ValgRad(
                label = strings.nbWhisperTiny,
                subtitle = strings.cpu + " · " + strings.automatisk,
                selected = false,
                onClick = {}
            )
            ValgRad(
                label = strings.nbWhisperSmall,
                subtitle = strings.cpu + " · " + strings.automatisk,
                selected = false,
                onClick = {}
            )
        }

        // ----------------------------------------------------------- opplesning
        SectionLabel(strings.opplesning, Modifier.padding(start = 16.dp, top = 24.dp, bottom = 8.dp))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ValgRad(
                label = strings.bareITalemodus,
                subtitle = strings.bareITalemodusBeskrivelse,
                selected = true,
                onClick = {}
            )
            ValgRad(
                label = strings.ogsåIVanligChat,
                subtitle = strings.ogsåIVanligChatBeskrivelse,
                selected = false,
                onClick = {}
            )
            ValgRad(
                label = strings.aldriAutomatisk,
                subtitle = strings.aldriAutomatiskBeskrivelse,
                selected = false,
                onClick = {}
            )
        }

        // ----------------------------------------------------------- samtalekontroller
        SectionLabel(strings.samtalekontroller, Modifier.padding(start = 16.dp, top = 24.dp, bottom = 8.dp))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ValgRad(
                label = strings.stemmeavbrudd,
                subtitle = strings.stemmeavbruddBeskrivelse,
                selected = true,
                onClick = {}
            )
            ValgRad(
                label = strings.automatiskLavereFallback,
                subtitle = strings.automatiskLavereFallbackBeskrivelse,
                selected = true,
                onClick = {}
            )
            ValgRad(
                label = strings.mikrofonMute,
                subtitle = strings.mikrofonMuteBeskrivelse,
                selected = false,
                onClick = {}
            )
            ValgRad(
                label = strings.høyttaler,
                subtitle = strings.høyttalerBeskrivelse,
                selected = true,
                onClick = {}
            )
        }

        // ----------------------------------------------------------- lagring
        SectionLabel(strings.lagring, Modifier.padding(start = 16.dp, top = 24.dp, bottom = 8.dp))
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            InfoRow(
                label = strings.mobilenBeholder,
                value = strings.mobilenBeholderBeskrivelse
            )
            InfoRow(
                label = strings.installerteModeller,
                value = strings.installerteModellerBeskrivelse
            )
            InfoRow(
                label = strings.synkroniserTrygt,
                value = strings.synkroniserTrygtBeskrivelse
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Taleprofil-chip
// ---------------------------------------------------------------------------

@Composable
private fun TaleprofilChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val s = LocalStrings.current
    val scheme = MaterialTheme.colorScheme
    Surface(
        color = if (selected) VariantColors.accent.copy(alpha = 0.15f) else VariantColors.surfaceElevated,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .border(
                1.dp,
                if (selected) VariantColors.accent else VariantColors.divider,
                RoundedCornerShape(8.dp)
            )
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) VariantColors.accent else VariantColors.textSecondary,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
        )
    }
}

// ---------------------------------------------------------------------------
// Tale-motor-rad
// ---------------------------------------------------------------------------

@Composable
private fun TaleMotorRad(
    label: String,
    undertittel: String,
    status: String,
    statusFarge: Color,
    onClick: () -> Unit
) {
    val s = LocalStrings.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable { onClick() }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(30.dp)
                .clip(CircleShape)
                .background(VariantColors.surfaceElevated),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                OmpIcons.Mic,
                contentDescription = null,
                tint = VariantColors.textSecondary,
                modifier = Modifier.size(15.dp)
            )
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                label,
                style = MaterialTheme.typography.bodyMedium,
                color = VariantColors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            Text(
                undertittel,
                style = MaterialTheme.typography.labelSmall,
                color = VariantColors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Box {
            IconButton(
                onClick = {},
                modifier = Modifier.size(38.dp)
            ) {
                Icon(
                    if (status == s.installert) OmpIcons.Done else OmpIcons.Add,
                    contentDescription = status,
                    tint = statusFarge,
                    modifier = Modifier.size(18.dp)
                )
            }
            DropdownMenu(
                expanded = false,
                onDismissRequest = {}
            ) {
                DropdownMenuItem(
                    text = { Text(s.installer) },
                    leadingIcon = { Icon(OmpIcons.Add, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    onClick = {}
                )
                DropdownMenuItem(
                    text = { Text(s.testDenneStemmen) },
                    leadingIcon = { Icon(OmpIcons.Sparkle, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    onClick = {}
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Stemme-chip
// ---------------------------------------------------------------------------

@Composable
private fun StemmeChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        color = if (selected) VariantColors.accent.copy(alpha = 0.15f) else VariantColors.surfaceElevated,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .border(
                1.dp,
                if (selected) VariantColors.accent else VariantColors.divider,
                RoundedCornerShape(8.dp)
            )
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) VariantColors.accent else VariantColors.textSecondary,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
        )
    }
}

// ---------------------------------------------------------------------------
// Valg-rad
// ---------------------------------------------------------------------------

@Composable
private fun ValgRad(
    label: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(24.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(if (selected) VariantColors.accent else VariantColors.divider),
            contentAlignment = Alignment.Center
        ) {
            if (selected) {
                Icon(
                    OmpIcons.Done,
                    contentDescription = null,
                    tint = Color(0xFFFFFFFF),
                    modifier = Modifier.size(14.dp)
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                label,
                style = MaterialTheme.typography.bodyMedium,
                color = VariantColors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            Text(
                subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = VariantColors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Info-rad
// ---------------------------------------------------------------------------

@Composable
private fun InfoRow(
    label: String,
    value: String
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        Spacer(Modifier.weight(1f))
        Text(
            value,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
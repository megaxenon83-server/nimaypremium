package com.omp.chat.ui.screens

import com.omp.chat.ui.theme.VariantColors;

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.omp.chat.data.EndpointProbe
import com.omp.chat.data.EndpointResolver
import com.omp.chat.data.PcProfile
import com.omp.chat.ui.AppViewModel
import com.omp.chat.ui.LocalStrings
import com.omp.chat.ui.theme.VariantColors.accent
import com.omp.chat.ui.theme.VariantColors.online
import com.omp.chat.ui.theme.VariantColors.background
import com.omp.chat.ui.theme.VariantColors.divider
import com.omp.chat.ui.theme.VariantColors.error
import com.omp.chat.ui.theme.VariantColors.surfaceElevated
import com.omp.chat.ui.theme.VariantColors.sidebar
import com.omp.chat.ui.theme.VariantColors.surface
import com.omp.chat.ui.theme.VariantColors.textPrimary
import com.omp.chat.ui.theme.VariantColors.textSecondary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun PcManagerScreen(
    viewModel: AppViewModel,
    onBack: () -> Unit,
    onScan: () -> Unit = {}
) {
    val strings = LocalStrings.current
    val scope = rememberCoroutineScope()
    val profiles by viewModel.pcProfileList.collectAsState()
    val activeId by viewModel.activePcId.collectAsState()
    val statusMap by viewModel.pcStatus.collectAsState()

    var editing by remember { mutableStateOf<PcProfile?>(null) }
    var creating by remember { mutableStateOf(false) }
    var busyId by remember { mutableStateOf<String?>(null) }
    var toast by remember { mutableStateOf<String?>(null) }
    var probeReport by remember { mutableStateOf<Map<String, List<EndpointProbe>>>(emptyMap()) }
    var refreshTick by remember { mutableStateOf(0) }

    LaunchedEffect(profiles, refreshTick) {
        // Test alle kjente adresser per PC (LAN, Tailscale, VPN) og vis hvilken
        // som faktisk svarte – aldri bare "Utilgjengelig".
        for (p in profiles) {
            viewModel.updatePcStatus(p.id, "checking")
            scope.launch {
                val probes = withContext(Dispatchers.IO) { EndpointResolver.probeAll(p) }
                probeReport = probeReport + (p.id to probes)
                val winner = probes.firstOrNull { it.ok }
                if (winner != null) {
                    viewModel.updatePcStatus(p.id, "connected")
                    if (winner.url != p.baseUrl) viewModel.promoteEndpoint(winner.url, p.id)
                } else {
                    viewModel.updatePcStatus(p.id, "offline")
                }
            }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .background(VariantColors.background)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Tilbake", tint = VariantColors.textPrimary)
            }
            Text(
                "PC-er",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = VariantColors.textPrimary,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = { refreshTick++ }, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Default.Refresh, contentDescription = "Test alle adresser", tint = VariantColors.textSecondary)
            }
            IconButton(onClick = onScan, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Default.Add, contentDescription = "Skann QR-kode", tint = VariantColors.accent)
            }
            IconButton(onClick = { creating = true }, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Default.Add, contentDescription = "Legg til PC", tint = VariantColors.accent)
            }
        }

        if (profiles.isEmpty()) {
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text("Ingen PC-er registrert ennå.", color = VariantColors.textSecondary)
                Spacer(Modifier.height(8.dp))
                Text("Legg til en PC for å starte.", color = VariantColors.textSecondary, style = MaterialTheme.typography.labelSmall)
                Spacer(Modifier.height(12.dp))
                Button(onClick = { creating = true }) { Text("Legg til PC") }
            }
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                items(profiles, key = { it.id }) { profile ->
                    val status = statusMap[profile.id] ?: "unknown"
                    PcCard(
                        profile = profile,
                        active = profile.id == activeId,
                        status = status,
                        probes = probeReport[profile.id].orEmpty(),
                        busy = busyId == profile.id,
                        onConnect = {
                            val isActive = profile.id == activeId
                            if (isActive) {
                                viewModel.disconnectPc(profile.id)
                                toast = "Frakoblet ${profile.name}"
                            } else {
                                busyId = profile.id
                                viewModel.switchPc(profile.id)
                                busyId = null
                                toast = "Byttet til ${profile.name}"
                            }
                        },
                        onWake = {
                            busyId = profile.id
                            viewModel.wakePc(profile) { ok ->
                                busyId = null
                                toast = if (ok) "WOL-pakke sendt til ${profile.name}" else "Kunne ikke sende WOL-pakke"
                            }
                        },
                        onPowerOff = {
                            viewModel.powerOffPc(profile) { ok, msg ->
                                toast = if (ok) "Slår av ${profile.name}" else msg
                            }
                        },
                        onEdit = { editing = profile },
                        onDelete = { viewModel.deletePcProfile(profile.id); toast = "Slettet ${profile.name}" }
                    )
                }
            }
        }
    }

    if (creating) {
        PcEditDialog(
            profile = null,
            onDismiss = { creating = false },
            onSave = { p ->
                viewModel.savePcProfile(p)
                creating = false
                toast = "Lagt til ${p.name}"
            }
        )
    }
    editing?.let { p ->
        PcEditDialog(
            profile = p,
            onDismiss = { editing = null },
            onSave = { updated ->
                viewModel.savePcProfile(updated)
                editing = null
                toast = "Oppdatert ${updated.name}"
            }
        )
    }

    toast?.let { t ->
        LaunchedEffect(t) {
            kotlinx.coroutines.delay(2500)
            toast = null
        }
        androidx.compose.material3.Snackbar(
            modifier = Modifier.padding(12.dp)
        ) { Text(t) }
    }
}

@Composable
private fun PcCard(
    profile: PcProfile,
    active: Boolean,
    status: String,
    probes: List<EndpointProbe>,
    busy: Boolean,
    onConnect: () -> Unit,
    onWake: () -> Unit,
    onPowerOff: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    var showPowerOff by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var deleteCountdown by remember { mutableStateOf(10) }
    val statusColor = when (status) {
        "connected" -> VariantColors.online
        "pc_available" -> VariantColors.accent
        "starting", "checking", "wol_sent" -> VariantColors.accent
        "offline" -> VariantColors.error
        else -> VariantColors.textSecondary
    }
    val statusText = when (status) {
        "connected" -> "OMP Bridge tilkoblet"
        "pc_available" -> "PC tilgjengelig"
        "starting", "wol_sent" -> "WOL sendt"
        "checking" -> "Sjekker…"
        "offline" -> "Utilgjengelig"
        else -> "Ukjent"
    }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 6.dp),
        shape = RoundedCornerShape(12.dp),
        color = if (active) VariantColors.accent.copy(alpha = 0.12f) else VariantColors.surface
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(statusColor)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    profile.name,
                    color = VariantColors.textPrimary,
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (active) {
                    Text("Aktiv", color = VariantColors.accent, style = MaterialTheme.typography.labelSmall)
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(profile.baseUrl, color = VariantColors.textSecondary, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (profile.mac.isNotBlank()) {
                Text("MAC: ${profile.mac}", color = VariantColors.textSecondary, style = MaterialTheme.typography.labelSmall)
            }
            Text("Status: $statusText", color = statusColor, style = MaterialTheme.typography.labelSmall)
            if (probes.isNotEmpty()) {
                Spacer(Modifier.height(2.dp))
                probes.forEach { probe ->
                    Text(
                        "${EndpointResolver.hostLabel(probe.url)}: ${if (probe.ok) "svarte" else probe.reason}",
                        color = if (probe.ok) VariantColors.online else VariantColors.textSecondary,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Button(
                    onClick = onConnect,
                    enabled = !busy && profile.baseUrl.isNotBlank(),
                    modifier = Modifier.weight(1f)
                ) {
                    if (busy) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = Color.White)
                    } else if (active) {
                        Text("Koble fra")
                    } else {
                        Text("Bytt til")
                    }
                }
                TextButton(onClick = onWake, enabled = profile.mac.isNotBlank() && !busy) {
                    Text("Vekk", color = VariantColors.accent, style = MaterialTheme.typography.labelSmall)
                }
                IconButton(onClick = { showPowerOff = true }) {
                    Icon(Icons.Default.PowerSettingsNew, contentDescription = "Slå av", tint = VariantColors.error)
                }
                IconButton(onClick = onEdit) {
                    Icon(Icons.Default.Edit, contentDescription = "Rediger", tint = VariantColors.textSecondary)
                }
                IconButton(onClick = { showDeleteConfirm = true }) {
                    Icon(Icons.Default.Delete, contentDescription = "Slett", tint = VariantColors.error)
                }
            }
        }
    }
    if (showPowerOff) {
        AlertDialog(
            onDismissRequest = { showPowerOff = false },
            title = { Text("Slå av PC?") },
            text = { Text("Dette slår av ${profile.name} umiddelbart. Er du sikker?") },
            confirmButton = {
                TextButton(onClick = {
                    showPowerOff = false
                    onPowerOff()
                }) { Text("Slå av", color = VariantColors.error) }
            },
            dismissButton = {
                TextButton(onClick = { showPowerOff = false }) { Text("Avbryt") }
            }
        )
    }
    if (showDeleteConfirm) {
        LaunchedEffect(showDeleteConfirm) {
            deleteCountdown = 10
            for (i in 10 downTo 1) {
                deleteCountdown = i
                kotlinx.coroutines.delay(1000)
            }
            showDeleteConfirm = false
        }
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Slett PC?") },
            text = { Text("Er du sikker på at du vil slette denne PC-en? ($deleteCountdown)") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    onDelete()
                }) { Text("Slett", color = VariantColors.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Avbryt") }
            }
        )
    }
}

@Composable
private fun PcEditDialog(
    profile: PcProfile?,
    onDismiss: () -> Unit,
    onSave: (PcProfile) -> Unit
) {
    var name by remember { mutableStateOf(profile?.name ?: "") }
    var baseUrl by remember { mutableStateOf(profile?.baseUrl ?: "") }
    var token by remember { mutableStateOf(profile?.token ?: "") }
    var mac by remember { mutableStateOf(profile?.mac ?: "") }
    var broadcast by remember { mutableStateOf(profile?.wolBroadcast ?: "255.255.255.255") }
    var wolPort by remember { mutableStateOf((profile?.wolPort ?: 9).toString()) }
    var useDocker by remember { mutableStateOf(profile?.useDockerWol ?: false) }
    var dockerUrl by remember { mutableStateOf(profile?.dockerWolUrl ?: "") }
    var dockerToken by remember { mutableStateOf(profile?.dockerWolToken ?: "") }
    var autoConnect by remember { mutableStateOf(profile?.autoConnect ?: true) }
    var extraEndpoints by remember { mutableStateOf(profile?.endpoints?.joinToString("\n").orEmpty()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (profile == null) "Ny PC" else "Rediger PC", color = VariantColors.textPrimary) },
        text = {
            Column {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Navn") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(value = baseUrl, onValueChange = { baseUrl = it }, label = { Text("Adresse (http://ip:7777)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = extraEndpoints,
                    onValueChange = { extraEndpoints = it },
                    label = { Text("Ekstra adresser (LAN/Tailscale), én per linje") },
                    minLines = 2,
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(6.dp))
                var showToken by remember { mutableStateOf(false) }
                OutlinedTextField(
                    value = token,
                    onValueChange = { token = it },
                    label = { Text("Token") },
                    singleLine = true,
                    visualTransformation = if (showToken) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showToken = !showToken }) {
                            Icon(
                                if (showToken) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = if (showToken) "Skjul" else "Vis",
                                tint = VariantColors.textSecondary
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(value = mac, onValueChange = { mac = it }, label = { Text("MAC-adresse (for WOL)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(value = broadcast, onValueChange = { broadcast = it }, label = { Text("Broadcast") }, singleLine = true, modifier = Modifier.weight(1f))
                    Spacer(Modifier.width(6.dp))
                    OutlinedTextField(value = wolPort, onValueChange = { wolPort = it }, label = { Text("Port") }, singleLine = true, modifier = Modifier.width(70.dp))
                }
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(checked = useDocker, onCheckedChange = { useDocker = it })
                    Spacer(Modifier.width(8.dp))
                    Text("Bruk sentral WOL-tjeneste", color = VariantColors.textPrimary, style = MaterialTheme.typography.labelSmall)
                }
                if (useDocker) {
                    Spacer(Modifier.height(6.dp))
                    OutlinedTextField(value = dockerUrl, onValueChange = { dockerUrl = it }, label = { Text("WOL-tjeneste URL") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(checked = autoConnect, onCheckedChange = { autoConnect = it })
                    Spacer(Modifier.width(8.dp))
                    Text("Automatisk tilkobling", color = VariantColors.textPrimary, style = MaterialTheme.typography.labelSmall)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (name.isBlank() || baseUrl.isBlank()) return@TextButton
                onSave(
                    PcProfile(
                        id = profile?.id ?: java.util.UUID.randomUUID().toString(),
                        name = name.trim(),
                        baseUrl = baseUrl.trim(),
                        token = token.trim(),
                        mac = mac.trim(),
                        wolBroadcast = broadcast.trim().ifBlank { "255.255.255.255" },
                        wolPort = wolPort.toIntOrNull() ?: 9,
                        lastCwd = profile?.lastCwd ?: "",
                        lastSession = profile?.lastSession ?: "",
                        favoriteSessions = profile?.favoriteSessions ?: emptyList(),
                        autoConnect = autoConnect,
                        favoriteModels = profile?.favoriteModels ?: emptyList(),
                        fallbackModel = profile?.fallbackModel ?: "",
                        useDockerWol = useDocker,
                        dockerWolUrl = dockerUrl.trim(),
                        dockerWolToken = dockerToken.trim(),
                        lanUrl = profile?.lanUrl ?: "",
                        tailscaleUrl = profile?.tailscaleUrl ?: "",
                        endpoints = extraEndpoints.lines()
                            .map { it.trim() }
                            .filter { it.isNotBlank() }
                            .distinct()
                    )
                )
            }) { Text("Lagre", color = VariantColors.accent) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Avbryt", color = VariantColors.textSecondary) }
        },
        containerColor = VariantColors.sidebar
    )
}

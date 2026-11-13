package com.omp.chat.ui.screens

import com.omp.chat.ui.theme.VariantColors;

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.omp.chat.ui.AppViewModel
import com.omp.chat.ui.LocalStrings
import com.omp.chat.ui.theme.VariantColors.accent
import com.omp.chat.ui.theme.VariantColors.online
import com.omp.chat.ui.theme.VariantColors.background
import com.omp.chat.ui.theme.VariantColors.divider
import com.omp.chat.ui.theme.VariantColors.surfaceElevated
import com.omp.chat.ui.theme.VariantColors.sidebar
import com.omp.chat.ui.theme.VariantColors.surface
import com.omp.chat.ui.theme.VariantColors.textPrimary
import com.omp.chat.ui.theme.VariantColors.textSecondary
import kotlinx.coroutines.launch

@Composable
fun GotifyScreen(
    viewModel: AppViewModel,
    onBack: () -> Unit
) {
    val settings = viewModel.settingsState.collectAsState().value
    var enabled by remember { mutableStateOf(settings.gotifyEnabled) }
    var server by remember { mutableStateOf(settings.gotifyServer) }
    var token by remember { mutableStateOf(settings.gotifyToken) }
    var showToken by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }

    LaunchedEffect(settings.gotifyEnabled, settings.gotifyServer, settings.gotifyToken) {
        enabled = settings.gotifyEnabled
        server = settings.gotifyServer
        token = settings.gotifyToken
    }

    Column(Modifier.fillMaxSize().background(VariantColors.background)) {
        Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Tilbake", tint = VariantColors.textPrimary)
            }
            Text("Gotify", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = VariantColors.textPrimary, modifier = Modifier.weight(1f))
        }
        Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = enabled, onCheckedChange = { enabled = it })
                Spacer(Modifier.width(8.dp))
                Text("Aktiver Gotify", color = VariantColors.textPrimary)
            }
            OutlinedTextField(
                value = server,
                onValueChange = { server = it },
                label = { Text("Server-URL (f.eks. http://192.168.1.10:8090)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = token,
                onValueChange = { token = it },
                label = { Text("App-token") },
                singleLine = true,
                visualTransformation = if (showToken) androidx.compose.ui.text.input.VisualTransformation.None else androidx.compose.ui.text.input.PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { showToken = !showToken }) {
                        Text(if (showToken) "Skjul" else "Vis", color = VariantColors.textSecondary, style = MaterialTheme.typography.labelSmall)
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TextButton(onClick = {
                    val ok = viewModel.testGotify()
                    status = if (ok) "Tilkobling OK" else "Tilkobling feilet"
                }) { Text("Test tilkobling", color = VariantColors.accent) }
                TextButton(onClick = {
                    viewModel.saveGotifyConfig(enabled, server, token)
                    status = "Lagret og synkronisert til bridge"
                }) { Text("Lagre og aktiver", color = VariantColors.online) }
            }
            if (status.isNotBlank()) {
                Text(status, color = VariantColors.textSecondary, style = MaterialTheme.typography.labelSmall)
            }
            Spacer(Modifier.height(8.dp))
            Text("Varsler sendes fra bridgen når oppgaver fullføres/feiler, godkjenning kreves, OMP feiler eller bridgen kobles fra/til. Tokens lagres lokalt og sendes aldri i logger.", color = VariantColors.textSecondary, style = MaterialTheme.typography.labelSmall)
        }
    }
}

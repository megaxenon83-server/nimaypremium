package com.omp.chat.ui.screens

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.omp.chat.ui.theme.MonoStyle
import com.omp.chat.ui.theme.OmpTheme

/**
 * Shown once after an uncaught exception so a phone-only crash can be reported
 * without adb: the stack trace is rendered and can be copied to the clipboard.
 */
@Composable
fun CrashScreen(
    report: String,
    english: Boolean,
    onContinue: () -> Unit
) {
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    OmpTheme(theme = "dark", dynamicColor = false, fontScale = 1f) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Scaffold { padding ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(20.dp)
                ) {
                    Text(
                        text = if (english) "OmpChat stopped" else "OmpChat stoppet",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = if (english) {
                            "The app hit an unexpected error on the previous launch. The technical details are below."
                        } else {
                            "Appen fikk en uventet feil ved forrige oppstart. De tekniske detaljene står under."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(14.dp))
                    Text(
                        text = report,
                        style = MonoStyle,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                    )
                    Spacer(Modifier.height(14.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = {
                                clipboard.setText(AnnotatedString(report))
                                Toast.makeText(
                                    context,
                                    if (english) "Crash report copied" else "Feilmeldingen er kopiert",
                                    Toast.LENGTH_SHORT
                                ).show()
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(if (english) "Copy report" else "Kopier feilmelding")
                        }
                        OutlinedButton(onClick = onContinue, modifier = Modifier.weight(1f)) {
                            Text(if (english) "Continue" else "Fortsett")
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = if (english) {
                            "Tip: send the copied text to the developer, then continue."
                        } else {
                            "Tips: send den kopierte teksten til utvikleren, og trykk Fortsett."
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

package com.neomods.libeditor.crash

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Process
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neomods.libeditor.ui.theme.LibEditorTheme

class CrashActivity : ComponentActivity() {

    companion object {
        const val EXTRA_CRASH_LOG = "crash_log"
        const val EXTRA_CRASH_MESSAGE = "crash_message"
        const val EXTRA_EXCEPTION_TYPE = "exception_type"
        const val EXTRA_STACKTRACE = "stacktrace"
        const val EXTRA_THREAD = "crash_thread"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val crashLog = intent.getStringExtra(EXTRA_CRASH_LOG) ?: "No crash log available"
        val crashMessage = intent.getStringExtra(EXTRA_CRASH_MESSAGE) ?: "Unknown error occurred"
        val exceptionType = intent.getStringExtra(EXTRA_EXCEPTION_TYPE) ?: extractExceptionType(crashLog)
        val stacktrace = intent.getStringExtra(EXTRA_STACKTRACE) ?: extractStacktrace(crashLog)
        val crashThread = intent.getStringExtra(EXTRA_THREAD) ?: "main"

        setContent {
            LibEditorTheme(themeMode = com.neomods.libeditor.domain.ThemeMode.DARK) {
                CrashScreen(
                    crashLog = crashLog,
                    crashMessage = crashMessage,
                    exceptionType = exceptionType,
                    stacktrace = stacktrace,
                    crashThread = crashThread,
                    onRestart = { restartApp() }
                )
            }
        }
    }

    private fun extractExceptionType(log: String): String {
        val match = Regex("""(?m)^Type:\s*(.+)""").find(log)
        if (match != null) return match.groupValues[1].trim()
        val lines = log.lines()
        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.contains("Exception") || trimmed.contains("Error")) {
                return trimmed.substringBefore(":").substringBefore(" ").trim()
            }
        }
        return "UnknownException"
    }

    private fun extractStacktrace(log: String): String {
        val stackStart = log.indexOf("at ")
        if (stackStart == -1) return log
        return log.substring(stackStart)
    }

    private fun restartApp() {
        val intent = packageManager.getLaunchIntentForPackage(packageName)
        if (intent != null) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            startActivity(intent)
        }
        finish()
        Process.killProcess(Process.myPid())
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CrashScreen(
    crashLog: String,
    crashMessage: String,
    exceptionType: String,
    stacktrace: String,
    crashThread: String,
    onRestart: () -> Unit
) {
    val context = LocalContext.current
    var showReportSent by remember { mutableStateOf(false) }
    var reportEnabled by remember { mutableStateOf(CrashReporter.isOptedIn(context)) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Crash Report",
                        fontWeight = FontWeight.Bold
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    titleContentColor = MaterialTheme.colorScheme.onErrorContainer
                ),
                navigationIcon = {
                    Icon(
                        Icons.Default.ErrorOutline,
                        contentDescription = null,
                        modifier = Modifier.padding(start = 16.dp),
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Error info card
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Default.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error
                        )
                        Text(
                            text = exceptionType,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = crashMessage,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        AssistChip(
                            onClick = {},
                            label = { Text(crashThread, fontSize = 11.sp) },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.Smartphone,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        )
                    }
                }
            }

            // Crash log
            ElevatedCard(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Stack Trace",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer
                        ) {
                            Text(
                                text = "${stacktrace.lines().size} lines",
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            .padding(12.dp)
                    ) {
                        Text(
                            text = crashLog,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            lineHeight = 16.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.verticalScroll(rememberScrollState())
                        )
                    }
                }
            }

            // Report switch
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Send anonymous report",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "Help improve the app",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = reportEnabled,
                        onCheckedChange = {
                            reportEnabled = it
                            CrashReporter.setOptedIn(context, it)
                            if (it) {
                                CrashReporter.reportCrash(
                                    context = context,
                                    exceptionType = exceptionType,
                                    message = crashMessage,
                                    stacktrace = stacktrace,
                                    thread = crashThread,
                                    crashLog = crashLog
                                )
                                showReportSent = true
                            }
                        }
                    )
                }
            }

            if (showReportSent) {
                Snackbar(
                    action = {
                        TextButton(onClick = { showReportSent = false }) {
                            Text("OK")
                        }
                    }
                ) {
                    Text("Report sent successfully")
                }
            }

            // Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText("Crash Log", crashLog)
                        clipboard.setPrimaryClip(clip)
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Copy Log")
                }

                Button(
                    onClick = onRestart,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(Icons.Default.RestartAlt, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Restart")
                }
            }
        }
    }
}

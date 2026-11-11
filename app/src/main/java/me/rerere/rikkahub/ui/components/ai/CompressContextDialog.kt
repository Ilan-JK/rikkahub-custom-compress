package me.rerere.rikkahub.ui.components.ai

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.datastore.CompressionRange
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.ui.context.LocalSettings
import org.koin.compose.koinInject

@Composable
fun CompressContextDialog(
    onDismiss: () -> Unit,
    onConfirm: (additionalPrompt: String, range: CompressionRange) -> Job,
) {
    val settings = LocalSettings.current
    val store = koinInject<SettingsStore>()
    val scope = rememberCoroutineScope()
    var additionalPrompt by remember { mutableStateOf("") }
    var range by remember { mutableStateOf(settings.compression.manualRange) }
    var currentJob by remember { mutableStateOf<Job?>(null) }
    val isLoading = currentJob?.isActive == true

    LaunchedEffect(currentJob) {
        currentJob?.join()
        if (currentJob?.isCompleted == true && currentJob?.isCancelled == false) onDismiss()
        currentJob = null
    }
    AlertDialog(
        onDismissRequest = { if (!isLoading) onDismiss() },
        title = { Text(stringResource(R.string.chat_page_compress_context_title)) },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                if (isLoading) {
                    ContainedLoadingIndicator()
                    Text(stringResource(R.string.chat_page_compressing))
                } else {
                    OutlinedTextField(
                        value = additionalPrompt,
                        onValueChange = { additionalPrompt = it },
                        label = { Text("本次附加要求") },
                        maxLines = 4,
                    )
                    CompressionRangeEditor(range) { updated ->
                        range = updated
                        scope.launch { store.update { it.copy(compression = it.compression.copy(manualRange = updated)) } }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (isLoading) currentJob?.cancel() else currentJob = onConfirm(additionalPrompt, range)
            }) { Text(stringResource(if (isLoading) R.string.cancel else R.string.confirm)) }
        },
        dismissButton = {
            if (!isLoading) TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

package me.rerere.rikkahub.ui.pages.setting

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.datastore.CompressionSettings
import me.rerere.rikkahub.data.datastore.CompressionTrigger

@Composable
internal fun CompressionOptionsDialog(
    initial: CompressionSettings,
    onDismiss: () -> Unit,
    onSave: (CompressionSettings) -> Unit,
) {
    var settings by remember(initial) { mutableStateOf(initial) }
    var range by remember(initial) { mutableStateOf(initial.range.toString()) }
    var threshold by remember(initial) { mutableStateOf(initial.messageThreshold.coerceAtLeast(1).toString()) }
    val countMode = settings.trigger == CompressionTrigger.MessageCount
    val effectiveRange = range.toIntOrNull()
    val effectiveThreshold = threshold.toIntOrNull()?.takeIf { it > 0 }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.compression_options)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CompressionSwitch(R.string.compression_use_count, countMode) {
                    settings = settings.copy(trigger = if (it) CompressionTrigger.MessageCount else CompressionTrigger.Context)
                }
                if (countMode) {
                    OutlinedTextField(
                        value = threshold, onValueChange = { threshold = it }, singleLine = true,
                        label = { Text(stringResource(R.string.compression_threshold)) },
                        isError = effectiveThreshold == null, modifier = Modifier.fillMaxWidth(),
                    )
                }
                OutlinedTextField(
                    value = range, onValueChange = { range = it }, singleLine = true,
                    label = { Text(stringResource(R.string.compression_range)) },
                    supportingText = { Text(stringResource(R.string.compression_range_hint)) },
                    isError = effectiveRange == null, modifier = Modifier.fillMaxWidth(),
                )
                CompressionSwitch(R.string.compression_read_all, settings.readAllSummaries) {
                    settings = settings.copy(readAllSummaries = it)
                }
                CompressionSwitch(R.string.compression_system, settings.systemInstruction) {
                    settings = settings.copy(systemInstruction = it)
                }
                Text(stringResource(R.string.compression_request_contents), style = MaterialTheme.typography.titleSmall)
                CompressionSwitch(R.string.compression_assistant, settings.assistantPrompt) {
                    settings = settings.copy(assistantPrompt = it)
                }
                CompressionSwitch(R.string.compression_modes, settings.modeInjections) {
                    settings = settings.copy(modeInjections = it)
                }
                CompressionSwitch(R.string.compression_lore, settings.lorebooks) {
                    settings = settings.copy(lorebooks = it)
                }
                CompressionSwitch(R.string.compression_memory, settings.memory) {
                    settings = settings.copy(memory = it)
                }
                CompressionSwitch(R.string.compression_tools, settings.tools) {
                    settings = settings.copy(tools = it)
                }
                CompressionSwitch(R.string.compression_time, settings.timeReminder) {
                    settings = settings.copy(timeReminder = it)
                }
                CompressionSwitch(R.string.compression_workspace, settings.workspaceReminder) {
                    settings = settings.copy(workspaceReminder = it)
                }
                CompressionSwitch(R.string.compression_template, settings.messageTemplate) {
                    settings = settings.copy(messageTemplate = it)
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = effectiveRange != null && (!countMode || effectiveThreshold != null),
                onClick = {
                    val selectedRange = effectiveRange ?: return@TextButton
                    onSave(settings.copy(
                        range = selectedRange,
                        messageThreshold = effectiveThreshold ?: initial.messageThreshold,
                    ))
                },
            ) { Text(stringResource(R.string.confirm)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun CompressionSwitch(label: Int, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(label), modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}


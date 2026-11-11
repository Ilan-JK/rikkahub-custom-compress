package me.rerere.rikkahub.ui.components.ai

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import me.rerere.rikkahub.data.datastore.CompressionRange
import me.rerere.rikkahub.data.datastore.CompressionRangeMode
import me.rerere.rikkahub.ui.components.ui.ConnectedChoiceButtons
import me.rerere.rikkahub.ui.components.ui.ConnectedChoiceLabel

@Composable
fun CompressionRangeEditor(value: CompressionRange, onChange: (CompressionRange) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ConnectedChoiceButtons(
            options = CompressionRangeMode.entries,
            selected = value.mode,
            onSelect = { onChange(value.copy(mode = it)) },
        ) { mode, selected ->
            ConnectedChoiceLabel(if (mode == CompressionRangeMode.EARLIEST) "压缩最早的信息数" else "保留最近的信息数", selected)
        }
        val earliest = value.mode == CompressionRangeMode.EARLIEST
        CompressionNumberField(
            value = if (earliest) value.earliestCount else value.keepRecentCount,
            minimum = if (earliest) 1 else 0,
            label = if (earliest) "压缩最早的信息数" else "保留最近的信息数",
            onChange = { onChange(if (earliest) value.copy(earliestCount = it) else value.copy(keepRecentCount = it)) },
        )
    }
}

@Composable
fun CompressionNumberField(value: Int, minimum: Int, label: String, onChange: (Int) -> Unit) {
    var text by remember(value, label) { mutableStateOf(value.toString()) }
    OutlinedTextField(
        value = text,
        onValueChange = { input ->
            if (input.length <= 5 && input.all { it in '0'..'9' }) {
                text = input
                input.toIntOrNull()?.takeIf { it >= minimum }?.let(onChange)
            }
        },
        label = { Text(label) },
        isError = text.toIntOrNull()?.let { it < minimum } != false,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
}

package me.rerere.rikkahub.ui.components.message

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.CancellationException
import me.rerere.ai.ui.UIMessage
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.ai.RequestInspection

@Composable
internal fun RequestInspectionDialog(message: UIMessage, preview: suspend () -> String, onDismiss: () -> Unit) {
    val sent = remember(message.id) { RequestInspection.read(message.id) }
    var selected by remember(message.id) { mutableStateOf<Int?>(null) }
    val previewText by produceState<Result<String>?>(null, selected) {
        if (selected != -1) return@produceState
        value = try {
            Result.success(preview())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.compression_view_request)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                if (sent.isEmpty()) Text(stringResource(R.string.compression_no_snapshot))
                sent.forEachIndexed { index, step ->
                    TextButton(onClick = { selected = if (selected == index) null else index }) {
                        Text("${stringResource(R.string.compression_sent)} · ${index + 1}")
                    }
                    if (selected == index) SelectionContainer { Text(step) }
                }
                TextButton(onClick = { selected = if (selected == -1) null else -1 }) {
                    Text(stringResource(R.string.compression_preview))
                }
                if (selected == -1) SelectionContainer {
                    Text(previewText?.getOrElse { it.localizedMessage.orEmpty() } ?: "…")
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.confirm)) } },
    )
}

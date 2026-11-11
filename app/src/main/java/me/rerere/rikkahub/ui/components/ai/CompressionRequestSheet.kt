package me.rerere.rikkahub.ui.components.ai

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import me.rerere.rikkahub.data.datastore.CompressionPromptRole
import me.rerere.rikkahub.data.datastore.CompressionRequestSettings
import me.rerere.rikkahub.ui.components.ui.CardGroup
import me.rerere.rikkahub.ui.components.ui.ConnectedChoiceButtons
import me.rerere.rikkahub.ui.components.ui.ConnectedChoiceLabel
import me.rerere.rikkahub.ui.components.ui.switchItem

@Composable
fun CompressionRequestSheet(
    settings: CompressionRequestSettings,
    onUpdate: ((CompressionRequestSettings) -> CompressionRequestSettings) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberBottomSheetState(
            initialValue = SheetValue.Hidden,
            enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
        ),
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Text(
                    "压缩发送配置",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }
            item {
                CardGroup {
                    formItem(headlineContent = { Text("压缩指令角色") }) {
                        ConnectedChoiceButtons(
                            options = CompressionPromptRole.entries,
                            selected = settings.promptRole,
                            onSelect = { role -> onUpdate { it.copy(promptRole = role) } },
                        ) { role, selected ->
                            ConnectedChoiceLabel(
                                if (role == CompressionPromptRole.USER) "用户请求" else "系统提示词",
                                selected,
                            )
                        }
                    }
                }
            }
            item {
                CardGroup {
                    switchItem(
                        checked = settings.includeAssistantPrompt,
                        onCheckedChange = { checked -> onUpdate { it.copy(includeAssistantPrompt = checked) } },
                        headlineContent = { Text("助手提示词") },
                    )
                    switchItem(
                        checked = settings.includeModeInjections,
                        onCheckedChange = { checked -> onUpdate { it.copy(includeModeInjections = checked) } },
                        headlineContent = { Text("模式插入提示词") },
                    )
                    switchItem(
                        checked = settings.includeLorebooks,
                        onCheckedChange = { checked -> onUpdate { it.copy(includeLorebooks = checked) } },
                        headlineContent = { Text("世界书") },
                    )
                    switchItem(
                        checked = settings.includeMemory,
                        onCheckedChange = { checked -> onUpdate { it.copy(includeMemory = checked) } },
                        headlineContent = { Text("记忆") },
                    )
                    switchItem(
                        checked = settings.includeTools,
                        onCheckedChange = { checked -> onUpdate { it.copy(includeTools = checked) } },
                        headlineContent = { Text("工具定义与说明") },
                    )
                    switchItem(
                        checked = settings.includeTimeReminders,
                        onCheckedChange = { checked -> onUpdate { it.copy(includeTimeReminders = checked) } },
                        headlineContent = { Text("时间提醒") },
                    )
                    switchItem(
                        checked = settings.includeWorkspace,
                        onCheckedChange = { checked -> onUpdate { it.copy(includeWorkspace = checked) } },
                        headlineContent = { Text("工作区提示") },
                    )
                    switchItem(
                        checked = settings.includeMessageTemplate,
                        onCheckedChange = { checked -> onUpdate { it.copy(includeMessageTemplate = checked) } },
                        headlineContent = { Text("信息模板") },
                    )
                }
            }
        }
    }
}

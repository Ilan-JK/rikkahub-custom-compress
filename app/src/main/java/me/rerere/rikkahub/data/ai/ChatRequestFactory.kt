package me.rerere.rikkahub.data.ai

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import me.rerere.ai.agent.AgentHooks
import me.rerere.ai.agent.AgentRequest
import me.rerere.ai.agent.RetryPolicy
import me.rerere.ai.agent.StepRequest
import me.rerere.ai.agent.expandNotices
import me.rerere.ai.agent.withRetry
import me.rerere.ai.core.MessageRole
import me.rerere.ai.core.ReasoningLevel
import me.rerere.ai.core.Tool
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ProviderManager
import me.rerere.ai.provider.ProviderSetting
import me.rerere.ai.provider.TextGenerationParams
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.ai.ui.limitContext
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.ai.prompts.buildMemoryPrompt
import me.rerere.rikkahub.data.ai.tools.ChatToolFactory
import me.rerere.rikkahub.data.ai.transformers.Base64ImageToLocalFileTransformer
import me.rerere.rikkahub.data.ai.transformers.DocumentAsPromptTransformer
import me.rerere.rikkahub.data.ai.transformers.InputMessageTransformer
import me.rerere.rikkahub.data.ai.transformers.OcrTransformer
import me.rerere.rikkahub.data.ai.transformers.OutputMessageTransformer
import me.rerere.rikkahub.data.ai.transformers.PlaceholderTransformer
import me.rerere.rikkahub.data.ai.transformers.PromptInjectionTransformer
import me.rerere.rikkahub.data.ai.transformers.RegexOutputTransformer
import me.rerere.rikkahub.data.ai.transformers.TemplateTransformer
import me.rerere.rikkahub.data.ai.transformers.ThinkTagTransformer
import me.rerere.rikkahub.data.ai.transformers.TimeReminderTransformer
import me.rerere.rikkahub.data.ai.transformers.WorkspaceReminderTransformer
import me.rerere.rikkahub.data.ai.transformers.onGenerationFinish
import me.rerere.rikkahub.data.ai.transformers.transformThinkTags
import me.rerere.rikkahub.data.ai.transformers.transforms
import me.rerere.rikkahub.data.ai.transformers.visualTransforms
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.datastore.CompressionPromptRole
import me.rerere.rikkahub.data.datastore.CompressionRequestSettings
import me.rerere.rikkahub.data.datastore.findProvider
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.data.model.MemoryFile
import me.rerere.rikkahub.data.model.getAssistantOf
import me.rerere.rikkahub.data.model.getChatModelOf
import me.rerere.rikkahub.data.repository.MemoryRepository
import me.rerere.rikkahub.data.repository.WorkspaceRepository
import me.rerere.rikkahub.data.repository.memoryId
import kotlin.time.Clock
import kotlin.uuid.Uuid

private const val TAG = "ChatRequestFactory"

/**
 * 一轮对话的请求上下文，按会话固定的配置解析一次。
 *
 * 压缩默认复用正常对话配置，也可按压缩请求设置排除部分内容。
 */
class ChatTurnContext(
    val settings: Settings,
    val assistant: Assistant,
    val model: Model,
    val providerSetting: ProviderSetting,
    val conversationId: Uuid,
    val conversationSystemPrompt: String?,
    val workspaceCwd: String?,
    val memories: List<MemoryFile>,
    val tools: List<Tool>,
)

/** 把会话变成发给模型的请求：系统提示词、上下文裁剪、输入输出转换都在这里 */
class ChatRequestFactory(
    private val context: Context,
    private val providerManager: ProviderManager,
    private val usageRecorder: UsageRecorder,
    private val chatToolFactory: ChatToolFactory,
    private val memoryRepository: MemoryRepository,
    templateTransformer: TemplateTransformer,
    workspaceRepository: WorkspaceRepository,
) {
    private val inputTransformers: List<InputMessageTransformer> = listOf(
        TimeReminderTransformer,
        PromptInjectionTransformer,
        PlaceholderTransformer,
        DocumentAsPromptTransformer,
        OcrTransformer,
        templateTransformer,
        WorkspaceReminderTransformer(workspaceRepository),
    )

    private val outputTransformers: List<OutputMessageTransformer> = listOf(
        ThinkTagTransformer,
        Base64ImageToLocalFileTransformer,
        RegexOutputTransformer,
    )

    /** 解析本轮的助手、模型、记忆和工具；MCP 服务名不合法时抛出 InvalidMcpServerNamesException */
    suspend fun createContext(settings: Settings, conversation: Conversation, compression: Boolean = false): ChatTurnContext {
        // 模型、思考级别、搜索、工具等以会话上固定的配置为准
        val assistant = settings.getAssistantOf(conversation)
        val model = settings.getChatModelOf(conversation)
            ?: throw IllegalStateException("No chat model selected")
        val provider = model.findProvider(settings.providers) ?: error("Provider not found")
        return ChatTurnContext(
            settings = settings,
            assistant = assistant,
            model = model,
            providerSetting = provider,
            conversationId = conversation.id,
            conversationSystemPrompt = conversation.customSystemPrompt,
            workspaceCwd = conversation.workspaceCwd,
            memories = if (assistant.enableMemory && (!compression || settings.compression.request.includeMemory)) {
                memoryRepository.getFiles(assistant.memoryId)
            } else emptyList(),
            tools = if (compression && !settings.compression.request.includeTools) emptyList() else chatToolFactory.createTools(
                settings = settings,
                assistant = assistant,
                model = model,
                conversationId = conversation.id,
                workspaceCwd = conversation.workspaceCwd,
            ),
        )
    }

    /**
     * 本轮交给 runAgent 的请求和钩子。[messages] 的最后一条是助手消息时从它继续，否则新建一条助手消息。
     * [processingStatus] 用来显示 OCR 这类输入转换的进度；[takePendingParts] 取出要在请求模型前写进助手消息的通知。
     * [pauseAfterTools] 在一批工具执行完之后调用，返回 true 时这次运行以 Paused 结束。
     */
    fun createAgent(
        turn: ChatTurnContext,
        messages: List<UIMessage>,
        processingStatus: MutableStateFlow<String?>,
        takePendingParts: suspend () -> List<UIMessagePart> = { emptyList() },
        pauseAfterTools: (UIMessage) -> Boolean = { false },
    ): Pair<AgentRequest, AgentHooks> {
        val request = AgentRequest(
            provider = usageRecorder.wrap(
                providerManager.getProviderByType(turn.providerSetting),
                UsageTag(UsageSource.CHAT, turn.assistant.id, turn.conversationId),
            ),
            providerSetting = turn.providerSetting,
            messages = messages,
            tools = turn.tools,
            stream = turn.assistant.streamOutput,
            retry = turn.settings.retryPolicy(),
        )
        val hooks = object : AgentHooks {
            override suspend fun prepareStep(messages: List<UIMessage>): StepRequest {
                Log.i(TAG, "prepareStep: ${turn.model.id}")
                return buildStep(turn, messages, processingStatus)
            }

            override suspend fun previewStep(message: UIMessage, newPartsFrom: Int) =
                message.transformNewParts(newPartsFrom) {
                    it.visualTransforms(outputTransformers, context, turn.model, turn.assistant, turn.settings)
                }

            override suspend fun finishStep(message: UIMessage, newPartsFrom: Int) =
                message.transformNewParts(newPartsFrom) {
                    it.transforms(outputTransformers, context, turn.model, turn.assistant, turn.settings)
                        .visualTransforms(outputTransformers, context, turn.model, turn.assistant, turn.settings)
                        .onGenerationFinish(outputTransformers, context, turn.model, turn.assistant, turn.settings)
                }

            override suspend fun pendingParts() = takePendingParts()

            override suspend fun shouldPause(message: UIMessage) = pauseAfterTools(message)
        }
        return request to hooks
    }

    /**
     * 生成压缩摘要。
     *
     * 手动、自动及中途压缩共用发送内容和指令角色设置。
     * 默认保留正常对话前缀；修改发送内容或角色可能影响提示词缓存。
     */
    suspend fun generateCompressionSummary(
        turn: ChatTurnContext,
        messages: List<UIMessage>,
        compressPrompt: String,
    ): String {
        // 组装请求要把全部历史过一遍输入转换器，长对话要算好几秒：不能放在调用方的主线程上，
        // 这段纯计算里也检查不到取消。放进独立的协程，调用方取消时 await 立刻返回、不用等它算完；
        // 没算完的那份同时被取消，在下一个挂起点停下，最迟在发出请求之前
        val work = CoroutineScope(Dispatchers.IO).async { requestCompressionSummary(turn, messages, compressPrompt) }
        return try {
            work.await()
        } finally {
            work.cancel()
        }
    }

    private suspend fun requestCompressionSummary(
        turn: ChatTurnContext,
        messages: List<UIMessage>,
        compressPrompt: String,
    ): String {
        val (requestMessages, params) = buildStep(turn, messages, compression = turn.settings.compression.request)
        // 压缩指令不经过输入转换器，模板转换器会把其中的花括号当成模板语法
        val result = withRetry(
            policy = turn.settings.retryPolicy(),
            onRetry = { Log.w(TAG, "Compression request failed, retrying in ${it.delay}", it.cause) },
        ) {
            usageRecorder.wrap(
                providerManager.getProviderByType(turn.providerSetting),
                UsageTag(UsageSource.COMPRESSION, turn.assistant.id, turn.conversationId),
            ).generateText(
                providerSetting = turn.providerSetting,
                messages = requestMessages.withCompressionPrompt(compressPrompt, turn.settings.compression.request.promptRole),
                // 助手的输出上限是给聊天回复定的，结构化摘要写不完会被截断；max_tokens 不属于请求前缀，改了不影响缓存
                params = params.copy(maxTokens = null),
            )
        }
        val summary = result.message.compressionSummaryOrNull()
            ?: error(context.getString(R.string.chat_page_compress_tool_call_response))
        return summary.ifBlank { error("Failed to generate compressed summary") }
    }

    /** 正常对话保持原有组装方式，压缩请求按共用配置筛选附加内容。 */
    private suspend fun buildStep(
        turn: ChatTurnContext,
        messages: List<UIMessage>,
        processingStatus: MutableStateFlow<String?> = MutableStateFlow(null),
        compression: CompressionRequestSettings? = null,
    ): StepRequest {
        val assistant = turn.assistant.forCompression(compression)
        val tools = if (compression?.includeTools == false) emptyList() else turn.tools
        val requestMessages = buildList {
            val system = buildRequestSystemPrompt(
                assistant = assistant,
                conversationSystemPrompt = turn.conversationSystemPrompt,
                memoryPrompt = if (assistant.enableMemory && compression?.includeMemory != false) {
                    buildMemoryPrompt(turn.memories)
                } else null,
                toolPrompts = tools.map { it.systemPrompt(turn.model, messages) },
                compression = compression,
            )
            if (system.isNotBlank()) {
                add(UIMessage.system(prompt = system).copy(isSynthetic = true))
            }
            addAll(
                messages.limitContext(assistant.contextMessageLimit, turn.settings.compression.summaryMode)
                    .withContinuationHint()
            )
        }.transforms(
            transformers = inputTransformers.filter { it.includedInCompression(compression) },
            context = context,
            model = turn.model,
            assistant = assistant,
            settings = turn.settings,
            processingStatus = processingStatus,
            workspaceCwd = turn.workspaceCwd,
        ).expandNotices().let {
            if (compression?.includeTools == false) it.withToolHistoryAsText() else it
        }
        val params = TextGenerationParams(
            model = turn.model,
            temperature = assistant.temperature,
            topP = assistant.topP,
            maxTokens = assistant.maxTokens,
            tools = tools,
            reasoningLevel = assistant.reasoningLevel,
            customHeaders = buildList {
                addAll(assistant.customHeaders)
                addAll(turn.model.customHeaders)
            },
            customBody = buildList {
                addAll(assistant.customBodies)
                addAll(turn.model.customBodies)
            },
            sessionId = turn.conversationId.toString(),
        )
        return StepRequest(requestMessages, params)
    }
}

internal fun buildRequestSystemPrompt(
    assistant: Assistant,
    conversationSystemPrompt: String?,
    memoryPrompt: String?,
    toolPrompts: List<String>,
    compression: CompressionRequestSettings?,
): String = buildString {
    if (compression?.includeAssistantPrompt != false) {
        append(
            if (assistant.allowConversationSystemPrompt && !conversationSystemPrompt.isNullOrBlank()) {
                conversationSystemPrompt
            } else assistant.systemPrompt
        )
    }
    if (assistant.enableMemory && compression?.includeMemory != false && memoryPrompt != null) {
        appendLine()
        append(memoryPrompt)
    }
    if (compression?.includeTools != false) {
        toolPrompts.forEach {
            appendLine()
            append(it)
        }
    }
}

internal fun Assistant.forCompression(options: CompressionRequestSettings?): Assistant = if (options == null) this else copy(
    modeInjectionIds = if (options.includeModeInjections) modeInjectionIds else emptySet(),
    lorebookIds = if (options.includeLorebooks) lorebookIds else emptySet(),
)

internal fun InputMessageTransformer.includedInCompression(options: CompressionRequestSettings?): Boolean = when {
    options == null -> true
    this is TimeReminderTransformer -> options.includeTimeReminders
    this is WorkspaceReminderTransformer -> options.includeWorkspace
    this is TemplateTransformer -> options.includeMessageTemplate
    else -> true
}

internal fun List<UIMessage>.withCompressionPrompt(prompt: String, role: CompressionPromptRole): List<UIMessage> {
    if (role == CompressionPromptRole.USER) return this + UIMessage.user(prompt)
    // Claude 和 Google 只读取第一条系统信息，必须合并，且压缩指令放在系统内容末尾。
    val systemParts = filter { it.role == MessageRole.SYSTEM }.flatMap { it.parts }
    val system = UIMessage.system("").copy(
        parts = systemParts + UIMessagePart.Text(prompt),
        isSynthetic = true,
    )
    return listOf(system) + filter { it.role != MessageRole.SYSTEM }
}

@Suppress("DEPRECATION")
internal fun List<UIMessage>.withToolHistoryAsText(): List<UIMessage> = map { message ->
    message.copy(
        role = if (message.role == MessageRole.TOOL) MessageRole.USER else message.role,
        parts = message.parts.flatMap { part ->
            when (part) {
                is UIMessagePart.Tool -> listOf(
                    UIMessagePart.Text("[Tool: ${part.toolName}]\nInput: ${part.input}\nResult:"),
                ) + part.output
                is UIMessagePart.ServerTool -> listOf(
                    UIMessagePart.Text("[Server tool: ${part.toolName}]\nInput: ${part.input}\nResult: ${part.output}"),
                )
                is UIMessagePart.ToolCall -> listOf(
                    UIMessagePart.Text("[Tool: ${part.toolName}]\nInput: ${part.arguments}"),
                )
                is UIMessagePart.ToolResult -> listOf(
                    UIMessagePart.Text("[Tool result: ${part.toolName}]\n${part.content}"),
                )
                else -> listOf(part)
            }
        },
    )
}

private const val CONTINUATION_HINT =
    "The conversation history before this point was replaced by the summary above. " +
        "If a task was in progress, continue it from where it left off: do not reply to the summary itself, " +
        "and do not ask the user to repeat anything."

/**
 * 检查点后面不是用户消息时（一轮中途压缩，后面直接是助手的回复或什么都没有），告诉模型接着干活，
 * 否则它会把摘要当成用户发来的内容去回应。
 *
 * 按"后面不是用户消息"而不是"在末尾"判断：这一轮之后的请求里检查点仍然带着这段话，前缀不变，不影响提示词缓存。
 */
internal fun List<UIMessage>.withContinuationHint(): List<UIMessage> {
    val index = indexOfLast { it.isContextCheckpoint }
    if (index < 0 || getOrNull(index + 1)?.role == MessageRole.USER) return this
    val checkpoint = this[index]
    return toMutableList().also {
        it[index] = checkpoint.copy(parts = checkpoint.parts + UIMessagePart.Text(CONTINUATION_HINT))
    }
}

/**
 * 取出压缩请求回复里的摘要正文。
 *
 * 指令要求不调用工具，模型仍可能发起调用。这时附带的文本只是"我先看看文件"之类的说明，
 * 当成摘要保存会让之后的请求丢掉全部历史，所以返回 null 交给调用方报错。
 * 压缩请求不经过输出转换器，正文开头的 <think> 思考内容也要在这里剥掉，否则会存进摘要。
 */
internal fun UIMessage.compressionSummaryOrNull(): String? {
    if (getTools().isNotEmpty()) return null
    return listOf(this)
        .transformThinkTags(now = Clock.System.now(), generationFinished = true)
        .single()
        .toText()
        .trim()
}

/**
 * 输出转换只处理这一步新生成的 part：之前步骤的内容已经处理过，再处理一遍会让正则这类改写叠加，
 * 也能让每一步开头的 <think> 都被识别出来。
 */
internal suspend fun UIMessage.transformNewParts(
    newPartsFrom: Int,
    transform: suspend (List<UIMessage>) -> List<UIMessage>,
): UIMessage {
    val transformed = transform(listOf(copy(parts = parts.drop(newPartsFrom)))).single()
    return transformed.copy(parts = parts.take(newPartsFrom) + transformed.parts)
}

/** 标题、建议、记忆整理这类后台请求的参数：只带模型自己的自定义请求头和请求体 */
internal fun backgroundTextGenerationParams(
    model: Model,
    sessionId: Uuid,
    reasoningLevel: ReasoningLevel = ReasoningLevel.AUTO,
): TextGenerationParams = TextGenerationParams(
    model = model,
    reasoningLevel = reasoningLevel,
    customHeaders = model.customHeaders,
    customBody = model.customBodies,
    sessionId = sessionId.toString(),
)

internal fun Settings.retryPolicy() =
    if (networkSetting.enableAutoRetry) RetryPolicy.NETWORK else RetryPolicy.NONE

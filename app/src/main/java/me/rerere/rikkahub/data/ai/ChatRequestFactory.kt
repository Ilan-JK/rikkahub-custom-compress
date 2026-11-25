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
import me.rerere.ai.provider.ModelAbility
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
import me.rerere.rikkahub.data.datastore.findProvider
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.data.model.MemoryFile
import me.rerere.rikkahub.data.model.getAssistantOf
import me.rerere.rikkahub.data.model.getChatModelOf
import me.rerere.rikkahub.data.model.summariesEnabled
import me.rerere.rikkahub.data.repository.MemoryRepository
import me.rerere.rikkahub.data.repository.WorkspaceRepository
import me.rerere.rikkahub.data.repository.memoryId
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import kotlin.time.Clock
import kotlin.uuid.Uuid

private const val TAG = "ChatRequestFactory"

/**
 * 一轮对话的请求上下文，按会话固定的配置解析一次。
 *
 * 正常对话和压缩共享会话的模型与 sessionId，压缩可按设置选择提示词和工具。
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
    val summariesEnabled: Boolean = true,
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
    suspend fun createContext(settings: Settings, conversation: Conversation, forCompression: Boolean = false): ChatTurnContext {
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
            memories = if (assistant.enableMemory && (!forCompression || settings.compression.memory)) {
                memoryRepository.getFiles(assistant.memoryId)
            } else emptyList(),
            tools = if (!forCompression || settings.compression.tools) chatToolFactory.createTools(
                settings = settings,
                assistant = assistant,
                model = model,
                conversationId = conversation.id,
                workspaceCwd = conversation.workspaceCwd,
            ) else emptyList(),
            summariesEnabled = conversation.summariesEnabled,
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
        firstResponseElapsedMs: (() -> Long)? = null,
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
            firstResponseElapsedMs = firstResponseElapsedMs,
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

            override fun onModelRequest(reply: UIMessage, step: StepRequest) {
                RequestInspection.record(reply.id, step)
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
     * 使用与正常对话相同的模型和消息转换，按压缩设置筛选内容并加入压缩指令。
     */
    suspend fun generateCompressionSummary(
        turn: ChatTurnContext,
        messages: List<UIMessage>,
        compressPrompt: String,
        inspect: (StepRequest) -> Unit = {},
    ): String {
        // 组装请求要把全部历史过一遍输入转换器，长对话要算好几秒：不能放在调用方的主线程上，
        // 这段纯计算里也检查不到取消。放进独立的协程，调用方取消时 await 立刻返回、不用等它算完；
        // 没算完的那份同时被取消，在下一个挂起点停下，最迟在发出请求之前
        val work = CoroutineScope(Dispatchers.IO).async { requestCompressionSummary(turn, messages, compressPrompt, inspect) }
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
        inspect: (StepRequest) -> Unit,
    ): String {
        val step = previewRequest(turn, messages, compressPrompt)
        val (requestMessages, params) = step
        val result = withRetry(
            policy = turn.settings.retryPolicy(),
            onRetry = { Log.w(TAG, "Compression request failed, retrying in ${it.delay}", it.cause) },
        ) {
            inspect(step)
            usageRecorder.wrap(
                providerManager.getProviderByType(turn.providerSetting),
                UsageTag(UsageSource.COMPRESSION, turn.assistant.id, turn.conversationId),
            ).generateText(
                providerSetting = turn.providerSetting,
                messages = requestMessages,
                // 助手的输出上限是给聊天回复定的，结构化摘要写不完会被截断；max_tokens 不属于请求前缀，改了不影响缓存
                params = params,
            )
        }
        check(result.finishReason?.lowercase() !in setOf("length", "max_tokens", "max_output_tokens", "incomplete")) {
            context.getString(R.string.compression_incomplete)
        }
        val summary = result.message.compressionSummaryOrNull()
            ?: error(context.getString(R.string.chat_page_compress_tool_call_response))
        return summary.ifBlank { error("Failed to generate compressed summary") }
    }

    internal suspend fun previewRequest(turn: ChatTurnContext, messages: List<UIMessage>, compressPrompt: String? = null): StepRequest {
        val step = buildStep(turn, messages, compressPrompt = compressPrompt)
        return if (compressPrompt == null) step else step.copy(params = step.params.copy(maxTokens = null))
    }

    /** 一步请求交给供应商适配层的消息和参数，正常对话和压缩共用。 */
    private suspend fun buildStep(
        turn: ChatTurnContext,
        messages: List<UIMessage>,
        processingStatus: MutableStateFlow<String?> = MutableStateFlow(null),
        compressPrompt: String? = null,
    ): StepRequest {
        val compression = turn.settings.compression.takeIf { compressPrompt != null }
        // Historical compression previews still use the summaries that existed for that request.
        val summariesEnabled = compression != null || turn.summariesEnabled
        val toolPromptHistory = if (summariesEnabled) messages else messages.filterNot { it.isContextCheckpoint }
        val assistant = turn.assistant.copy(
            modeInjectionIds = if (compression?.modeInjections == false) emptySet() else turn.assistant.modeInjectionIds,
            lorebookIds = if (compression?.lorebooks == false) emptySet() else turn.assistant.lorebookIds,
        )
        val requestMessages = buildList {
            val system = buildString {
                val effectiveSystemPrompt =
                    if (assistant.allowConversationSystemPrompt && !turn.conversationSystemPrompt.isNullOrBlank()) {
                        turn.conversationSystemPrompt
                    } else {
                        assistant.systemPrompt
                    }
                if (compression?.assistantPrompt != false && effectiveSystemPrompt.isNotBlank()) {
                    append(effectiveSystemPrompt)
                }

                // 记忆
                if (assistant.enableMemory && compression?.memory != false) {
                    appendLine()
                    append(buildMemoryPrompt(turn.memories))
                }
                // 工具prompt
                turn.tools.forEach { tool ->
                    appendLine()
                    append(tool.systemPrompt(turn.model, toolPromptHistory))
                }
            }
            if (system.isNotBlank()) {
                add(UIMessage.system(prompt = system).copy(isSynthetic = true))
            }
            val history = messages.limitContext(
                if (compression == null) assistant.contextMessageLimit else 0,
                turn.settings.compression.readAllSummaries,
                summariesEnabled,
            )
            addAll(if (compression == null) history.withContinuationHint() else history)
        }.transforms(
            transformers = if (compression == null) inputTransformers else inputTransformers.filter {
                when (it) {
                    TimeReminderTransformer -> compression.timeReminder
                    is WorkspaceReminderTransformer -> compression.workspaceReminder
                    is TemplateTransformer -> compression.messageTemplate
                    else -> true
                }
            },
            context = context,
            model = turn.model,
            assistant = assistant,
            settings = turn.settings,
            processingStatus = processingStatus,
            workspaceCwd = turn.workspaceCwd,
        ).expandNotices().let { prepared ->
            if (compressPrompt == null) prepared else {
                val firstOriginal = prepared.indexOfFirst { it.isUncompressedRequestMessage() }
                val lastSummary = prepared.indexOfLast { it.isContextCheckpoint }
                val instruction = if (compression?.readAllSummaries == true && firstOriginal in 0 until lastSummary) {
                    "$compressPrompt\n\nFor this request, summarize the messages inside all <uncompressed_messages> " +
                        "blocks, wherever they appear relative to <compression_summary> blocks. " +
                        "Use existing summaries only as background."
                } else compressPrompt
                val tagged = prepared.map { message ->
                    if (message.isContextCheckpoint) message.copy(
                        parts = listOf(UIMessagePart.Text("<compression_summary>\n${message.toText()}\n</compression_summary>"))
                    ) else message
                }
                if (compression?.systemInstruction == true) {
                    val system = tagged.filter { it.role == MessageRole.SYSTEM }.map { it.toText() }
                    listOf(UIMessage.system((system + instruction).joinToString("\n\n")).copy(isSynthetic = true)) +
                        tagged.filterNot { it.role == MessageRole.SYSTEM }
                } else tagged + UIMessage.user(instruction)
            }
        }
        val params = TextGenerationParams(
            model = turn.model,
            temperature = assistant.temperature,
            topP = assistant.topP,
            maxTokens = assistant.maxTokens,
            tools = turn.tools,
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
        return StepRequest(
            requestMessages.trimRequestReasoning(
                keepCurrentToolTurn = compressPrompt == null || (
                    compression?.systemInstruction == true &&
                        messages.lastOrNull()?.parts?.lastOrNull() is UIMessagePart.Tool
                    ),
                replayAllForTools = turn.providerSetting.replaysReasoningAcrossToolTurns(params),
                allowReasoning = (turn.providerSetting as? ProviderSetting.OpenAI)
                    ?.let { it.useResponseApi || it.includeHistoryReasoning } ?: true,
            ).let { trimmed ->
                if (compression?.readAllSummaries == true) {
                    trimmed.wrapUncompressedMessages(trailingInstruction = !compression.systemInstruction)
                } else trimmed
            },
            params,
        )
    }
}

private fun UIMessage.isUncompressedRequestMessage(): Boolean =
    !isContextCheckpoint && !isSynthetic && role != MessageRole.SYSTEM

/** Mark each run of uncovered messages so intervening summaries stay outside the tags. */
internal fun List<UIMessage>.wrapUncompressedMessages(trailingInstruction: Boolean = false): List<UIMessage> {
    val historySize = size - if (trailingInstruction) 1 else 0
    val starts = hashSetOf<Int>()
    val ends = hashSetOf<Int>()
    var lastOriginal: Int? = null
    for (index in 0 until historySize) {
        val message = this[index]
        if (message.isContextCheckpoint) {
            lastOriginal?.let { ends.add(it) }
            lastOriginal = null
        } else if (message.isUncompressedRequestMessage()) {
            if (lastOriginal == null) starts.add(index)
            lastOriginal = index
        }
    }
    lastOriginal?.let { ends.add(it) }
    if (starts.isEmpty()) return this

    return mapIndexed { index, message ->
        if (index !in starts && index !in ends) return@mapIndexed message
        val parts = message.parts.toMutableList()
        if (index in starts) {
            val text = parts.firstOrNull() as? UIMessagePart.Text
            if (text == null) parts.add(0, UIMessagePart.Text("<uncompressed_messages>\n"))
            else parts[0] = text.copy(text = "<uncompressed_messages>\n${text.text}")
        }
        if (index in ends) {
            val text = parts.lastOrNull() as? UIMessagePart.Text
            if (text == null) parts.add(UIMessagePart.Text("\n</uncompressed_messages>"))
            else parts[parts.lastIndex] = text.copy(text = "${text.text}\n</uncompressed_messages>")
        }
        message.copy(parts = parts)
    }
}

private fun ProviderSetting.replaysReasoningAcrossToolTurns(params: TextGenerationParams): Boolean =
    this is ProviderSetting.OpenAI && !useResponseApi &&
        baseUrl.toHttpUrlOrNull()?.host == "api.deepseek.com" &&
        params.reasoningLevel.isEnabled &&
        ModelAbility.REASONING in params.model.abilities &&
        ModelAbility.TOOL in params.model.abilities && params.tools.isNotEmpty()

/** Keep reasoning only where a provider may need it to continue a tool exchange. */
internal fun List<UIMessage>.trimRequestReasoning(
    keepCurrentToolTurn: Boolean,
    replayAllForTools: Boolean = false,
    allowReasoning: Boolean = true,
): List<UIMessage> {
    if (allowReasoning && replayAllForTools) return this

    val lastUser = indexOfLast { it.role == MessageRole.USER && !it.isSynthetic }
    val currentToolTurn = allowReasoning && keepCurrentToolTurn &&
        (lastUser + 1 until size).any { this[it].hasPart<UIMessagePart.Tool>() }
    return mapIndexedNotNull { index, message ->
        if (message.role != MessageRole.ASSISTANT || (currentToolTurn && index > lastUser)) {
            return@mapIndexedNotNull message
        }
        val parsed = listOf(message).transformThinkTags(now = Clock.System.now(), generationFinished = false).single()
        val parts = parsed.parts.filterNot { it is UIMessagePart.Reasoning }
        if (parts.size == parsed.parts.size) message else parsed.copy(parts = parts).takeIf { it.isValidToUpload() }
    }
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

package me.rerere.rikkahub.data.datastore

import kotlinx.serialization.Serializable

@Serializable
enum class CompressionTrigger { Context, MessageCount }

@Serializable
data class CompressionSettings(
    val trigger: CompressionTrigger = CompressionTrigger.Context,
    val messageThreshold: Int = 50,
    val range: Int = 0,
    val mainPrompt: String = "",
    val midTurnPrompt: String = "",
    val readAllSummaries: Boolean = false,
    val systemInstruction: Boolean = false,
    val assistantPrompt: Boolean = true,
    val modeInjections: Boolean = true,
    val lorebooks: Boolean = true,
    val memory: Boolean = true,
    val tools: Boolean = true,
    val timeReminder: Boolean = true,
    val workspaceReminder: Boolean = true,
    val messageTemplate: Boolean = true,
)

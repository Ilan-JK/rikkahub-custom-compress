package me.rerere.rikkahub.data.datastore.migration

import androidx.datastore.core.DataMigration
import androidx.datastore.preferences.core.Preferences
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import me.rerere.rikkahub.data.datastore.CompressionTrigger
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.utils.JsonInstant
import me.rerere.rikkahub.utils.jsonPrimitiveOrNull

internal class PreferenceStoreV6Migration : DataMigration<Preferences> {
    override suspend fun shouldMigrate(currentData: Preferences): Boolean =
        (currentData[SettingsStore.VERSION] ?: 0) < 6

    override suspend fun migrate(currentData: Preferences): Preferences {
        val prefs = currentData.toMutablePreferences()
        prefs[SettingsStore.COMPRESSION]?.let { stored ->
            val old = JsonInstant.parseToJsonElement(stored)
            val migrated = migrateCompressionSettings(old)
            if (migrated != old) {
                prefs[SettingsStore.COMPRESSION] = JsonInstant.encodeToString(migrated)
            }
        }
        prefs[SettingsStore.VERSION] = 6
        return prefs.toPreferences()
    }

    override suspend fun cleanUp() {}
}

/** Convert the 3.2.0 fork's configuration for both installed settings and backups. */
internal fun migrateCompressionSettings(element: JsonElement): JsonElement {
    val original = element as? JsonObject ?: return element
    val migrated = original.toMutableMap()
    when (original["trigger"]?.jsonPrimitiveOrNull?.contentOrNull) {
        "TOKEN_RATIO" -> migrated["trigger"] = JsonPrimitive(CompressionTrigger.Context.name)
        "MESSAGE_COUNT" -> migrated["trigger"] = JsonPrimitive(CompressionTrigger.MessageCount.name)
    }
    migrated.remove("modelId")
    migrated.remove("reasoningLevel")
    migrated.remove("prompt")?.let { migrated.putIfAbsent("mainPrompt", it) }
    val automaticRange = migrated.remove("automaticRange") as? JsonObject
    val manualRange = migrated.remove("manualRange") as? JsonObject
    // There is one persistent range now. Keep the automatic range to preserve unattended behavior.
    (automaticRange ?: manualRange)?.let { range ->
        val signedRange = when (range["mode"]?.jsonPrimitiveOrNull?.contentOrNull) {
            "EARLIEST" -> (range["earliestCount"]?.jsonPrimitiveOrNull?.intOrNull ?: 20).coerceAtLeast(1)
            "KEEP_RECENT" -> -(range["keepRecentCount"]?.jsonPrimitiveOrNull?.intOrNull ?: 20).coerceAtLeast(0)
            else -> null
        }
        signedRange?.let { migrated.putIfAbsent("range", JsonPrimitive(it)) }
    }
    migrated.remove("summaryMode")?.jsonPrimitiveOrNull?.contentOrNull?.let { mode ->
        migrated.putIfAbsent("readAllSummaries", JsonPrimitive(mode == "ALL"))
    }
    val request = migrated.remove("request") as? JsonObject
    request?.get("promptRole")?.jsonPrimitiveOrNull?.contentOrNull?.let { role ->
        migrated.putIfAbsent("systemInstruction", JsonPrimitive(role == "SYSTEM"))
    }
    if (request != null) {
        val fields = mapOf(
            "includeAssistantPrompt" to "assistantPrompt",
            "includeModeInjections" to "modeInjections",
            "includeLorebooks" to "lorebooks",
            "includeMemory" to "memory",
            "includeTools" to "tools",
            "includeTimeReminders" to "timeReminder",
            "includeWorkspace" to "workspaceReminder",
            "includeMessageTemplate" to "messageTemplate",
        )
        for ((old, new) in fields) {
            request[old]?.let { migrated.putIfAbsent(new, it) }
        }
    }
    return JsonObject(migrated)
}

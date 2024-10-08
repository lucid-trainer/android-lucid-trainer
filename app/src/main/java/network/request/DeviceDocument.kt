package network.request

import java.time.LocalDateTime

data class DeviceDocument (
    val timestamp: String,
    val readingTimestamp: String,
    val eventType: String,
    val lastAwakeTimestamp: String,
    val lastPromptTimestamp: String,
    val coolDownDateTime: String,
    val isInCooldownPeriod: Boolean,
    val startPromptAllowPeriod: String,
    val isInPromptWindow: Boolean,
    val intensity: Int,
    val prompt_allowed: Boolean,
    val fgFilesUsed: Int,
    val mainFilesUsed: Int,
    val debugLog: String
)
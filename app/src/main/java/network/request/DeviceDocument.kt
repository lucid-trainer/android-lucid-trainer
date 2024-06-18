package network.request

import java.time.LocalDateTime

data class DeviceDocument (
    val timestamp: String,
    val readingTimestamp: String,
    val eventType: String,
    val lastAwakeTimestamp: String,
    val lastPromptTimestamp: String,
    val followUpCoolDownDateTime : String,
    val allCoolDownDateTime: String,
    val isInAllCooldownPeriod: Boolean,
    val intensity: Int,
    val prompt_allowed: Boolean,
    val fgFilesUsed: Int,
    val mainFilesUsed: Int,
    val debugLog: String
)
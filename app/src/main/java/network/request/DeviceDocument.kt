package network.request

data class DeviceDocument (
    val timestamp: String,
    val readingTimestamp: String,
    val eventType: String,
    val lastAwakeTimestamp: String,
    val coolDownDateTime: String,
    val isInCooldownPeriod: Boolean,
    val startPromptAllowPeriod: String,
    val isInPromptWindow: Boolean,
    val promptCount: Int,
    val intensity: Int,
    val prompt_allowed: Boolean,
    val debugLog: String
)
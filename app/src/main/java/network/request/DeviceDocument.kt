package network.request

class DeviceDocument (
    val timestamp: String,
    val readingTimestamp: String,
    val eventType: String,
    val intensity: Int,
    val asleepEventCount: Int,
    val minutesSinceLastCount: Long,
    val lastTimestampDeepAsleep: String,
    val prompt_allowed: Boolean
)
package network.request

class DeviceDocument (
    val timestamp: String,
    val readingTimestamp: String,
    val vibrationType: String,
    val duration: Int,
    val reps: Int,
    val delayAfterRep: Int,
    val asleepCnt: Int
)
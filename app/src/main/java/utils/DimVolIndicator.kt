package utils

import java.time.LocalDateTime

data class DimVolUpdateStatus(
    var updateMinuteLimit: Long,
    var lastUpdateTime: LocalDateTime,
    var lastUpdateVol: Float
)

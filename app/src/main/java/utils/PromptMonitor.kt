package utils

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class PromptMonitor {

    var stopPromptWindow: LocalDateTime? = null
    var promptEventWaiting: String? = null
    var awakeEventList: MutableList<LocalDateTime> =
        emptyList<LocalDateTime>().toMutableList()
    var lightEventList: MutableList<LocalDateTime> =
        emptyList<LocalDateTime>().toMutableList()
    var remEventList: MutableList<LocalDateTime> = emptyList<LocalDateTime>().toMutableList()
    var allPromptEvents: MutableList<LocalDateTime> =
        emptyList<LocalDateTime>().toMutableList()
    var asleepEventCountSinceAwake = 0
    var deepAsleepEventCountSinceActive = 0
    var lastTimestampSinceDeepAsleep: LocalDateTime? = null

    companion object {
        const val MAX_REM_PROMPT_COUNT_PER_HOUR = 3
        const val MAX_LIGHT_PROMPT_COUNT = 3
        const val MAX_TOTAL_PROMPT_COUNT = 9
    }

    private val formatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")

    fun clear() {
        awakeEventList.clear()
        lightEventList.clear()
        remEventList.clear()
        allPromptEvents.clear()
        asleepEventCountSinceAwake = 0
        deepAsleepEventCountSinceActive = 0
        stopPromptWindow = null
        promptEventWaiting = null
        lastTimestampSinceDeepAsleep = null
    }

    fun getEventsDisplay(): String {
        var eventsDisplay = ""

        if (awakeEventList.isNotEmpty()) {
            val formatAwakeEvents = awakeEventList.map { dateTime -> dateTime.format(formatter) }
            eventsDisplay += "ActiveEvents: $formatAwakeEvents \n"
        }

        if (lightEventList.isNotEmpty()) {
            val formatLightEvents: List<String> =
                lightEventList.map { dateTime -> dateTime.format(formatter) }
            eventsDisplay += "Light Events: $formatLightEvents \n"
        }

        if (remEventList.isNotEmpty()) {
            val formatRemEvents: List<String> =
                remEventList.map { dateTime -> dateTime.format(formatter) }
            eventsDisplay += "REM Events: $formatRemEvents \n"
        }

        return eventsDisplay
    }

    fun handleDeepAsleepEvent(lastTimestamp: String?) {
        asleepEventCountSinceAwake++
        deepAsleepEventCountSinceActive++
        if (deepAsleepEventCountSinceActive > 40) {
            lastTimestampSinceDeepAsleep = LocalDateTime.parse(lastTimestamp)

        }
    }

    fun handleAwakeEvent() {
        asleepEventCountSinceAwake = 0
        deepAsleepEventCountSinceActive = 0
    }

    fun handleRestlessEvent() {
        asleepEventCountSinceAwake++
        deepAsleepEventCountSinceActive = 0
    }

    fun handleAsleepEvent() {
        asleepEventCountSinceAwake++
        deepAsleepEventCountSinceActive = 0
    }

    fun handleLightAsleepEvent() {
        asleepEventCountSinceAwake++
        deepAsleepEventCountSinceActive = 0
    }

    fun handleRemAsleepEvent() {
        asleepEventCountSinceAwake++
        deepAsleepEventCountSinceActive = 0
    }

    fun isStopPromptWindow(lastTimestamp: String?): Boolean {
        return stopPromptWindow != null && stopPromptWindow!! > LocalDateTime.parse(lastTimestamp)
                && (deepAsleepEventCountSinceActive > 20 || asleepEventCountSinceAwake > 40)
    }

    fun isAwakeEventAllowed(lastTimestamp: String?): Boolean {
        return (awakeEventList.isEmpty() || LocalDateTime.parse(lastTimestamp) >= awakeEventList.last()
                    .plusMinutes(60)) &&
                (lastTimestampSinceDeepAsleep == null || LocalDateTime.parse(lastTimestamp) >=
                        lastTimestampSinceDeepAsleep!!.plusMinutes(2))
    }

    fun isRemEventAllowed(lastTimestamp: String?): Boolean {
        val totalPromptCount = allPromptEvents.size
        val lastHourCnt = remEventList.filter{ it > LocalDateTime.parse(lastTimestamp).minusMinutes(60) }.size
        val promptCntNotExceeded = lastHourCnt < MAX_REM_PROMPT_COUNT_PER_HOUR
                && totalPromptCount < MAX_TOTAL_PROMPT_COUNT

        return promptEventWaiting == null && asleepEventCountSinceAwake >= 15 && promptCntNotExceeded &&
                (allPromptEvents.isEmpty() || LocalDateTime.parse(lastTimestamp) >= allPromptEvents.last().plusMinutes(5))
    }

    fun isLightEventAllowed(lastTimestamp: String?): Boolean {
        val totalCnt = lightEventList.size
        val totalPromptCount = allPromptEvents.size
        val lastHourAllCnt = allPromptEvents.filter{ it > LocalDateTime.parse(lastTimestamp).minusMinutes(60) }.size

        //rem prompts are preferred, but we'll allow a light prompt if there haven't been any for at least an hour
        val promptCntNotExceeded = lastHourAllCnt == 0 && totalCnt < MAX_LIGHT_PROMPT_COUNT
                && totalPromptCount < MAX_TOTAL_PROMPT_COUNT

        return promptEventWaiting == null && asleepEventCountSinceAwake >= 25 && promptCntNotExceeded &&
                (allPromptEvents.isEmpty() || LocalDateTime.parse(lastTimestamp) >= allPromptEvents.last().plusMinutes(5))
    }

    fun promptIntensityLevel(lastTimestamp: String?, minTimeBetweenPrompts: Long, maxTimeBetweenPrompts: Long): Int {

        return if(allPromptEvents.isEmpty()) {
            2
        } else if (LocalDateTime.parse(lastTimestamp) <= allPromptEvents.last().plusMinutes(minTimeBetweenPrompts)) {
            0
        } else if (LocalDateTime.parse(lastTimestamp) <= allPromptEvents.last().plusMinutes(maxTimeBetweenPrompts)) {
            1
        } else {
            2
        }
    }
}
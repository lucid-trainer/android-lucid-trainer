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
        const val MAX_PROMPT_COUNT_PER_PERIOD = 3
        const val MAX_LIGHT_PROMPT_COUNT_PER_PERIOD = 1
        const val MAX_PROMPT_COUNT = 12
        const val MIN_TIME_BETWEEN_PROMPTS = 15L
        const val MAX_TIME_BETWEEN_PROMPTS = 40L
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
        val lastPeriodCount = allPromptEvents.filter{ it > LocalDateTime.parse(lastTimestamp).minusMinutes(70) }.size

        val promptCntNotExceeded = lastPeriodCount < MAX_PROMPT_COUNT_PER_PERIOD
                && totalPromptCount <= MAX_PROMPT_COUNT

        return promptEventWaiting == null && asleepEventCountSinceAwake >= 15 && promptCntNotExceeded &&
                (allPromptEvents.isEmpty() || LocalDateTime.parse(lastTimestamp) >= allPromptEvents.last().plusMinutes(5))
    }

    fun isLightEventAllowed(lastTimestamp: String?): Boolean {
        val totalCount = allPromptEvents.size
        val lastPeriodCount = lightEventList.filter{ it > LocalDateTime.parse(lastTimestamp).minusMinutes(100) }.size

        val promptCntNotExceeded = lastPeriodCount < MAX_LIGHT_PROMPT_COUNT_PER_PERIOD
                && totalCount <= MAX_PROMPT_COUNT

        return promptEventWaiting == null && asleepEventCountSinceAwake >= 25 && promptCntNotExceeded &&
                (allPromptEvents.isEmpty() || LocalDateTime.parse(lastTimestamp) >= allPromptEvents.last().plusMinutes(5))
    }

    fun promptIntensityLevel(lastTimestamp: String?): Int {

        val hour = LocalDateTime.parse(lastTimestamp).hour

        var intensity = if (allPromptEvents.isEmpty()) {
            3
        } else if (LocalDateTime.parse(lastTimestamp) <= allPromptEvents.last()
                .plusMinutes(MIN_TIME_BETWEEN_PROMPTS)
        ) {
            1
        } else if (LocalDateTime.parse(lastTimestamp) <= allPromptEvents.last()
                .plusMinutes(MAX_TIME_BETWEEN_PROMPTS)
        ) {
            2
        } else {
            3
        }

        //adjust up or down a bit depending on hour
        if(hour < 3) {
            intensity++
        } else if(hour >= 5) {
            intensity--
        }

        return intensity

    }
}
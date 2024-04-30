package utils

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class PromptMonitor() {

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

    var playCount = 1

    companion object {
        const val PLAY_COUNT_MAX = 4
    }

    private val formatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")

    fun clear() {
        lightEventList.clear()
        remEventList.clear()
        asleepEventCountSinceAwake = 0
        stopPromptWindow = null
        promptEventWaiting = null
        playCount = 1
    }

    fun getEventsDisplay(): String {
        var eventsDisplay = "";

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

        return eventsDisplay;
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

    fun handleRestlessEvent(lastTimestamp: String?) {
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
                && (deepAsleepEventCountSinceActive > 16 || asleepEventCountSinceAwake > 40)
    }

    fun isAwakeEventAllowed(lastTimestamp: String?): Boolean {
        return (awakeEventList.isEmpty() || LocalDateTime.parse(lastTimestamp) >= awakeEventList.last()
                    .plusMinutes(60)) &&
                (lastTimestampSinceDeepAsleep == null || LocalDateTime.parse(lastTimestamp) >=
                        lastTimestampSinceDeepAsleep!!.plusMinutes(2))
    }

    fun isLightEventAllowed(lastTimestamp: String?, timeBetweenPrompts: Long): Boolean {
        return promptEventWaiting == null && asleepEventCountSinceAwake >= 50 &&
                (allPromptEvents.isEmpty() || LocalDateTime.parse(lastTimestamp) >= allPromptEvents.last()
                    .plusMinutes(timeBetweenPrompts))
    }

    fun isRemEventAllowed(lastTimestamp: String?, timeBetweenPrompts: Long): Boolean {
        return promptEventWaiting == null && asleepEventCountSinceAwake >= 20 &&
                (remEventList.isEmpty() || LocalDateTime.parse(lastTimestamp)  >= remEventList.last().plusMinutes(timeBetweenPrompts))
    }

    fun isTogglePromptWindow(lastTimestamp: String?): Boolean {
        return stopPromptWindow != null && stopPromptWindow!! > LocalDateTime.parse(lastTimestamp)
    }

    fun getNextPlayCount() : Int{
        if (playCount == PLAY_COUNT_MAX) {
            playCount = 1
        } else {
            playCount++
        }
        return playCount
    }

}
package utils

import android.util.Log
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

    var lastAwakeDateTime : LocalDateTime? = null
    private var cooldownDateTime : LocalDateTime? = null

    companion object {
        const val MAX_PROMPT_COUNT_PER_PERIOD = 5
        const val PROMPT_PERIOD = 25L
        const val LIGHT_PROMPT_PERIOD = 70L
        const val COOLDOWN_PERIOD = 35L
        const val MIN_TIME_BETWEEN_PROMPTS = 10L
        const val MAX_TIME_BETWEEN_PROMPTS = 25L
    }

    private val formatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")

    fun clear() {
        awakeEventList.clear()
        lightEventList.clear()
        remEventList.clear()
        allPromptEvents.clear()
        stopPromptWindow = null
        promptEventWaiting = null
        lastAwakeDateTime = null
        cooldownDateTime = null
    }

    fun getEventsDisplay(): String {
        var eventsDisplay = ""

        if (awakeEventList.isNotEmpty()) {
            val formatAwakeEvents = awakeEventList.toMutableList().map { dateTime -> dateTime.format(formatter) }
            eventsDisplay += "ActiveEvents: $formatAwakeEvents \n"
        }

        if (lightEventList.isNotEmpty()) {
            val formatLightEvents: List<String> =
                lightEventList.toMutableList().map { dateTime -> dateTime.format(formatter) }

            eventsDisplay += "Light Events: $formatLightEvents \n"
        }

        if (remEventList.isNotEmpty()) {
            val formatRemEvents: List<String> =
                remEventList.toMutableList().map { dateTime -> dateTime.format(formatter) }
            eventsDisplay += "REM Events: $formatRemEvents \n"
        }

        return eventsDisplay
    }

    fun addLightEvent(lastDateTime : LocalDateTime) {
        lightEventList.add(lastDateTime)
        allPromptEvents.add(lastDateTime)

        checkCooldown(lastDateTime)
    }

    fun addRemEvent(lastDateTime : LocalDateTime) {
        remEventList.add(lastDateTime)
        allPromptEvents.add(lastDateTime)

        checkCooldown(lastDateTime)
    }

    private fun checkCooldown(lastDateTime: LocalDateTime) {
        //events tend to cluster which we want.  When we get to the max in a period wait for the cooldown period to end before
        //allowing any more events
        if (allPromptEvents.size >= MAX_PROMPT_COUNT_PER_PERIOD
            && lastDateTime <= allPromptEvents.takeLast(5).first().plusMinutes(PROMPT_PERIOD)
        ) {
            cooldownDateTime = lastDateTime
        }
    }

    private fun isInCoolDownPeriod(lastTimestamp: String?) : Boolean {
        return cooldownDateTime != null &&
                LocalDateTime.parse(lastTimestamp) < cooldownDateTime!!.plusMinutes(COOLDOWN_PERIOD)
    }

    fun isStopPromptWindow(lastTimestamp: String?): Boolean {
        return stopPromptWindow != null && stopPromptWindow!! > LocalDateTime.parse(lastTimestamp) &&
                (LocalDateTime.parse(lastTimestamp) >= lastAwakeDateTime!!.plusMinutes(20))
    }

    fun isAwakeEventAllowed(lastTimestamp: String?): Boolean {
        return (awakeEventList.isEmpty() || LocalDateTime.parse(lastTimestamp) >= awakeEventList.last()
                    .plusMinutes(60))
    }

    fun isRemEventAllowed(lastTimestamp: String?): Boolean {
        return promptEventWaiting == null && LocalDateTime.parse(lastTimestamp) >= lastAwakeDateTime!!.plusMinutes(6) &&
                !isInCoolDownPeriod(lastTimestamp) &&
                (allPromptEvents.isEmpty() || LocalDateTime.parse(lastTimestamp) >= allPromptEvents.last().plusMinutes(3))
    }

    fun isLightEventAllowed(lastTimestamp: String?): Boolean {
        Log.d("PromptMonitor",
            "$lastTimestamp test 1 = ${allPromptEvents.isNotEmpty() && LocalDateTime.parse(lastTimestamp) <= allPromptEvents.last().plusMinutes(PROMPT_PERIOD)}" +
                    " test 2 = ${allPromptEvents.isEmpty() || LocalDateTime.parse(lastTimestamp) >= allPromptEvents.last().plusMinutes(LIGHT_PROMPT_PERIOD)}")

        if(allPromptEvents.isNotEmpty()) {
            Log.d("PromptMonitor", "last = ${allPromptEvents.last().toString()} timestamp = ${LocalDateTime.parse(lastTimestamp)}" +
                    " greater than ${allPromptEvents.last().plusMinutes(LIGHT_PROMPT_PERIOD)}")
        }

        return promptEventWaiting == null && LocalDateTime.parse(lastTimestamp) >= lastAwakeDateTime!!.plusMinutes(6) &&
                //allow a light event if in a window of earlier prompts or if there hasn't been one for awhile
                ((allPromptEvents.isNotEmpty() && LocalDateTime.parse(lastTimestamp) <= allPromptEvents.last().plusMinutes(PROMPT_PERIOD)) ||
                        allPromptEvents.isEmpty() || LocalDateTime.parse(lastTimestamp) >= allPromptEvents.last().plusMinutes(LIGHT_PROMPT_PERIOD)) &&
                !isInCoolDownPeriod(lastTimestamp) &&
                (allPromptEvents.isEmpty() || LocalDateTime.parse(lastTimestamp) >= allPromptEvents.last().plusMinutes(3))
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
        if(hour < 5) {
            intensity++
        } else {
            intensity--
        }

        return intensity

    }
}
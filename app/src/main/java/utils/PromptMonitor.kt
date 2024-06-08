package utils

import android.util.Log
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class PromptMonitor {

    var stopPromptWindow: LocalDateTime? = null
    var promptEventWaiting: String? = null
    private var awakeEventList: MutableList<LocalDateTime> =
        emptyList<LocalDateTime>().toMutableList()
    private var lightEventList: MutableList<LocalDateTime> =
        emptyList<LocalDateTime>().toMutableList()
    private var remEventList: MutableList<LocalDateTime> = emptyList<LocalDateTime>().toMutableList()
    private var followUpEventList: MutableList<LocalDateTime> = emptyList<LocalDateTime>().toMutableList()
    var allPromptEvents: MutableList<LocalDateTime> = emptyList<LocalDateTime>().toMutableList()

    var lastAwakeDateTime : LocalDateTime? = null
    var lastFollowupDateTime : LocalDateTime? = null
    private var coolDownDateTime : LocalDateTime? = null

    companion object {
        const val MAX_PROMPT_COUNT_PER_PERIOD = 4
        const val PROMPT_PERIOD = 20L
        const val COOL_DOWN_PERIOD = 25L
        const val MIN_TIME_BETWEEN_PROMPTS = 10L
        const val MAX_TIME_BETWEEN_PROMPTS = 25L
    }

    private val formatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")

    fun clear() {
        awakeEventList.clear()
        lightEventList.clear()
        remEventList.clear()
        followUpEventList.clear()
        allPromptEvents.clear()
        stopPromptWindow = null
        promptEventWaiting = null
        lastAwakeDateTime = null
        lastFollowupDateTime = null
        coolDownDateTime = null
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

        if (followUpEventList.isNotEmpty()) {
            val formatfollowUpEvents: List<String> =
                followUpEventList.toMutableList().map { dateTime -> dateTime.format(formatter) }
            eventsDisplay += "Follow-up Events: $formatfollowUpEvents \n"
        }

        return eventsDisplay
    }

    fun addAwakeEvent(lastDateTime: LocalDateTime) {
        awakeEventList.add(lastDateTime)
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

    fun addFollowUpEvent(lastDateTime : LocalDateTime) {
        followUpEventList.add(lastDateTime)
        allPromptEvents.add(lastDateTime)

        checkCooldown(lastDateTime)
    }

    private fun checkCooldown(lastDateTime: LocalDateTime) {
        //events tend to cluster which we want.  When we get to the max in a period wait for the cooldown period to end before
        //allowing any more events
        if (allPromptEvents.size >= MAX_PROMPT_COUNT_PER_PERIOD
            && lastDateTime <= allPromptEvents.takeLast(5).first().plusMinutes(PROMPT_PERIOD)
        ) {
            coolDownDateTime = lastDateTime
        }
    }

    private fun isInCoolDownPeriod(lastTimestamp: String?) : Boolean {
        return coolDownDateTime != null &&
                LocalDateTime.parse(lastTimestamp) < coolDownDateTime!!.plusMinutes(COOL_DOWN_PERIOD)
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
        //we're most interested in the rem events so the constraints are more limited
        return promptEventWaiting == null && !isInAwakePeriod(lastTimestamp) &&
                (allPromptEvents.isEmpty() || LocalDateTime.parse(lastTimestamp) >= allPromptEvents.last().plusMinutes(3))
    }

    fun isLightEventAllowed(lastTimestamp: String?): Boolean {
        return promptEventWaiting == null && !isInAwakePeriod(lastTimestamp) && !isInCoolDownPeriod(lastTimestamp) &&
        //allow a light event if in a window of earlier prompts
                ((allPromptEvents.isNotEmpty() &&
                        LocalDateTime.parse(lastTimestamp) <= allPromptEvents.last().plusMinutes(PROMPT_PERIOD)) &&
                        (allPromptEvents.isEmpty() || LocalDateTime.parse(lastTimestamp) >= allPromptEvents.last().plusMinutes(3)))
    }

    fun isFollowUpEventAllowed(lastTimestamp: String?): Boolean {
        //we want several events in a row to nudge the sleeper - but anchored to a rem or light trigger event within the last few minutes
        //This allows for padding with one or more follow-up events in a cycle of prompts
        val remAndLightEvents = (remEventList + lightEventList).sorted()

        val isAllowed = remAndLightEvents.isNotEmpty() && promptEventWaiting == null &&
                !isInCoolDownPeriod(lastTimestamp) && !isInAwakePeriod(lastTimestamp) &&
                (lastFollowupDateTime == null || remAndLightEvents.last()  > lastFollowupDateTime) &&
                LocalDateTime.parse(lastTimestamp) > remAndLightEvents.last().plusMinutes(3) &&
                LocalDateTime.parse(lastTimestamp) <= remAndLightEvents.last().plusMinutes(5)

        return isAllowed
    }

    private fun isInAwakePeriod(lastTimestamp: String?) : Boolean {
        return lastAwakeDateTime != null &&
                LocalDateTime.parse(lastTimestamp) <= lastAwakeDateTime!!.plusMinutes(6)
    }

    fun promptIntensityLevel(lastTimestamp: String?): Int {

        val hour = LocalDateTime.parse(lastTimestamp).hour

        var intensity = if (allPromptEvents.isEmpty()) {
            4
        } else if (LocalDateTime.parse(lastTimestamp) <= allPromptEvents.last()
                .plusMinutes(MIN_TIME_BETWEEN_PROMPTS)
        ) {
            2
        } else if (LocalDateTime.parse(lastTimestamp) <= allPromptEvents.last()
                .plusMinutes(MAX_TIME_BETWEEN_PROMPTS)
        ) {
            3
        } else {
            4
        }

        //adjust up or down a bit depending on hour
        if(hour >= 6) {
            intensity--
        }

        return intensity

    }

}
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
    var lastHighActiveTimestamp: LocalDateTime? = null
    var followUpCoolDownDateTime : LocalDateTime? = null

    companion object {
        const val MAX_PROMPT_COUNT_PER_PERIOD = 5
        const val PROMPT_PERIOD = 20L
        const val FOLLOW_UP_COOL_DOWN_PERIOD = 25L
        const val ALL_COOL_DOWN_PERIOD = 10L
        const val MINUTES_BETWEEN_PROMPTS = 2L
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
        followUpCoolDownDateTime = null
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

        checkFollowUpCooldown(lastDateTime)
    }

    fun addRemEvent(lastDateTime : LocalDateTime) {
        remEventList.add(lastDateTime)
        allPromptEvents.add(lastDateTime)

        checkFollowUpCooldown(lastDateTime)
    }

    fun addFollowUpEvent(lastDateTime : LocalDateTime) {
        followUpEventList.add(lastDateTime)
        allPromptEvents.add(lastDateTime)

        checkFollowUpCooldown(lastDateTime)
    }

    private fun checkFollowUpCooldown(lastDateTime: LocalDateTime) {
        //events tend to cluster which we want.  When we get to the max in a period wait for the cooldown period to end before
        //allowing any more events
        if (allPromptEvents.size >= MAX_PROMPT_COUNT_PER_PERIOD
            && lastDateTime <= allPromptEvents.takeLast(MAX_PROMPT_COUNT_PER_PERIOD).first().plusMinutes(PROMPT_PERIOD)
        ) {
            followUpCoolDownDateTime = lastDateTime
        }
    }

    private fun isInCoolDownPeriod(lastTimestamp: String?) : Boolean {
        return isInAllCoolDownPeriod(lastTimestamp) || 
                (followUpCoolDownDateTime != null &&
                 LocalDateTime.parse(lastTimestamp) < followUpCoolDownDateTime!!.plusMinutes(FOLLOW_UP_COOL_DOWN_PERIOD))
                
    }

    private fun isInAllCoolDownPeriod(lastTimestamp: String?) : Boolean {
        return lastHighActiveTimestamp != null && allPromptEvents.isNotEmpty() &&
            lastHighActiveTimestamp!! > allPromptEvents.last() &&
            LocalDateTime.parse(lastTimestamp) < allPromptEvents.last().plusMinutes(ALL_COOL_DOWN_PERIOD)
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
        val lastDateTime = LocalDateTime.parse(lastTimestamp)

        return promptEventWaiting == null && !isInAwakePeriod(lastTimestamp) && !isInAllCoolDownPeriod(lastTimestamp) &&
                (allPromptEvents.isEmpty() || lastDateTime >= allPromptEvents.last().plusMinutes(MINUTES_BETWEEN_PROMPTS))
    }

    fun isLightEventAllowed(lastTimestamp: String?): Boolean {
        return promptEventWaiting == null && !isInAwakePeriod(lastTimestamp) && !isInCoolDownPeriod(lastTimestamp) &&
        //allow a light event if in a window of earlier prompts
                ((allPromptEvents.isNotEmpty() &&
                        LocalDateTime.parse(lastTimestamp) <= allPromptEvents.last().plusMinutes(PROMPT_PERIOD)) &&
                        (allPromptEvents.isEmpty() || LocalDateTime.parse(lastTimestamp) >= allPromptEvents.last().plusMinutes(MINUTES_BETWEEN_PROMPTS)))
    }

    fun isFollowUpEventAllowed(lastTimestamp: String?): Boolean {
        //we want several events in a row to nudge the sleeper - but anchored to a rem or light trigger event within the last few minutes
        //This allows for padding with one or more follow-up events in a cycle of prompts
        val remAndLightEvents = (remEventList + lightEventList).sorted()

        val isAllowed = remAndLightEvents.isNotEmpty() && promptEventWaiting == null &&
                !isInCoolDownPeriod(lastTimestamp) && !isInAwakePeriod(lastTimestamp) &&
                (lastFollowupDateTime == null || remAndLightEvents.last()  > lastFollowupDateTime) &&
                LocalDateTime.parse(lastTimestamp) > remAndLightEvents.last().plusMinutes(MINUTES_BETWEEN_PROMPTS) &&
                LocalDateTime.parse(lastTimestamp) <= remAndLightEvents.last().plusMinutes(MINUTES_BETWEEN_PROMPTS*2)

        return isAllowed
    }

    private fun isInAwakePeriod(lastTimestamp: String?) : Boolean {
        return lastAwakeDateTime != null &&
                LocalDateTime.parse(lastTimestamp) <= lastAwakeDateTime!!.plusMinutes(6)
    }

    fun promptIntensityLevel(lastTimestamp: String?): Int {

        val hour = LocalDateTime.parse(lastTimestamp).hour

        var intensity = 3
        if(isInCoolDownPeriod(lastTimestamp)) {
            //this should be the last one before a cool down period starts so up the intensity
            intensity = 4
        }

        //adjust down a bit if late in the morning
        if(hour >= 6) {
            intensity--
        }

        return intensity

    }

}
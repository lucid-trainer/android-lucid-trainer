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
    var coolDownEndDateTime : LocalDateTime? = null


    companion object {
        const val MAX_PROMPT_COUNT_PER_PERIOD = 6
        const val PROMPT_PERIOD = 20L
        const val MAX_PROMPT_COOL_DOWN_PERIOD = 15L
        const val INTERRUPT_COOL_DOWN_PERIOD = 15L
        const val AWAKE_COOL_DOWN_PERIOD = 35L
        const val SECONDS_BETWEEN_PROMPTS = 120L
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
        coolDownEndDateTime = null
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
            val formatFollowUpEvents: List<String> =
                followUpEventList.toMutableList().map { dateTime -> dateTime.format(formatter) }
            eventsDisplay += "Follow-up Events: $formatFollowUpEvents \n"
        }

        return eventsDisplay
    }

    fun addAwakeEvent(lastDateTime: LocalDateTime) {
        awakeEventList.add(lastDateTime)
        //don't allow prompts for a longer period of time after an active event
        coolDownEndDateTime = lastDateTime.plusMinutes(AWAKE_COOL_DOWN_PERIOD)
        //Log.d("PromptMonitor", "$lastDateTime setting $followUpCoolDownEndDateTime follow-up time for awake event")
    }

    fun addLightEvent(lastDateTime : LocalDateTime) {
        lightEventList.add(lastDateTime)
        allPromptEvents.add(lastDateTime)

        checkMaxPromptCoolDown(lastDateTime)
    }

    fun addRemEvent(lastDateTime : LocalDateTime) {
        remEventList.add(lastDateTime)
        allPromptEvents.add(lastDateTime)

        checkMaxPromptCoolDown(lastDateTime)
    }

    fun addFollowUpEvent(lastDateTime : LocalDateTime) {
        followUpEventList.add(lastDateTime)
        allPromptEvents.add(lastDateTime)

        checkMaxPromptCoolDown(lastDateTime)
    }

    private fun checkMaxPromptCoolDown(lastDateTime: LocalDateTime) {
        //events tend to cluster which we want.  When we get to the max in a period wait for the cooldown period to end before
        //allowing any more events.  We also set this when a manual sound routine such as WildRoutine or PodcastRoutine is initiated
        //as this indicates the user is awake and going back to sleep
        if (allPromptEvents.size >= MAX_PROMPT_COUNT_PER_PERIOD
            && lastDateTime <= allPromptEvents.takeLast(MAX_PROMPT_COUNT_PER_PERIOD).first().plusMinutes(PROMPT_PERIOD)
        ) {
            coolDownEndDateTime = lastDateTime.plusMinutes(MAX_PROMPT_COOL_DOWN_PERIOD)
        }
    }

    fun checkInterruptCoolDown(lastTimestamp: String?, isSleepButton: Boolean) : Boolean {
        //Check if we've had an interrupt from the watch device via high movement or the Sleep button and disable prompts for a time if so
        var updatedAllCoolDown = false
        val lastDateTime = LocalDateTime.parse(lastTimestamp)

//        Log.d("PromptMonitor", "$lastTimestamp checking to set all cool down, currently $allCoolDownEndDateTime")
//        if(allPromptEvents.isNotEmpty()) {
//            Log.d("PromptMonitor", "$lastTimestamp lastPrompt = ${allPromptEvents.last()} plus4 = ${allPromptEvents.last().plusMinutes(4)}")
//        }
        var allCoolDownPeriod = INTERRUPT_COOL_DOWN_PERIOD

        if(isSleepButton) {
            //just make it a full hour if we use the sleep button
            allCoolDownPeriod = 60
        }

        if(allPromptEvents.isNotEmpty() && ( coolDownEndDateTime == null ||
            (lastDateTime > coolDownEndDateTime && lastDateTime > allPromptEvents.last() &&
             lastDateTime < allPromptEvents.last().plusMinutes(4)))) {
            coolDownEndDateTime = lastDateTime.plusMinutes(allCoolDownPeriod)
            updatedAllCoolDown = true

            //Log.d("PromptMonitor", "$lastTimestamp setting all cool down $allCoolDownEndDateTime")
        }

        return updatedAllCoolDown
    }

    fun isInCoolDownPeriod(lastTimestamp: String?) : Boolean {
       return coolDownEndDateTime != null && LocalDateTime.parse(lastTimestamp) <= coolDownEndDateTime
    }

    fun isStopPromptWindow(lastTimestamp: String?): Boolean {
        return stopPromptWindow != null && stopPromptWindow!! > LocalDateTime.parse(lastTimestamp) &&
                (LocalDateTime.parse(lastTimestamp) >= lastAwakeDateTime!!.plusMinutes(20))
    }

    fun isAwakeEventBeforePeriod(lastTimestamp: String?, period: Long): Boolean {
        return (awakeEventList.isEmpty() || LocalDateTime.parse(lastTimestamp) >= awakeEventList.last()
            .plusMinutes(period))
    }

    fun isAwakeEventAllowed(lastTimestamp: String?): Boolean {
        return (awakeEventList.isEmpty() || LocalDateTime.parse(lastTimestamp) >= awakeEventList.last()
                    .plusMinutes(60))
    }

    fun isRemEventAllowed(lastTimestamp: String?): Boolean {
        val lastDateTime = LocalDateTime.parse(lastTimestamp)

        Log.d("PromptMonitor", "$lastTimestamp isInAllCoolDown = ${isInCoolDownPeriod(lastTimestamp)}")

        return promptEventWaiting == null && !isInAwakePeriod(lastTimestamp) && !isInCoolDownPeriod(lastTimestamp) &&
                (allPromptEvents.isEmpty() || lastDateTime >= allPromptEvents.last().plusSeconds(SECONDS_BETWEEN_PROMPTS))
    }

    fun isLightEventAllowed(lastTimestamp: String?): Boolean {
        return promptEventWaiting == null && !isInAwakePeriod(lastTimestamp) && !isInCoolDownPeriod(lastTimestamp) &&
                //allow a light event if in a window of earlier prompts
                ((allPromptEvents.isNotEmpty() &&
                        LocalDateTime.parse(lastTimestamp) <= allPromptEvents.last().plusMinutes(PROMPT_PERIOD)) &&
                        (allPromptEvents.isEmpty() || LocalDateTime.parse(lastTimestamp) >= allPromptEvents.last().plusSeconds(SECONDS_BETWEEN_PROMPTS)))
    }

    fun isFollowUpEventAllowed(lastTimestamp: String?): Boolean {
        //we want several events in a row to nudge the sleeper - but anchored to a rem or light trigger event within the last few minutes
        //This allows for padding with one or more follow-up events in a cycle of prompts
        val remAndLightEvents = (remEventList + lightEventList).sorted()

        val isAllowed = remAndLightEvents.isNotEmpty() && promptEventWaiting == null &&
                !isInCoolDownPeriod(lastTimestamp) && !isInAwakePeriod(lastTimestamp) &&
                (lastFollowupDateTime == null || remAndLightEvents.last()  > lastFollowupDateTime) &&
                LocalDateTime.parse(lastTimestamp) > remAndLightEvents.last().plusSeconds(SECONDS_BETWEEN_PROMPTS) &&
                LocalDateTime.parse(lastTimestamp) <= remAndLightEvents.last().plusSeconds(SECONDS_BETWEEN_PROMPTS*2)

        return isAllowed
    }

    private fun isInAwakePeriod(lastTimestamp: String?) : Boolean {
        return lastAwakeDateTime != null &&
                LocalDateTime.parse(lastTimestamp) <= lastAwakeDateTime!!.plusMinutes(6)
    }

    fun promptIntensityLevel(lastTimestamp: String?): Int {

        val hour = LocalDateTime.parse(lastTimestamp).hour

        var intensity = 2

        //adjust down a bit if late in the morning
        if(hour >= 6) {
            intensity--
        }

        return intensity

    }

}
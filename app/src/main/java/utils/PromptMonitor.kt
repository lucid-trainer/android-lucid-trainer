package utils

import android.util.Log
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class PromptMonitor {
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
    var lastSleepButtonDateTime: LocalDateTime? = null
    var lastHighActivityEventDateTime: LocalDateTime? = null
    var coolDownEndDateTime : LocalDateTime? = null
    var lastFirstPromptDateTime : LocalDateTime? = null

    private var remEventTriggerList: MutableList<LocalDateTime> =
        emptyList<LocalDateTime>().toMutableList()
    var startPromptAllowPeriod: LocalDateTime? = null

    companion object {
        const val NEW_PROMPT_PERIOD_WAIT_SECONDS = 150L
        const val PROMPT_PERIOD = 20L
        const val MAX_PROMPT_COOL_DOWN_PERIOD = 12L
        const val INTERRUPT_COOL_DOWN_PERIOD = 12L
        const val HIGH_ACTIVITY_COOL_DOWN_PERIOD = 10L
        const val SLEEP_COOL_DOWN_PERIOD = 40L
        const val IN_AWAKE_PERIOD = 6L
        const val BETWEEN_AWAKE_PERIOD = 70L
        const val SECONDS_BETWEEN_PROMPTS = 150L
    }

    private val formatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")

    fun clear() {
        awakeEventList.clear()
        lightEventList.clear()
        remEventList.clear()
        followUpEventList.clear()
        allPromptEvents.clear()
        remEventTriggerList.clear()
        promptEventWaiting = null
        lastAwakeDateTime = null
        lastFollowupDateTime = null
        lastHighActivityEventDateTime = null
        coolDownEndDateTime = null
        startPromptAllowPeriod = null
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

    fun checkRemTriggerEvent(lastTimestamp: String?) {
        //if we have a Rem trigger event we'll check if we should start a new prompting window
        val lastDateTime = LocalDateTime.parse(lastTimestamp)

        if( startPromptAllowPeriod == null || lastDateTime > startPromptAllowPeriod!!.plusSeconds(NEW_PROMPT_PERIOD_WAIT_SECONDS)) {
            startPromptAllowPeriod = lastDateTime.plusSeconds(NEW_PROMPT_PERIOD_WAIT_SECONDS)
        }

        //Log.d("PromptMonitor", "$lastTimestamp startPromptAllowPeriod = $startPromptAllowPeriod")
        remEventTriggerList.add(lastDateTime)
    }

    private fun checkMaxPromptCoolDown(lastDateTime: LocalDateTime) {
        //events tend to cluster which we want.  When we get to the max in a period wait for the cooldown period to end before
        //allowing any more events.  We also set this when a manual sound routine such as WildRoutine or PodcastRoutine is initiated
        //as this indicates the user is awake and going back to sleep
        val maxPromptCount = getMaxPromptCountPerPeriod(lastDateTime)
        if (allPromptEvents.size >= maxPromptCount
            && lastDateTime <= allPromptEvents.takeLast(maxPromptCount).first().plusMinutes(PROMPT_PERIOD)
        ) {
            coolDownEndDateTime = lastDateTime.plusMinutes(MAX_PROMPT_COOL_DOWN_PERIOD)
            Log.d("PromptMonitor", "maxPromptCount = $maxPromptCount setting coolDownEndDateTime=$coolDownEndDateTime")
        }
    }

    fun checkInterruptCoolDown(lastTimestamp: String?, isSleepButton: Boolean) : Boolean {
        //Check if we've had an interrupt from the watch device via high movement or the Sleep button and disable prompts for a time if so
        var updatedAllCoolDown = false
        val lastDateTime = LocalDateTime.parse(lastTimestamp)
        lastHighActivityEventDateTime = lastDateTime

        if(isSleepButton) {
            //just set we use the sleep button
            coolDownEndDateTime = lastDateTime.plusMinutes( SLEEP_COOL_DOWN_PERIOD)
            updatedAllCoolDown = true
        } else {
            if(allPromptEvents.isNotEmpty() && ( coolDownEndDateTime == null ||
                (lastDateTime > coolDownEndDateTime && lastDateTime > allPromptEvents.last() &&
                 lastDateTime < allPromptEvents.last().plusMinutes(4)))) {
                coolDownEndDateTime = lastDateTime.plusMinutes(INTERRUPT_COOL_DOWN_PERIOD)
                updatedAllCoolDown = true
            }
        }
        return updatedAllCoolDown
    }

    fun isInPromptWindow(lastTimestamp: String?) : Boolean{
        val lastDateTime = LocalDateTime.parse(lastTimestamp)
        return startPromptAllowPeriod != null &&
                lastDateTime > startPromptAllowPeriod &&
                lastDateTime < startPromptAllowPeriod!!.plusMinutes(PROMPT_PERIOD)
    }

    fun isInCoolDownPeriod(lastTimestamp: String?) : Boolean {
       return coolDownEndDateTime != null && LocalDateTime.parse(lastTimestamp) <= coolDownEndDateTime
    }

    private fun isInSleepButtonPeriod(lastTimestamp: String?) : Boolean {
        return lastSleepButtonDateTime != null && LocalDateTime.parse(lastTimestamp) <= lastSleepButtonDateTime!!.plusMinutes(
            SLEEP_COOL_DOWN_PERIOD)
    }

    //if we detect a possible interrupt event we may want to do something with it even if no recent prompts
    fun isInHighActivityPeriod(lastTimestamp: String?, coolDownPeriod: Long = HIGH_ACTIVITY_COOL_DOWN_PERIOD) : Boolean {
        return lastHighActivityEventDateTime != null && LocalDateTime.parse(lastTimestamp) <= lastHighActivityEventDateTime!!.plusMinutes(
            coolDownPeriod)
    }

    fun isAwakeEventBeforePeriod(lastTimestamp: String?, period: Long): Boolean {
        return (awakeEventList.isEmpty() || LocalDateTime.parse(lastTimestamp) >= awakeEventList.last()
            .plusMinutes(period))
    }

    private fun isRecentPromptEvent(lastTimestamp: String?): Boolean {
        return allPromptEvents.isNotEmpty() && LocalDateTime.parse(lastTimestamp) <= allPromptEvents.last().plusSeconds(SECONDS_BETWEEN_PROMPTS)
    }

    private fun isRecentAwakeEvent(lastTimestamp: String?): Boolean {
        return awakeEventList.isNotEmpty() && LocalDateTime.parse(lastTimestamp) <= awakeEventList.last().plusMinutes(BETWEEN_AWAKE_PERIOD)
    }

    fun isAwakeEventAllowed(lastTimestamp: String?): Boolean {
        return !isInSleepButtonPeriod(lastTimestamp) && !isRecentPromptEvent(lastTimestamp) && !isRecentAwakeEvent(lastTimestamp)
    }

    fun isPromptEventAllowed(lastTimestamp: String?): Boolean {
        //Log.d("PromptMonitor", "$lastTimestamp isInPromptWindow = ${isInPromptWindow(lastTimestamp)}")
        val isAllowed = promptEventWaiting == null && isInPromptWindow(lastTimestamp) && !isInAwakePeriod(lastTimestamp) && !isInCoolDownPeriod(lastTimestamp) &&
                !isRecentPromptEvent(lastTimestamp)

        if(isAllowed) {
            //extend the prompt window
            startPromptAllowPeriod =  LocalDateTime.parse(lastTimestamp)
        }

        return isAllowed
    }

    fun isFollowUpEventAllowed(lastTimestamp: String?): Boolean {
        //we want several events in a row to nudge the sleeper - but anchored to a rem or light trigger event within the last few minutes
        //This allows for padding with one or more follow-up events in a cycle of prompts
        val remAndLightEvents = (remEventList + lightEventList).sorted()
        val lastDateTime = LocalDateTime.parse(lastTimestamp)

        return remAndLightEvents.isNotEmpty() && promptEventWaiting == null &&
                !isInCoolDownPeriod(lastTimestamp) && !isInAwakePeriod(lastTimestamp) &&
                (lastFollowupDateTime == null || remAndLightEvents.last() > lastFollowupDateTime) &&
                lastDateTime > remAndLightEvents.last().plusSeconds(SECONDS_BETWEEN_PROMPTS) &&
                lastDateTime<= remAndLightEvents.last().plusSeconds(SECONDS_BETWEEN_PROMPTS * 2)
    }

    private fun isInAwakePeriod(lastTimestamp: String?) : Boolean {
        return lastAwakeDateTime != null &&
                LocalDateTime.parse(lastTimestamp) <= lastAwakeDateTime!!.plusMinutes(IN_AWAKE_PERIOD)
    }

    fun promptIntensityLevel(promptCount: Int = 1): Int {
        return when(promptCount) {
            3 -> 1
            else -> 0
        }
    }

    fun getPromptCountInPeriod(lastDateTime: LocalDateTime) : Int {
        val inPromptChain = lastFirstPromptDateTime != null &&
                lastFirstPromptDateTime!! > lastDateTime.minusMinutes(PROMPT_PERIOD)

        //Log.d("MainActivity", "$lastDateTime inPromptChain = $inPromptChain list size = ${allPromptEvents.size}")
//        if(lastFirstPromptDateTime != null) {
//            Log.d("MainActivity", "count = ${allPromptEvents.filter { it > lastFirstPromptDateTime }.size}")
//        }

        return if(inPromptChain) {
            allPromptEvents.filter { it > lastFirstPromptDateTime }.size + 1
        } else {
            1
        }
    }

    private fun getMaxPromptCountPerPeriod(lastDateTime: LocalDateTime): Int {
        val hour = lastDateTime.hour
        var maxPromptCount = 6

        if (hour > 5) {
            maxPromptCount = 5
        }

        return maxPromptCount
    }
}
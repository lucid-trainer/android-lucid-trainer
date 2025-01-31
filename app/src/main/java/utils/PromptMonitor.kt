package utils

import android.util.Log
import java.time.DayOfWeek
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
    var lastActivityEventDateTime: LocalDateTime? = null
    var coolDownEndDateTime : LocalDateTime? = null
    var lastFirstPromptDateTime : LocalDateTime? = null
    var lastAlarmEvent : Pair<Int, Int>? = null

    private var remEventTriggerList: MutableList<LocalDateTime> =
        emptyList<LocalDateTime>().toMutableList()
    var startPromptAllowPeriod: LocalDateTime? = null

    companion object {
        const val NEW_PROMPT_PERIOD_WAIT_SECONDS = 45L
        const val PROMPT_PERIOD = 35L
        const val MAX_PROMPT_COOL_DOWN_PERIOD = 12L
        const val INTERRUPT_COOL_DOWN_PERIOD = 10L
        const val ACTIVITY_COOL_DOWN_PERIOD = 10L
        const val SLEEP_COOL_DOWN_PERIOD = 50L
        const val IN_AWAKE_PERIOD = 6L
        const val BETWEEN_AWAKE_PERIOD = 70L
        const val SECONDS_BETWEEN_PROMPTS = 90L
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
        lastActivityEventDateTime = null
        coolDownEndDateTime = null
        startPromptAllowPeriod = null
        lastAlarmEvent = null
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
        lastActivityEventDateTime = lastDateTime

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

    fun isInActivityPeriod(lastTimestamp: String?, coolDownPeriod: Long = ACTIVITY_COOL_DOWN_PERIOD) : Boolean {
        return lastActivityEventDateTime != null && LocalDateTime.parse(lastTimestamp) <= lastActivityEventDateTime!!.plusMinutes(
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

    fun getPromptHoursAllowed(lastTimestamp: String?, logOnly: Boolean = false): Boolean {
        val triggerDateTime = LocalDateTime.parse(lastTimestamp)
        val hour = triggerDateTime.hour
        val day = triggerDateTime.dayOfWeek
        val hourLimit = if(day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY) 6 else 5

        val allowedFirstPartOfNight = hour in 2..3
                && isAwakeEventBeforePeriod(lastTimestamp, 20)
        val allowedSecondPartOfNight = hour in 4 .. hourLimit
                && isAwakeEventBeforePeriod(lastTimestamp, 10)

        return if(logOnly) {
            hour in 0..hourLimit
        } else {
            allowedFirstPartOfNight || allowedSecondPartOfNight
        }
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

    fun isAlarmEventTime(): Boolean {
        var isAlarmTime = false

        val current = LocalDateTime.now()
        val hour = current.hour
        val minute = current.minute

        val day = current.dayOfWeek
        val isWeekend = day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY
        val isMondayWednesday = day == DayOfWeek.MONDAY || day == DayOfWeek.WEDNESDAY

        var alarmTimes = if(isWeekend) arrayListOf(Pair(7,5), Pair(7,10), Pair(7,55), Pair(8,5), Pair(8,10) )
            else if(isMondayWednesday) arrayListOf(Pair(6,20), Pair(6,25), Pair(6,30))
            else arrayListOf(Pair(6,45), Pair(6,50), Pair(6,55), Pair(7,5), Pair(7,10))

        //Log.d("MainActivity","${viewModel.lastTimestamp.value} hour=$hour minute=$minute alarmHour=$alarmHour")


        for(alarmTime in alarmTimes) {
            val (first, second) = alarmTime
            if(first == hour && second == minute && (lastAlarmEvent == null || lastAlarmEvent!! != alarmTime)) {
                lastAlarmEvent = alarmTime
                isAlarmTime = true
                break
            }
        }

        return isAlarmTime
    }

    fun promptIntensityLevel(promptCount: Int = 1): Int {
        return when(promptCount) {
            2 -> 1
            3 -> 2
            else -> 0
        }
    }

    fun getPromptCountInPeriod(lastDateTime: LocalDateTime) : Int {
        val inPromptChain = lastFirstPromptDateTime != null &&
                lastFirstPromptDateTime!! > lastDateTime.minusMinutes(PROMPT_PERIOD)

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
            maxPromptCount = 4
        }

        return maxPromptCount
    }
}
package utils

import android.util.Log
import presentation.MainActivity.Companion.EVENT_LABEL_REM
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class PromptMonitor {
    var promptEventWaiting: String? = null
    private var awakeEventList: MutableList<LocalDateTime> =
        emptyList<LocalDateTime>().toMutableList()
    private var promptEvents: MutableList<LocalDateTime> = emptyList<LocalDateTime>().toMutableList()
    var triggerEvents: MutableList<LocalDateTime> = emptyList<LocalDateTime>().toMutableList()
    var promptTriggerAndEventCount: MutableList<Pair<LocalDateTime, Int>> = emptyList<Pair<LocalDateTime, Int>>().toMutableList()
    private var currentPromptList: MutableList<LocalDateTime> = emptyList<LocalDateTime>().toMutableList()
    private var lastPromptDateTime: LocalDateTime? = null


    var lastAwakeDateTime : LocalDateTime? = null
    var lastFollowupDateTime : LocalDateTime? = null
    var lastSleepButtonDateTime: LocalDateTime? = null
    var lastActivityEventDateTime: LocalDateTime? = null
    var coolDownEndDateTime : LocalDateTime? = null
    var lastAlarmEvent : Pair<Int, Int>? = null

    private var remEventTriggerList: MutableList<LocalDateTime> =
        emptyList<LocalDateTime>().toMutableList()
    var startPromptAllowPeriod: LocalDateTime? = null

    companion object {
        const val NEW_PROMPT_PERIOD_WAIT_SECONDS = 45L
        const val PROMPT_PERIOD = 20L  //the period in which a new prompt chain can run
        const val MIN_PROMPT_COOL_DOWN_PERIOD = 5L //periods between allowed prompt chains
        const val MAX_PROMPT_COOL_DOWN_PERIOD = 15L //periods between allowed prompt chains
        const val MIN_PROMPT_COUNT = 4
        const val MAX_PROMPT_COUNT = 10
        const val INTERRUPT_COOL_DOWN_PERIOD = 10L //period that prompts are quited after movement
        const val ACTIVITY_COOL_DOWN_PERIOD = 10L
        const val SLEEP_COOL_DOWN_PERIOD = 50L
        const val IN_AWAKE_PERIOD = 6L
        const val BETWEEN_AWAKE_PERIOD = 70L
        const val SECONDS_BETWEEN_PROMPTS = 90L
    }

    private val formatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")

    fun clear() {
        awakeEventList.clear()
        currentPromptList.clear()
        promptEvents.clear()
        remEventTriggerList.clear()
        promptTriggerAndEventCount.clear()
        promptEventWaiting = null
        lastAwakeDateTime = null
        lastFollowupDateTime = null
        lastActivityEventDateTime = null
        coolDownEndDateTime = null
        startPromptAllowPeriod = null
        lastAlarmEvent = null
        lastPromptDateTime = null
    }

    fun getEventsDisplay(): String {
        var eventsDisplay = ""

        if (awakeEventList.isNotEmpty()) {
            val formatAwakeEvents = awakeEventList.map { dateTime -> dateTime.format(formatter) }
            eventsDisplay += "ActiveEvents: $formatAwakeEvents \n"
        }

        if(promptTriggerAndEventCount.isNotEmpty()) {
            val formatTriggerAndCounts = promptTriggerAndEventCount.map { triggerAndCount -> triggerAndCount.first.format(formatter) + "[" +
                triggerAndCount.second + "]"}
            eventsDisplay += "PromptEvents: $formatTriggerAndCounts \n"
        }

        return eventsDisplay
    }

    fun addAwakeEvent(lastDateTime: LocalDateTime) {
        awakeEventList.add(lastDateTime)
    }

    fun addPromptEvent(lastDateTime: LocalDateTime) {
        promptEvents.add(lastDateTime)

        //keep track of the prompt events that are actually fired and the first trigger
        incrementTriggerAndEventCount()
    }

    private fun addNewTriggerAndEventCount(now: LocalDateTime) {
        Log.d("MainActivity","adding trigger $now count 0")
        promptTriggerAndEventCount.add(Pair(now, 0))
    }

    private fun incrementTriggerAndEventCount() {
        val lastIdx = promptTriggerAndEventCount.size - 1
        if (lastIdx >= 0) {
            var lastPromptTriggerCount = promptTriggerAndEventCount[lastIdx]
            val lastCount = lastPromptTriggerCount.second
            Log.d("MainActivity","incrementing trigger ${lastPromptTriggerCount.first} from $lastCount")
            lastPromptTriggerCount = lastPromptTriggerCount.copy(second = lastCount + 1)
            promptTriggerAndEventCount[lastIdx] = lastPromptTriggerCount
        }
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

    fun checkInterruptCoolDown(lastTimestamp: String?, isButton: Boolean) : Boolean {
        //Check if we've had an interrupt from the watch device via high movement or the stop/sleep button and disable prompts for a time if so
        var updatedAllCoolDown = false
        val lastDateTime = LocalDateTime.parse(lastTimestamp)
        lastActivityEventDateTime = lastDateTime

        if(isButton) {
            //set the timeout to at least length of constant plus some random additional minutes
            lastSleepButtonDateTime = lastDateTime
            val sleepBtnCoolDown = SLEEP_COOL_DOWN_PERIOD + (0..40).random()
            coolDownEndDateTime = lastDateTime.plusMinutes(sleepBtnCoolDown)
            updatedAllCoolDown = true
            currentPromptList.clear()
            Log.d("MainActivity","clearing prompt list from isSleepButton, cool down ends $coolDownEndDateTime")
        } else {
            if(promptEvents.isNotEmpty() && ( coolDownEndDateTime == null ||
                (lastDateTime > coolDownEndDateTime && lastDateTime > promptEvents.last() &&
                 lastDateTime < promptEvents.last().plusMinutes(4)))) {
                //there was enough movement to interrupt any prompts, clear the list
                coolDownEndDateTime = lastDateTime.plusMinutes(INTERRUPT_COOL_DOWN_PERIOD)
                updatedAllCoolDown = true
                currentPromptList.clear()
                Log.d("MainActivity","clearing prompt list from interrupt cooldown $lastTimestamp")
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

    private fun isAwakeEventBeforePeriod(lastTimestamp: String?, period: Long): Boolean {
        return (awakeEventList.isEmpty() || LocalDateTime.parse(lastTimestamp) >= awakeEventList.last()
            .plusMinutes(period))
    }

    private fun isRecentPromptTriggerEvent(lastTimestamp: String?): Boolean {
        return triggerEvents.isNotEmpty() && LocalDateTime.parse(lastTimestamp) <= triggerEvents.last().plusSeconds(60)
    }

    private fun isRecentAwakeEvent(lastTimestamp: String?): Boolean {
        return awakeEventList.isNotEmpty() && LocalDateTime.parse(lastTimestamp) <= awakeEventList.last().plusMinutes(BETWEEN_AWAKE_PERIOD)
    }

    fun isAwakeEventAllowed(lastTimestamp: String?): Boolean {
        return !isInSleepButtonPeriod(lastTimestamp) && !isRecentPromptTriggerEvent(lastTimestamp) && !isRecentAwakeEvent(lastTimestamp)
    }

    fun getPromptHoursAllowed(lastTimestamp: String?, logOnly: Boolean = false): Boolean {
        val triggerDateTime = LocalDateTime.parse(lastTimestamp)
        val hour = triggerDateTime.hour
        val day = triggerDateTime.dayOfWeek
        val hourLimit = if(day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY) 7 else 5

        val allowedFirstPartOfNight = hour in 1..3
                && isAwakeEventBeforePeriod(lastTimestamp, 20)
        val allowedSecondPartOfNight = hour in 4 .. hourLimit
                && isAwakeEventBeforePeriod(lastTimestamp, 10)

        return if(logOnly) {
            hour in 0..hourLimit
        } else {
            allowedFirstPartOfNight || allowedSecondPartOfNight
        }
    }

    fun setPromptsIfAllowed(lastTimestamp: String?, eventType: String): Boolean {
        //Log.d("PromptMonitor", "$lastTimestamp isInPromptWindow = ${isInPromptWindow(lastTimestamp)}")
        val isAllowed = promptEventWaiting == null && isInPromptWindow(lastTimestamp) && !isInAwakePeriod(lastTimestamp)
                && !isInCoolDownPeriod(lastTimestamp)  && !isRecentPromptTriggerEvent(lastTimestamp)
                && (eventType == EVENT_LABEL_REM || currentPromptList.isNotEmpty())  // we only want REM events to start new chain

        if(isAllowed) {
            //extend the prompt window and add prompts to the list
            val triggerTime = LocalDateTime.parse(lastTimestamp)
            triggerEvents.add(triggerTime)
            addPrompts(lastTimestamp)
        }

        return isAllowed
    }

    private fun addPrompts(lastTimestamp: String?) {
        val triggerDateTime = LocalDateTime.parse(lastTimestamp)

        //load the initial list of prompts, or add a couple for each trigger
        if(currentPromptList.isEmpty()) {
            var nextPromptTime = triggerDateTime.plusSeconds(30)
            while(currentPromptList.size < MIN_PROMPT_COUNT) {
                Log.d("MainActivity","adding prompt $nextPromptTime")
                currentPromptList.add(nextPromptTime)
                nextPromptTime = nextPromptTime.plusSeconds(SECONDS_BETWEEN_PROMPTS)
            }

            Log.d("MainActivity","adding prompts and new trigger for $triggerDateTime")
            startPromptAllowPeriod = triggerDateTime
            addNewTriggerAndEventCount(triggerDateTime)
        } else if(currentPromptList.size < MAX_PROMPT_COUNT){
            repeat(2) {
                val lastPromptTime = currentPromptList.last()
                val nextPromptTime = if(lastPromptTime > triggerDateTime) lastPromptTime.plusSeconds(SECONDS_BETWEEN_PROMPTS)
                       else triggerDateTime.plusSeconds(60)
                Log.d("MainActivity","adding additional prompt $nextPromptTime")
                currentPromptList.add(nextPromptTime)
            }

            Log.d("MainActivity","updatingPrompts for $triggerDateTime")
        }

        Log.d("MainActivity","end of add prompts, list size is now ${currentPromptList.size}")
    }

    fun getNextPrompt(lastTimestamp: String?): LocalDateTime? {
        val triggerDateTime =  LocalDateTime.parse(lastTimestamp)
        var nextPrompt: LocalDateTime? = null

        //get the first prompt that's before the trigger time
        nextPrompt = if (lastPromptDateTime != null) {
            currentPromptList.firstOrNull{ it > lastPromptDateTime && it < triggerDateTime }
        } else {
            currentPromptList.firstOrNull { it < triggerDateTime }
        }

        if (nextPrompt != null) {
            lastPromptDateTime = nextPrompt
            Log.d("MainActivity","returning non-null prompt $nextPrompt")
        } else {
            //if it's been more than 5 minutes without a prompt, go ahead and clear the list
            //and set a cool down period before allowing new prompt triggers
            if(currentPromptList.size > 0
                && currentPromptList.firstOrNull{ it > triggerDateTime!!.minusMinutes(5) } == null) {
                //if we haven't gotten close to max prompts, we'll set a smaller cool down period
                val promptCoolDownPeriod = if(currentPromptList.size < MAX_PROMPT_COUNT - 2)
                    MIN_PROMPT_COOL_DOWN_PERIOD else MAX_PROMPT_COOL_DOWN_PERIOD
                coolDownEndDateTime = triggerDateTime.plusMinutes(promptCoolDownPeriod)
                Log.d("MainActivity","adding $promptCoolDownPeriod to get cool down $coolDownEndDateTime")
                currentPromptList.clear()
                Log.d("MainActivity","clearing prompt list from getNextPrompt")
            }
        }

        return nextPrompt
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

        var alarmTimes = if(isWeekend) arrayListOf(Pair(8,10), Pair(8,15))
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
            1 -> 1
            else -> 0
        }
    }

    fun getPromptCountInChain(lastDateTime: LocalDateTime) : Int {
        var promptCount = 1
        for ((i, promptTime) in currentPromptList.withIndex()) {
            if(promptTime == lastPromptDateTime) {
                promptCount =  i + 1
                break
            }
        }

        return promptCount
    }
}
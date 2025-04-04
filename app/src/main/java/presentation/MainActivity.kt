package presentation

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.graphics.Color
import android.media.AudioManager
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.AdapterView
import android.widget.SeekBar
import android.widget.SeekBar.OnSeekBarChangeListener
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.lucidtrainer.R
import com.lucidtrainer.databinding.ActivityMainBinding
import database.ReadingDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield
import network.Status
import network.request.DeviceDocument
import repository.DeviceDocumentsRepository
import sound.PodSoundRoutine
import sound.SoundPoolManager
import utils.AppConfig
import utils.FileManager
import utils.PromptMonitor
import utils.SpeechManager
import utils.TestManager
import viewmodel.DocumentViewModel
import viewmodel.DocumentViewModelFactory
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter


class MainActivity : AppCompatActivity(), AdapterView.OnItemSelectedListener {

    // variable to initialize it later
    private lateinit var viewModel: DocumentViewModel

    // create a view binding variable
    private lateinit var binding: ActivityMainBinding

    companion object {
        const val PLAY_EVENT = "playsound"
        const val POD_EVENT = "podcast"
        const val SLEEP_EVENT = "sleep"
        const val EVENT_LABEL_BUTTON = "button_press"
        const val EVENT_LABEL_WATCH = "watch_event"
        const val EVENT_LABEL_AWAKE = "awake_event"
        const val EVENT_LABEL_REM = "rem_event"
        const val EVENT_LABEL_PROMPT = "prompt_event"
        const val SLEEP_EVENT_PROMPT_DELAY = 10000L //3000L DEBUG VALUE
        const val RESET_VOL = 9

        const val ROOT_DIR = "lt_sounds"
        const val THEMES_DIR = "themes"
        const val FOREGROUND_DIR = "fg"
        const val CLIP_DIR = "clip"

        const val MANUAL_PLAY_MESSAGE = "Manual play"
        const val ACTIVE_EVENT_MESSAGE = "Movement"
        const val WATCH_EVENT_MESSAGE = "Watch event"
        const val INTERRUPT_MESSAGE = "Movement Interrupt"

        const val WILD_MESSAGE = "wild"
        const val MILD_MESSAGE = "mild"
        const val SSILD_MESSAGE = "s s i l d"
        const val SLEEP_BUTTON_MESSAGE = "sleep button"

        val filter = IntentFilter().apply {
            addAction(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
        }
    }

    //set up the manager services
    private lateinit var audioManager: AudioManager
    private lateinit var soundPoolManager: SoundPoolManager
    private lateinit var fileManager: FileManager
    private lateinit var speechManager: SpeechManager
    private lateinit var testManager: TestManager

    private var lastEventTimestamp = ""
    private var lastActiveEventTimestamp: LocalDateTime? = null
    private var apJob: Job? = null

    //monitor event sleep stage estimate for prompts
    private val promptMonitor: PromptMonitor = PromptMonitor()

    private val myNoisyAudioStreamReceiver = getSpeakerEnabledReceiver()

    private var maxVolume = 0
    private var mBgRawId = -1
    private var mBgLabel = ""

    //for testing prompting
    private var isPromptTesting = false

    private val deviceDocumentRepository = DeviceDocumentsRepository(
        AppConfig.ApiService()
    )

    @SuppressLint("SourceLockedOrientationActivity")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        audioManager = getSystemService(AUDIO_SERVICE) as AudioManager

        // instantiate view binding
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        fileManager = FileManager.getInstance(applicationContext)

        speechManager = SpeechManager.getInstance(applicationContext)

        soundPoolManager = SoundPoolManager.getInstance(application)

        testManager = TestManager.getInstance(soundPoolManager)

        setupSound()

        // initialize viewModel
        val dao = ReadingDatabase.getInstance(application).readingDao
        val viewModelFactory = DocumentViewModelFactory(dao)
        viewModel = ViewModelProvider(
            this, viewModelFactory
        )[DocumentViewModel::class.java]

        this.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT

        // use a broadcast receiver to stop any playback when bluetooth is disconnected
        registerReceiver(myNoisyAudioStreamReceiver, filter)

        // clear any old data
        purgeOldRecords(viewModel.startingDateTime)

        // Create the observer which updates the latest reading from the database
        val lastTimestampObserver = Observer<String> { }
        val lastReadingStringObserver = Observer<String> { }
        val sleepStageObserver = Observer<String> { }
        val eventMapObserver = Observer<Map<String, String>> { }

        // Observe the LiveData, passing in this activity as the LifecycleOwner and the observer.
        viewModel.lastTimestamp.observe(this, lastTimestampObserver)
        viewModel.lastReadingString.observe(this, lastReadingStringObserver)
        viewModel.sleepStage.observe(this, sleepStageObserver)
        viewModel.eventMap.observe(this, eventMapObserver)

        binding.switchcompat.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                clearSessionState()
                viewModel.setFlowEnabled(true)
                viewModel.getNewReadings()
            } else {
                viewModel.setFlowEnabled(false)
            }
        }

        // Since flow runs asynchronously,
        // start listening on background thread
        lifecycleScope.launch {

            viewModel.documentState.collect {

                // When state to check the
                // state of received data
                when (it.status) {
                    // If its init state then
                    // hide the progress bar
                    Status.INIT -> {
                        binding.progressBar.isVisible = false
                    }

                    // If its loading state then
                    // show the progress bar
                    Status.LOADING -> {
                        binding.progressBar.isVisible = true

                    }
                    // If api call was a success , Update the Ui with
                    // data and make progress bar invisible
                    Status.SUCCESS -> {
                        binding.progressBar.isVisible = false

                        //check for speech events initiated by alarms or sound routines
                        handleSpeechEvents()

                        if (lastEventTimestamp != viewModel.lastTimestamp.value) {
                            val dateFormat = DateTimeFormatter.ofPattern("M/dd/yyyy hh:mm:ss")
                            val displayDate = LocalDateTime.parse(viewModel.lastTimestamp.value)
                                .format(dateFormat)

                            //initialize the lastAwakeTimestamp
                            if (promptMonitor.lastAwakeDateTime == null) {
                                promptMonitor.lastAwakeDateTime =
                                    LocalDateTime.parse(viewModel.lastTimestamp.value)
                            }

                            //get any recent high active event and interrupt any prompts if found running
                            handleActivityEvent()

                            var reading = viewModel.lastReadingString.value

                            val sleepStage = viewModel.sleepStage.value ?: ""
                            processSleepStageEvents(sleepStage)

                            //check for prompt events
                            checkAndSubmitPromptEvent()

                            val eventsDisplay = promptMonitor.getEventsDisplay()
                            if (eventsDisplay.isNotEmpty()) {
                                reading += eventsDisplay
                            }

                            binding.timestampTextview.text = displayDate
                            binding.readingTextview.text = reading
                            binding.sleepStageTexview.text = sleepStage

                            if (viewModel.eventMap.value?.isNotEmpty()!!) {
                                viewModel.eventMap.value?.let { events ->
                                    playPromptsFromWatchUI(
                                        events
                                    )
                                }
                            }
                            lastEventTimestamp = viewModel.lastTimestamp.value.toString()
                        }
                    }
                    // In case of error, show some data to user
                    else -> {
                        Log.e("MainActivity", "$it.message")
                        binding.progressBar.isVisible = false
                        Toast.makeText(this@MainActivity, "${it.message}", Toast.LENGTH_SHORT)
                            .show()
                    }
                }

            }
        }
    }

    private fun resetNoisyReceiver() {
        unregisterReceiver(myNoisyAudioStreamReceiver)
        registerReceiver(myNoisyAudioStreamReceiver, filter)
    }

    private fun handleActivityEvent() {
        if (viewModel.lastActiveEventTimestamp != null && (lastActiveEventTimestamp == null || (
                    viewModel.lastActiveEventTimestamp!! > lastActiveEventTimestamp))
        ) {

            lastActiveEventTimestamp = viewModel.lastActiveEventTimestamp
            val hour = LocalDateTime.parse(viewModel.lastTimestamp.value).hour
            val hoursAllowed = hour in 22..23 || hour in 0..8
            val lastActivityValue = viewModel.lastActivityValue
            val isInActivityPeriod =
                promptMonitor.isInActivityPeriod(viewModel.lastTimestamp.value, 3L)
            val isPromptRunning = promptMonitor.promptEventWaiting != null

            if (lastActivityValue != "NONE" && hoursAllowed && !isInActivityPeriod && !isPromptRunning) {
                //we'll read out the time for any elevated activity
                speechManager.speakTheTimeWithMessage(lastActivityValue + " " + ACTIVE_EVENT_MESSAGE)
            }

            //we'll set a cooldown period to interrupt prompting if enough activity
            if (lastActivityValue == "MEDIUM" || lastActivityValue == "HIGH") {
                checkShouldStartInterruptCoolDown()
            }
        }
    }

    private fun handleSpeechEvents() {
        if (promptMonitor.isAlarmEventTime()) {
            speechManager.speakTheTime()
        }

        speechManager.handleSoundRoutineEvents()
    }

    private fun checkShouldStartInterruptCoolDown(isButton: Boolean = false) {
        val isStartAllCoolDown =
            promptMonitor.checkInterruptCoolDown(viewModel.lastTimestamp.value, isButton)

        if (isStartAllCoolDown && isButton) {
            speechManager.speakTheTimeWithMessage( SLEEP_BUTTON_MESSAGE, INTERRUPT_MESSAGE)
        }

        if (isStartAllCoolDown && !isButton) {
            stopSoundRoutine()
        }
    }

    private fun clearSessionState() {
        promptMonitor.clear()
        fileManager.clearFilesInSession()
    }

    private fun processSleepStageEvents(sleepStage: String) {
        //Log.d("SleepStage", "${viewModel.lastTimestamp.value} stage=$sleepStage lastAwake=${viewModel.lastAwakeTimestamp}")
        when (sleepStage) {
            "AWAKE" -> {
                binding.sleepStageTexview.setTextColor(Color.RED)

                if (promptMonitor.promptEventWaiting != null && promptMonitor.promptEventWaiting != EVENT_LABEL_AWAKE) {
                    //if we have an event other than awake event waiting it's likely part of this general activity so cancel
                    cancelStartCountDownPrompt(EVENT_LABEL_AWAKE)
                }

                promptMonitor.lastAwakeDateTime = viewModel.lastAwakeTimestamp
                checkAndSubmitAwakePromptEvent()
            }

            "RESTLESS" -> {
                binding.sleepStageTexview.setTextColor(Color.RED)
            }

            "LIGHT ASLEEP" -> {
                binding.sleepStageTexview.setTextColor(Color.YELLOW)
            }

            "REM ASLEEP" -> {
                checkForTriggerREMPromptEvent()
                binding.sleepStageTexview.setTextColor(Color.YELLOW)
            }

            "ASLEEP", "DEEP ASLEEP" -> {
                binding.sleepStageTexview.setTextColor(Color.BLUE)
            }
        }
    }

    private fun checkAndSubmitAwakePromptEvent() {

        val triggerDateTime = LocalDateTime.parse(viewModel.lastTimestamp.value)
        val hour = triggerDateTime.hour
        val day = triggerDateTime.dayOfWeek
        val hourLimit = if (day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY) 5 else 4

        if (binding.chipAwake.isChecked) {
            val hoursAllowed = hour in 2..hourLimit
            val isAwakeEventAllowed =
                hoursAllowed && promptMonitor.isAwakeEventAllowed(viewModel.lastTimestamp.value)

            if (hoursAllowed) {
                val document = getDeviceDocument(EVENT_LABEL_AWAKE, isAwakeEventAllowed)
                logEvent(document)
            }

            if (isAwakeEventAllowed) {
                Log.d("MainActivity", "calling prompt with awake label");
                startCountDownPromptTimer(EVENT_LABEL_AWAKE)
            }
        }
    }

    private fun checkForTriggerREMPromptEvent() {
        if (binding.chipRem.isChecked) {
            val hoursAllowed = promptMonitor.getPromptHoursAllowed(viewModel.lastTimestamp.value)
            val logAllowed = getPromptLogHoursAllowed(viewModel.lastTimestamp.value)

            val isREMPromptTriggerSet = hoursAllowed && promptMonitor.setPromptsIfAllowed(
                viewModel.lastTimestamp.value,
                EVENT_LABEL_REM
            )

            if (logAllowed) {
                val document = getDeviceDocument(EVENT_LABEL_REM, isREMPromptTriggerSet)
                logEvent(document)
            }

            if (!isREMPromptTriggerSet) {
                //each rem trigger event that doesn't result in prompt is evaluated to possibly set next allowed prompt window
                promptMonitor.checkRemTriggerEvent(viewModel.lastTimestamp.value)
            }
        }
    }

    private fun getPromptLogHoursAllowed(lastTimestamp: String?): Boolean {
        return promptMonitor.getPromptHoursAllowed(lastTimestamp, true)
    }

    private fun checkAndSubmitPromptEvent() {
        val triggerDateTime = LocalDateTime.parse(viewModel.lastTimestamp.value)

        if (binding.chipRem.isChecked) {
            val nextPrompt = promptMonitor.getNextPrompt(viewModel.lastTimestamp.value)

            if (nextPrompt != null) {
                val document = getDeviceDocument(EVENT_LABEL_PROMPT, true)
                logEvent(document)

                startCountDownPromptTimer(EVENT_LABEL_PROMPT)
                promptMonitor.lastFollowupDateTime = triggerDateTime
            }
        }
    }

    private fun logEvent(document: DeviceDocument) {

        CoroutineScope(Dispatchers.Default).launch {
            deviceDocumentRepository.postDevicePrompt(
                "logdata",
                document
            )
        }
    }

    private fun setupSound() {

        val bgSelector = binding.bgNoiseSpin
        bgSelector.onItemSelectedListener = this

        binding.btnNoise.setOnClickListener {
            if (mBgRawId != -1) {
                resetNoisyReceiver()
                soundPoolManager.stopPlayingBackground()
                binding.playStatus.text = "Playing $mBgLabel"
                soundPoolManager.playBackgroundSound(mBgRawId, 1F, binding.playStatus, 1F)
            } else {
                val text = "You need to choose a white noise selection"
                Toast.makeText(application, text, Toast.LENGTH_LONG).show()
            }
        }

        binding.btnPrompt.setOnClickListener {
            if (binding.chipGroup.checkedChipId == View.NO_ID) {
                val text = "You need to choose a sound routine"
                Toast.makeText(application, text, Toast.LENGTH_LONG).show()
            } else if(isPromptTesting) {
                val pType = if(binding.chipWild.isChecked) "w" else "m"
                testManager.testPrompting(pType, mBgRawId, mBgLabel, binding.playStatus)
            } else {
                resetNoisyReceiver()
                playPromptsFromEventsOrUI(EVENT_LABEL_BUTTON)
            }
        }

        binding.btnStop.setOnClickListener {
            soundPoolManager.stopPlayingAll(binding.playStatus)

            if(isPromptTesting) {
                testManager.stopPlayingAll(binding.playStatus)
            }
            checkShouldStartInterruptCoolDown()
        }

        binding.btnReset.setOnClickListener {
            confirmAndResetUsedFiles()
        }

        binding.btnClearDb.setOnClickListener {
            purgeAllRecords()
            viewModel.workingReadingList.clear()
            viewModel.sleepStage.value = ""
            //Log.d("MainActivity", "list size=" + viewModel.workingReadingList.size)
        }

        // Set the maximum volume of the SeekBar to the maximum volume of the MediaPlayer:
        maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        binding.seekBar.max = maxVolume
        //Log.d("MainActivity", "max volume=$maxVolume")

        // Set the current volume of the SeekBar to the current volume of the MediaPlayer:

        // Set the current volume of the SeekBar to the current volume of the MediaPlayer:
        val currVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        binding.seekBar.progress = currVolume
        //Log.d("MainActivity", "curr volume=$currVolume")

        binding.seekBar.setOnSeekBarChangeListener(object :
            OnSeekBarChangeListener {
            override fun onProgressChanged(
                seekBar: SeekBar,
                progress: Int, fromUser: Boolean
            ) {
                //Log.d("MainActivity", "volume=" + progress)
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, progress, 0)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                //do nothing
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                //do nothing
            }
        })

        binding.btnDefaultVol.setOnClickListener {
            binding.seekBar.progress = RESET_VOL
            binding.bgNoiseSpin.setSelection(1)
            binding.chipMid.isChecked = true
            binding.chipMild.isChecked = true
            binding.chipRem.isChecked = true
            binding.chipAwake.isChecked = false
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, RESET_VOL, 0)
        }


        val podCount =
            fileManager.getFilesFromDirectory(PodSoundRoutine.ROOT_DIR + "/" + PodSoundRoutine.POD_DIR).size
        binding.chipPod.isVisible = podCount > 0

        binding.chipGroupPod.isVisible = false
        binding.textPodLbl.isVisible = false

        binding.chipGroup.setOnCheckedStateChangeListener { _, _ ->
            if (binding.chipPod.isChecked) {
                binding.chipGroupPod.isVisible = true
                binding.textPodLbl.isVisible = true
            } else {
                binding.chipGroupPod.isVisible = false
                binding.textPodLbl.isVisible = false
            }
        }

        binding.chipGroupVol.setOnCheckedStateChangeListener { _, _ ->
            if (binding.chipLow.isChecked) {
                soundPoolManager.setAllVolAdj(0.65F)
            } else if (binding.chipMid.isChecked) {
                soundPoolManager.setAllVolAdj(0.85F)
            } else if (binding.chipHigh.isChecked) {
                soundPoolManager.setAllVolAdj(1.0F)
            } else {
                soundPoolManager.setAllVolAdj(0.85F)
            }
            soundPoolManager.stopPlayingAll(binding.playStatus)
        }
    }

    private fun confirmAndResetUsedFiles() {
        val builder = AlertDialog.Builder(this)

        builder.setMessage("Are you sure you want to reset all used files ")
            .setCancelable(false)
            .setPositiveButton("Yes") { dialog, id ->
                run {
                    val themes = fileManager.getAllDirectoriesFromPath("$ROOT_DIR/$THEMES_DIR")
                    for (theme in themes) {
                        val fgSize = fileManager.getUsedFilesFromDirectory(
                            "$ROOT_DIR/$THEMES_DIR/$theme/$FOREGROUND_DIR"
                        ).size
                        val clipSize = fileManager.getUsedFilesFromDirectory(
                            "$ROOT_DIR/$THEMES_DIR/$theme/$CLIP_DIR"
                        ).size
                        Log.d("MainActivity", "clearing used fg= $fgSize clip= $clipSize")

                        fileManager.resetFilesUsed(
                            "$ROOT_DIR/$THEMES_DIR/$theme/$FOREGROUND_DIR",
                            "$ROOT_DIR/$THEMES_DIR/$theme/$CLIP_DIR"
                        )
                    }
                }
            }
            .setNegativeButton("No") { dialog, id ->
                dialog.dismiss()
            }
        val alert = builder.create()
        alert.show()
    }

    /*
      Handles playing of prompt routines triggered from the app, either from events triggered by raw watch data or
      from UI events (button presses)
    */
    private fun playPromptsFromEventsOrUI(eventLabel: String, promptCount: Int = 0) {
        val soundList = mutableListOf<String>()
        var pMessage = ""

        val triggerDateTime = LocalDateTime.parse(viewModel.lastTimestamp.value)

        //default is a moderate length routine
        var playCount = 2

        var pType = "m"  //default to mild
        if (binding.chipSsild.isChecked) {
            pType = "s"
            pMessage = SSILD_MESSAGE
        }
        if (binding.chipWild.isChecked) {
            pType = "w"

            //manual wilds should be longer
            playCount = 3
            pMessage = WILD_MESSAGE
        }

        var pMod: String
        if (eventLabel == EVENT_LABEL_PROMPT) {
            pMod = "p"
        } else {
            //this can either be an auto awake event or an manual button event
            pMod = if (eventLabel == EVENT_LABEL_AWAKE) "a" else ""
            if (pMod.isEmpty()) {
                //it's a manual event from watch so update the event list and read the time out
                updateEventList(EVENT_LABEL_AWAKE, triggerDateTime.toString())
                speechManager.speakTheTimeWithMessage(MANUAL_PLAY_MESSAGE, pMessage)
            } else {
                //it's an auto play event so we want a minimal sound routine
                playCount = 1
            }
        }

        //Log.d("MainActivity", "called wth eventLabel = $eventLabel");
        soundList.add("$pType$pMod")

        //only allow this option via the prompt button
        if (eventLabel == EVENT_LABEL_BUTTON && binding.chipPod.isChecked) {
            if (binding.chipPod1.isChecked) {
                playCount = 1
            } else if (binding.chipPod2.isChecked) {
                playCount = 2
            } else if (binding.chipPod3.isChecked) {
                playCount = 3
            } else if (binding.chipPod4.isChecked) {
                playCount = 4
            }

            soundList.add("p")
        }

        resetNoisyReceiver()
        soundPoolManager.playSoundList(
            soundList, mBgRawId, mBgLabel, eventLabel, binding.playStatus, playCount, promptCount
        )
    }

    /*
      These are events passed from the watch to play a prompt routine, podcast or set a sleep period
     */
    private fun playPromptsFromWatchUI(eventMap: Map<String, String>) {
        Log.d("MainActivity", "called Watch method with eventMap " + eventMap.keys)

        val triggerDateTime = LocalDateTime.parse(viewModel.lastTimestamp.value)

        var soundList: MutableList<String> = emptyList<String>().toMutableList()
        var playCount = 2

        if (eventMap.containsKey(POD_EVENT) && (eventMap[POD_EVENT] != null)) {
            updateEventList(EVENT_LABEL_AWAKE, triggerDateTime.toString())
            CoroutineScope(Dispatchers.Default).launch {
                deviceDocumentRepository.postDevicePrompt(
                    "appdata",
                    getDeviceDocument(EVENT_LABEL_AWAKE, true)
                )
                //Log.d("MainActivity", "sending awake event to device repository for podcast event")
            }

            val podNumber = eventMap[POD_EVENT]!!.toInt()
            playCount = podNumber
            soundList.add("p")
        } else if (eventMap.containsKey(PLAY_EVENT) && (eventMap[PLAY_EVENT] != null)) {
            updateEventList(EVENT_LABEL_AWAKE, triggerDateTime.toString())
            CoroutineScope(Dispatchers.Default).launch {
                deviceDocumentRepository.postDevicePrompt(
                    "appdata",
                    getDeviceDocument(EVENT_LABEL_AWAKE, true)
                )
                //Log.d("MainActivity", "sending awake event to device repository for play event")
            }

            //get the prompt to play. This will usually be just one. For wild, play the auto version when triggered from watch
            val playEvent = eventMap[PLAY_EVENT]!!.replace("w", "wa")
            soundList = playEvent.split(",").toMutableList()

            var promptMessage = MILD_MESSAGE
            if (soundList.contains("s")) {
                promptMessage = SSILD_MESSAGE
            } else if (soundList.contains("wa")) {
                //wilds initiated from watch should be longer
                playCount = 3
                promptMessage = WILD_MESSAGE
            }

            speechManager.speakTheTimeWithMessage(WATCH_EVENT_MESSAGE, promptMessage)

        } else if (eventMap.containsKey(SLEEP_EVENT)) {
            //Log.d("PromptMonitor", "sleep event ${LocalDateTime.parse(viewModel.lastTimestamp.value)}")
            //stop any more prompts for a period of time
            lastActiveEventTimestamp = LocalDateTime.parse(viewModel.lastTimestamp.value)
            cancelStartCountDownPrompt(SLEEP_EVENT)
            checkShouldStartInterruptCoolDown(true)
        }

        if (soundList.isNotEmpty()) {
            soundPoolManager.stopPlayingForeground()
            checkShouldStartInterruptCoolDown()

            resetNoisyReceiver()

            Log.d("MainActivity", "play count $playCount for $soundList")

            soundPoolManager.playSoundList(
                soundList, mBgRawId, mBgLabel, EVENT_LABEL_WATCH, binding.playStatus, playCount
            )
        }
    }

    private fun stopSoundRoutine() {
        if (mBgRawId != -1) {
            soundPoolManager.stopPlayingBackground()
            binding.playStatus.text = "Playing $mBgLabel"
            soundPoolManager.playBackgroundSound(mBgRawId, 1F, binding.playStatus, 1F)
        }
    }

    private fun startCountDownPromptTimer(eventLabel: String) {
        //avoid stepping on a waiting or running job
        val isRunning = promptMonitor.promptEventWaiting != null

        Log.d("MainActivity", "called with event $eventLabel");

        if (isRunning) {
            Log.d(
                "MainActivity",
                "returning, promptEventWaiting = $promptMonitor.promptEventWaiting"
            )
            return
        }

        val scope = CoroutineScope(Dispatchers.Default)

        if (apJob == null || apJob!!.isCompleted) {
            apJob = scope.launch {
                promptMonitor.promptEventWaiting = eventLabel
                val triggerDateTime = LocalDateTime.parse(viewModel.lastTimestamp.value)

                //capture the in the event list
                updateEventList(eventLabel, triggerDateTime.toString())

                //get the current prompt count for rem associated prompt events
                var promptCount = 0
                if (eventLabel == EVENT_LABEL_PROMPT) {
                    promptCount = getPromptCount()
                }

                //send a vibration event to the watch
                deviceDocumentRepository.postDevicePrompt(
                    "appdata",
                    getDeviceDocument(eventLabel, true, promptCount)
                )

                if (eventLabel != EVENT_LABEL_AWAKE) {
                    //give the watch a little time to pick up the vibration event
                    yield()
                    delay(timeMillis = SLEEP_EVENT_PROMPT_DELAY)
                }

                playPromptsFromEventsOrUI(eventLabel, promptCount)

                delay(timeMillis = 10000)

                //Log.d("MainActivity", "set promptEventWaiting to null")
                promptMonitor.promptEventWaiting = null
            }
        }
    }

    private fun getPromptCount(): Int {
        val lastDateTime = LocalDateTime.parse(viewModel.lastTimestamp.value)
        return promptMonitor.getPromptCountInChain(lastDateTime)
    }

    private fun cancelStartCountDownPrompt(eventLabel: String) {
        Log.d("MainActivity", "stopping prompt from $eventLabel")

        promptMonitor.promptEventWaiting = null

        val document = getDeviceDocument("cancel from: $eventLabel", false)
        logEvent(document)

        if (apJob != null && apJob!!.isActive) {
            apJob!!.cancel()
        }

        if (mBgLabel.isNotEmpty()) {
            binding.playStatus.text = "Playing $mBgLabel"
        } else {
            binding.playStatus.text = ""
        }

        soundPoolManager.stopPlayingForeground()
        soundPoolManager.stopPlayingAltBackground()
        speechManager.clearSoundRoutineEvents()
    }

    private fun getDeviceDocument(
        type: String,
        allowed: Boolean,
        promptCount: Int = 0
    ): DeviceDocument {
        val triggerTimestamp =
            if (viewModel.lastTimestamp.value != null) viewModel.lastTimestamp.value!! else ""

        //add any additional debug logging here
        val debugLog = ""

        val intensity = promptMonitor.promptIntensityLevel(promptCount)

        return DeviceDocument(
            LocalDateTime.now().toString(),
            triggerTimestamp,
            type,
            promptMonitor.lastAwakeDateTime.toString(),
            promptMonitor.coolDownEndDateTime.toString(),
            promptMonitor.isInCoolDownPeriod(triggerTimestamp),
            promptMonitor.startPromptAllowPeriod.toString(),
            promptMonitor.isInPromptWindow(triggerTimestamp),
            promptCount,
            intensity,
            allowed,
            debugLog
        )
    }

    private fun updateEventList(eventLabel: String, triggerTimestamp: String) {
        val now = LocalDateTime.parse(triggerTimestamp)

        when (eventLabel) {
            EVENT_LABEL_AWAKE -> promptMonitor.addAwakeEvent(now)

            EVENT_LABEL_PROMPT -> promptMonitor.addPromptEvent(now)
        }
    }

    private fun getSpeakerEnabledReceiver(): BroadcastReceiver {
        //stop playing if headphones disconnected, automatically start playing if connected
        val broadcastReceiver: BroadcastReceiver = (object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
                    Log.d("MainActivity", "bluetooth disconnected")
                    soundPoolManager.stopPlayingAll(binding.playStatus)
                }

                if (intent.action == BluetoothDevice.ACTION_ACL_CONNECTED) {
                    Log.d("MainActivity", "bluetooth connected")
                    if (mBgRawId == -1) {
                        mBgLabel = "Fan"
                        mBgRawId = R.raw.boxfan
                        binding.bgNoiseSpin.setSelection(1)
                    }
                    soundPoolManager.stopPlayingBackground()
                    binding.playStatus.text = "Playing $mBgLabel"
                    soundPoolManager.playBackgroundSound(mBgRawId, 1F, binding.playStatus, 1F)
                }
            }
        })
        return broadcastReceiver
    }

    private fun purgeOldRecords(dateTime: LocalDateTime) {
        // create a scope to access the database from a thread other than the main thread
        val scope = CoroutineScope(Dispatchers.Default)
        scope.launch {
            //Log.d("MainActivity", "dateTimeOfQuery=$dateTime")
            val dao = ReadingDatabase.getInstance(application).readingDao
            dao.deleteOlder(dateTime)
        }
    }

    private fun purgeAllRecords() {
        // create a scope to access the database from a thread other than the main thread
        val scope = CoroutineScope(Dispatchers.Default)
        scope.launch {
            lastEventTimestamp = ""
            //Log.d("MainActivity", "delete all records")
            val dao = ReadingDatabase.getInstance(application).readingDao
            dao.deleteAll()
        }
    }

    override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
        val bgChoice = parent?.getItemAtPosition(position)

        //Log.d("MainActivity", "bgChoice = $bgChoice")

        mBgRawId = if (bgChoice.toString() == "Fan") {
            mBgLabel = "Fan"
            R.raw.boxfan
        } else if (bgChoice.toString() == "AC") {
            mBgLabel = "AC"
            R.raw.ac
        } else if (bgChoice.toString() == "Metal Fan") {
            mBgLabel = "MetalFan"
            R.raw.metal_fan
        } else if (bgChoice.toString() == "Waves") {
            mBgLabel = "Waves"
            R.raw.waves
        } else if (bgChoice.toString() == "Brown") {
            mBgLabel = "Brown"
            R.raw.brown
        } else if (bgChoice.toString() == "Green") {
            mBgLabel = "Green"
            R.raw.green
        } else if (bgChoice.toString() == "Pink") {
            mBgLabel = "Pink"
            R.raw.pink
        } else {
            -1
        }

        //Log.d("MainActivity", "bgChoice = $mBgRawId")
    }

    override fun onNothingSelected(parent: AdapterView<*>?) {
        //do nothing
    }

}
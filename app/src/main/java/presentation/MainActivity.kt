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
import android.speech.tts.TextToSpeech
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
import sound.WILDSoundRoutine.Companion.CLIP_DIR
import sound.WILDSoundRoutine.Companion.FOREGROUND_DIR
import sound.WILDSoundRoutine.Companion.ROOT_DIR
import utils.AppConfig
import utils.FileManager
import utils.PromptMonitor
import viewmodel.DocumentViewModel
import viewmodel.DocumentViewModelFactory
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale


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
        const val EVENT_LABEL_LIGHT = "light_event"
        const val EVENT_LABEL_REM = "rem_event"
        const val EVENT_LABEL_FOLLOW_UP = "follow_up_event"
        const val SLEEP_EVENT_PROMPT_DELAY = 10000L //3000L DEBUG VALUE
        const val RESET_VOL = 9


        const val WILD_FG_DIR = "$ROOT_DIR/$FOREGROUND_DIR"
        const val WILD_CLIP_DIR = "$ROOT_DIR/$CLIP_DIR"

        const val MANUAL_PLAY_MESSAGE = "Manual play"
        const val ACTIVE_EVENT_MESSAGE = "Elevated movement"
        const val REM_EVENT_MESSAGE = "Rem event"
        const val WATCH_EVENT_MESSAGE = "Watch event"

        const val WILD_MESSAGE = "wild"
        const val MILD_MESSAGE = "mild"
        const val SSILD_MESSAGE = "s s i l d"
    }

    //set up the AudioManager and SoundPool
    private lateinit var audioManager: AudioManager
    private lateinit var soundPoolManager: SoundPoolManager
    private lateinit var fileManager: FileManager
    private lateinit var textToSpeech: TextToSpeech

    private var  lastEventTimestamp = ""
    private var lastActiveEventTimestamp: LocalDateTime? = null
    private var apJob: Job? = null

    //monitor event sleep stage estimate for prompts
    private val promptMonitor : PromptMonitor = PromptMonitor()

    private var maxVolume = 0
    private var mBgRawId = -1
    private var mBgLabel = ""

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

        //init the FileManager instance before the SoundManager
        fileManager = FileManager.getInstance(applicationContext)

        textToSpeech = getTextToSpeech()

        setupSound()

        // initialize viewModel
        val dao = ReadingDatabase.getInstance(application).readingDao
        val viewModelFactory = DocumentViewModelFactory(dao)
        viewModel = ViewModelProvider(
            this, viewModelFactory)[DocumentViewModel::class.java]

        this.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT

        // use a broadcast receiver to stop any playback when bluetooth is disconnected

        val filter = IntentFilter().apply {
            addAction(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
        }

        val myNoisyAudioStreamReceiver = getSpeakerEnabledReceiver()

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

                        if(lastEventTimestamp != viewModel.lastTimestamp.value) {
                            val dateFormat = DateTimeFormatter.ofPattern("M/dd/yyyy hh:mm:ss")
                            val displayDate = LocalDateTime.parse(viewModel.lastTimestamp.value)
                                .format(dateFormat)

                            //initialize the lastAwakeTimestamp
                            if (promptMonitor.lastAwakeDateTime == null) {
                                promptMonitor.lastAwakeDateTime = LocalDateTime.parse(viewModel.lastTimestamp.value)
                            }

                            //get any recent high active event and interrupt any prompts if found running
                            handleHighActivityEvent()

                            var reading = viewModel.lastReadingString.value

                            val sleepStage = viewModel.sleepStage.value ?: ""
                            processSleepStageEvents(sleepStage)

                            val eventsDisplay = promptMonitor.getEventsDisplay()
                            if(eventsDisplay.isNotEmpty()) {
                                reading += eventsDisplay
                            }

                            binding.timestampTextview.text = displayDate
                            binding.readingTextview.text = reading
                            binding.sleepStageTexview.text = sleepStage

                            viewModel.eventMap.value?.let { events -> processEvents(events) }
                            lastEventTimestamp = viewModel.lastTimestamp.value.toString()
                        }
                    }
                    // In case of error, show some data to user
                    else -> {
                        Log.e("MainActivity","$it.message")
                        binding.progressBar.isVisible = false
                        Toast.makeText(this@MainActivity, "${it.message}", Toast.LENGTH_SHORT).show()
                    }
                }

            }
        }
    }

    private fun handleHighActivityEvent() {
        if (viewModel.lastActiveEventTimestamp != null && (lastActiveEventTimestamp == null || (
                    viewModel.lastActiveEventTimestamp!! > lastActiveEventTimestamp))
        ) {

            lastActiveEventTimestamp = viewModel.lastActiveEventTimestamp
            val hour = LocalDateTime.parse(viewModel.lastTimestamp.value).hour
            val hoursAllowed = hour in 22..23 || hour in 0..7
            if (hoursAllowed && !promptMonitor.isInHighActivityPeriod(viewModel.lastTimestamp.value, 3L)) {
                //we'll read out the time for any new possible interrupt periods
                speakTheTime(ACTIVE_EVENT_MESSAGE)
            }

            checkShouldStartInterruptCoolDown()
        }
    }

    private fun checkShouldStartInterruptCoolDown(isStartButton : Boolean = false) {
        val isStartAllCoolDown = promptMonitor.checkInterruptCoolDown(viewModel.lastTimestamp.value, isStartButton)
         if (isStartAllCoolDown && !isStartButton) {
             stopSoundRoutine()
         }
    }

    private fun clearSessionState() {
        promptMonitor.clear()
        fileManager.clearFilesInSession()
    }

    private fun processSleepStageEvents(sleepStage: String) {
        //Log.d("SleepStage", "${viewModel.lastTimestamp.value} stage=$sleepStage lastAwake=${viewModel.lastAwakeTimestamp}")
        when(sleepStage) {

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
                checkAndSubmitFollowUpPromptEvent()
                binding.sleepStageTexview.setTextColor(Color.RED)
            }

            "LIGHT ASLEEP" -> {
                checkAndSubmitLightPromptEvent()
                binding.sleepStageTexview.setTextColor(Color.YELLOW)
            }

            "REM ASLEEP" -> {
                checkAndSubmitREMPromptEvent()
                binding.sleepStageTexview.setTextColor(Color.YELLOW)
            }

            "ASLEEP", "DEEP ASLEEP" -> {
                checkAndSubmitFollowUpPromptEvent()
                binding.sleepStageTexview.setTextColor(Color.BLUE)
            }
        }
    }

    private fun checkAndSubmitAwakePromptEvent() {

        val triggerDateTime = LocalDateTime.parse(viewModel.lastTimestamp.value)
        val hour = triggerDateTime.hour
        val day = triggerDateTime.dayOfWeek
        val limit = if(day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY) 6 else 5

        if (binding.chipAwake.isChecked) {
            val hoursAllowed = hour in 1..limit
            val isAwakeEventAllowed = hoursAllowed && promptMonitor.isAwakeEventAllowed(viewModel.lastTimestamp.value)

            if (hoursAllowed) {
                val document = getDeviceDocument(EVENT_LABEL_AWAKE, isAwakeEventAllowed)
                logEvent(document)
            }

            if (isAwakeEventAllowed) {
                startCountDownPromptTimer(EVENT_LABEL_AWAKE)
            }
        }
    }

    private fun checkAndSubmitLightPromptEvent() {
        val triggerDateTime = LocalDateTime.parse(viewModel.lastTimestamp.value)

        if (binding.chipRem.isChecked) {
            val hoursAllowed = getPromptHoursAllowed(triggerDateTime)

            val isLightPromptEventAllowed = hoursAllowed && promptMonitor.isPromptEventAllowed(viewModel.lastTimestamp.value)

            if (hoursAllowed) {
                val document = getDeviceDocument(EVENT_LABEL_LIGHT, isLightPromptEventAllowed)
                logEvent(document)
            }

            if (isLightPromptEventAllowed) {
                startCountDownPromptTimer(EVENT_LABEL_LIGHT)
            }
        }
    }

    private fun checkAndSubmitREMPromptEvent() {
        val triggerDateTime = LocalDateTime.parse(viewModel.lastTimestamp.value)

        if (binding.chipRem.isChecked) {
            val hoursAllowed = getPromptHoursAllowed(triggerDateTime)

            val isREMPromptEventAllowed = hoursAllowed && promptMonitor.isPromptEventAllowed(viewModel.lastTimestamp.value)

            if (hoursAllowed) {
                val document = getDeviceDocument(EVENT_LABEL_REM, isREMPromptEventAllowed)
                logEvent(document)
            }

            if (isREMPromptEventAllowed) {
                startCountDownPromptTimer(EVENT_LABEL_REM)
            }

            //each rem trigger event is evaluated to possibly set next allowed prompt window
            promptMonitor.checkRemTriggerEvent(viewModel.lastTimestamp.value)
        }
    }

    private fun getPromptHoursAllowed(triggerDateTime: LocalDateTime): Boolean {
        val hour = triggerDateTime.hour
        val day = triggerDateTime.dayOfWeek
        val limit = if(day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY) 6 else 5

        return (hour in 1..3 && promptMonitor.isAwakeEventBeforePeriod(viewModel.lastTimestamp.value, 20)) ||
                (hour in 4..limit && promptMonitor.isAwakeEventBeforePeriod(viewModel.lastTimestamp.value, 40))
    }

    private fun checkAndSubmitFollowUpPromptEvent() {
        val triggerDateTime = LocalDateTime.parse(viewModel.lastTimestamp.value)

        if (binding.chipRem.isChecked) {
            val isFollowUpPromptEventNeeded = promptMonitor.isFollowUpEventAllowed(viewModel.lastTimestamp.value)

            if (isFollowUpPromptEventNeeded) {
                val document = getDeviceDocument(EVENT_LABEL_FOLLOW_UP, true)
                logEvent(document)

                startCountDownPromptTimer(EVENT_LABEL_FOLLOW_UP)
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

        soundPoolManager = SoundPoolManager.getInstance(application)

        val bgSelector = binding.bgNoiseSpin
        bgSelector.onItemSelectedListener = this

        binding.btnNoise.setOnClickListener {
            if (mBgRawId != -1) {
                soundPoolManager.stopPlayingBackground()
                binding.playStatus.text = "Playing $mBgLabel"
                soundPoolManager.playBackgroundSound(mBgRawId, 1F, binding.playStatus)
            } else {
                val text = "You need to choose a white noise selection"
                Toast.makeText(application, text, Toast.LENGTH_LONG).show()
            }
        }

        binding.btnPrompt.setOnClickListener {
            if(binding.chipGroup.checkedChipId == View.NO_ID ) {
                val text = "You need to choose a sound routine"
                Toast.makeText(application, text, Toast.LENGTH_LONG).show()
            } else {
                playPrompts(EVENT_LABEL_BUTTON)
            }
        }

        binding.btnStop.setOnClickListener {
            soundPoolManager.stopPlayingAll(binding.playStatus)
        }

        binding.btnReset.setOnClickListener {
            confirmAndReset()
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
                override fun onProgressChanged(seekBar: SeekBar,
                                               progress: Int, fromUser: Boolean) {
                    //Log.d("MainActivity", "volume=" + progress)
                    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC,progress,0)
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
            binding.chipLow.isChecked = true
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC,RESET_VOL,0)
        }


        val podCount = fileManager.getFilesFromDirectory(PodSoundRoutine.ROOT_DIR+"/"+PodSoundRoutine.POD_DIR).size
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

    private fun confirmAndReset() {
        val builder = AlertDialog.Builder(this)

        val fgSize = fileManager.getUsedFilesFromDirectory(WILD_FG_DIR).size
        val clipSize = fileManager.getUsedFilesFromDirectory(WILD_CLIP_DIR).size

        builder.setMessage("Are you sure you want to reset used files ($fgSize fgs, $clipSize clips?)")
            .setCancelable(false)
            .setPositiveButton("Yes") { dialog, id ->
                fileManager.resetFilesUsed(
                    "$ROOT_DIR/$FOREGROUND_DIR",
                    "$ROOT_DIR/$CLIP_DIR"
                )
            }
            .setNegativeButton("No") { dialog, id ->
                dialog.dismiss()
            }
        val alert = builder.create()
        alert.show()
    }

    private fun playPrompts(eventLabel : String, hour: Int = -1) {
        val soundList = mutableListOf<String>()
        var pMod = ""
        var pType = ""
        var pMessage = ""

        var playCount = 1
        val triggerDateTime = LocalDateTime.parse(viewModel.lastTimestamp.value)

        if (binding.chipSsild.isChecked) {
            pType = "s"
            pMessage = SSILD_MESSAGE
        }
        if (binding.chipMild.isChecked) {
            pType = "m"
            pMessage = MILD_MESSAGE
        }
        if (binding.chipWild.isChecked) {
            pType = "w"
            pMessage = WILD_MESSAGE
        }

        val promptCount = promptMonitor.getPromptCountInPeriod(LocalDateTime.parse(viewModel.lastTimestamp.value))

        if(eventLabel == EVENT_LABEL_LIGHT || eventLabel == EVENT_LABEL_REM || eventLabel == EVENT_LABEL_FOLLOW_UP) {
            pMod = "p"

            playCount = if(promptCount == 1) {
                val promptMessage = "first $REM_EVENT_MESSAGE"
                speakTheTime(promptMessage, pMessage, 0.4F)
                when (hour) {
                    6,7,8,9 -> 2
                    else -> 1
                }
            } else {
                when (hour) {
                    6, 7, 8, 9 -> 3
                    else -> 2
                }
            }
        } else {
            //this can either be an auto awake event or an manual button event
            pMod = if(eventLabel == EVENT_LABEL_AWAKE) "a" else ""
            if(pMod.isEmpty()) {
                //it's a manual event from watch so update the event list and read the time out
                updateEventList(EVENT_LABEL_AWAKE, triggerDateTime.toString())
                speakTheTime(MANUAL_PLAY_MESSAGE, pMessage)
            }
        }

        soundList.add("$pType$pMod")

        //only allow this option via the prompt button
        if (eventLabel == EVENT_LABEL_BUTTON && binding.chipPod.isChecked) {
            if(binding.chipPod1.isChecked) {
                playCount = 1
            } else if(binding.chipPod2.isChecked) {
                playCount = 2
            } else if(binding.chipPod3.isChecked) {
                playCount = 3
            } else if(binding.chipPod4.isChecked) {
                playCount = 4
            }

            soundList.add("p")
        }

        //Log.d("MainActivity", "setting soundlist to=$soundList")
        val intensityLevel = promptMonitor.promptIntensityLevel(viewModel.lastTimestamp.value, promptCount)

        soundPoolManager.playSoundList(
            soundList, mBgRawId, mBgLabel, eventLabel, binding.playStatus, playCount, intensityLevel, promptCount)
    }

    private fun processEvents(eventMap: Map<String, String>) {
        val triggerDateTime = LocalDateTime.parse(viewModel.lastTimestamp.value)

        var soundList : MutableList<String> = emptyList<String>().toMutableList()
        var playCount = 1

        if(eventMap.containsKey(POD_EVENT) && (eventMap[POD_EVENT] != null)) {
            updateEventList(EVENT_LABEL_AWAKE, triggerDateTime.toString())
            CoroutineScope(Dispatchers.Default).launch {
                deviceDocumentRepository.postDevicePrompt("appdata", getDeviceDocument(EVENT_LABEL_AWAKE, true))
                //Log.d("MainActivity", "sending awake event to device repository for podcast event")
            }

            val podNumber = eventMap[POD_EVENT]!!.toInt()
            playCount = podNumber
            soundList.add("p")
        }  else if(eventMap.containsKey(PLAY_EVENT) && (eventMap[PLAY_EVENT] != null)) {
            updateEventList(EVENT_LABEL_AWAKE, triggerDateTime.toString())
            CoroutineScope(Dispatchers.Default).launch {
                deviceDocumentRepository.postDevicePrompt("appdata", getDeviceDocument(EVENT_LABEL_AWAKE, true))
                //Log.d("MainActivity", "sending awake event to device repository for play event")
            }

            soundList = eventMap[PLAY_EVENT]!!.split(",").toMutableList()

            var promptMessage = MILD_MESSAGE
            if(soundList.contains("s")) {
                promptMessage = SSILD_MESSAGE
            } else if(soundList.contains("w")) {
                promptMessage = WILD_MESSAGE
            }

            speakTheTime(WATCH_EVENT_MESSAGE, promptMessage)

        } else if (eventMap.containsKey(SLEEP_EVENT)) {
            //Log.d("PromptMonitor", "sleep event ${LocalDateTime.parse(viewModel.lastTimestamp.value)}")
            //stop any more prompts for a period of time
            lastActiveEventTimestamp = LocalDateTime.parse(viewModel.lastTimestamp.value)

            cancelStartCountDownPrompt(SLEEP_EVENT)
            checkShouldStartInterruptCoolDown(true)
        }

        if(soundList.isNotEmpty()) {
            soundPoolManager.stopPlayingForeground()
            soundPoolManager.stopPlayingBackground()
            soundPoolManager.playSoundList(
                soundList, mBgRawId, mBgLabel, EVENT_LABEL_WATCH, binding.playStatus, playCount)
        }
    }

    private fun speakTheTime(eventMessage : String, promptMessage: String = "", volume: Float = 0.6F) {
        val currentTime = LocalTime.now().format(DateTimeFormatter.ofPattern("hh:mm a", Locale.ENGLISH))
        val commenceMessage = if(promptMessage.isNotEmpty()) "Commencing $promptMessage soon." else ""
        val fullMessage = "$eventMessage detected. $commenceMessage The time is $currentTime"
        Log.d("SpeakTheTime", "${viewModel.lastTimestamp.value} tts $fullMessage")
        val params = Bundle()
        params.putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, volume);

        textToSpeech.speak(fullMessage, TextToSpeech.QUEUE_FLUSH, params, null)
    }

    private fun stopSoundRoutine() {
        if (mBgRawId != -1) {
            soundPoolManager.stopPlayingBackground()
            binding.playStatus.text = "Playing $mBgLabel"
            soundPoolManager.playBackgroundSound(mBgRawId, 1F, binding.playStatus)
        }
    }

    private fun startCountDownPromptTimer(eventLabel : String) {
        //avoid stepping on a waiting or running job
        val isRunning = promptMonitor.promptEventWaiting != null

        if(isRunning) {
            Log.d("MainActivity", "returning, promptEventWaiting = $promptMonitor.promptEventWaiting")
            return
        }

        val scope = CoroutineScope(Dispatchers.Default)

        if (apJob == null || apJob!!.isCompleted) {
            apJob = scope.launch {
                promptMonitor.promptEventWaiting = eventLabel
                val triggerDateTime = LocalDateTime.parse(viewModel.lastTimestamp.value)
                val hour = triggerDateTime.hour

                //capture in event list in the event list
                updateEventList(eventLabel, triggerDateTime.toString())

                //send a vibration event to the watch
                deviceDocumentRepository.postDevicePrompt("appdata",
                    getDeviceDocument(eventLabel, true))

                if(eventLabel != EVENT_LABEL_AWAKE) {
                    //give the watch a little time to pick up the vibration event
                    yield()
                    delay(timeMillis = SLEEP_EVENT_PROMPT_DELAY)
                }

                playPrompts(eventLabel, hour)

                delay(timeMillis = 10000)

                //Log.d("MainActivity", "set promptEventWaiting to null")
                promptMonitor.promptEventWaiting = null
            }
        }
    }

    private fun cancelStartCountDownPrompt(eventLabel: String) {
        //Log.d("MainActivity", "stopping auto prompt from $eventLabel " +
        //        "${viewModel.lastTimestamp.value} promptEventWaiting = $promptEventWaiting")

        promptMonitor.promptEventWaiting = null

        val document = getDeviceDocument("cancel from: $eventLabel", false )
        logEvent(document)

        if(apJob != null && apJob!!.isActive) {
            apJob!!.cancel()
        }

        if(mBgLabel.isNotEmpty()) {
            binding.playStatus.text = "Playing $mBgLabel"
        } else {
            binding.playStatus.text = ""
        }

        soundPoolManager.stopPlayingForeground()
        soundPoolManager.stopPlayingAltBackground()
    }

    private fun getDeviceDocument(type: String, allowed: Boolean) : DeviceDocument {
        val triggerTimestamp =
            if (viewModel.lastTimestamp.value != null) viewModel.lastTimestamp.value!! else ""

        val lastPromptTimestamp = if(promptMonitor.allPromptEvents.isEmpty()) ""
            else promptMonitor.allPromptEvents.last().toString()

        //add any additional debug logging here
        val debugLog = ""

        val promptCount = promptMonitor.getPromptCountInPeriod(LocalDateTime.parse(viewModel.lastTimestamp.value))
        val intensity = promptMonitor.promptIntensityLevel(viewModel.lastTimestamp.value, promptCount)

        return  DeviceDocument(
            LocalDateTime.now().toString(),
            triggerTimestamp,
            type,
            promptMonitor.lastAwakeDateTime.toString(),
            lastPromptTimestamp,
            promptMonitor.coolDownEndDateTime.toString(),
            promptMonitor.isInCoolDownPeriod(triggerTimestamp),
            promptMonitor.startPromptAllowPeriod.toString(),
            promptMonitor.isInPromptWindow(triggerTimestamp),
            intensity,
            allowed,
            fileManager.getUsedFilesFromDirectory(WILD_FG_DIR).size,
            fileManager.getUsedFilesFromDirectory(WILD_CLIP_DIR).size,
            debugLog
        )
    }

    private fun updateEventList(eventLabel: String, triggerTimestamp: String) {
        val now = LocalDateTime.parse(triggerTimestamp)

        when(eventLabel) {
            EVENT_LABEL_AWAKE -> promptMonitor.addAwakeEvent(now)

            EVENT_LABEL_LIGHT -> promptMonitor.addLightEvent(now)

            EVENT_LABEL_REM -> promptMonitor.addRemEvent(now)

            EVENT_LABEL_FOLLOW_UP -> promptMonitor.addFollowUpEvent(now)
        }
    }

    private fun getSpeakerEnabledReceiver(): BroadcastReceiver {
        //stop playing if headphones disconnected, automatically start playing if connected
        val broadcastReceiver: BroadcastReceiver = (object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
                    soundPoolManager.stopPlayingAll(binding.playStatus)
                }

                if(intent.action == BluetoothDevice.ACTION_ACL_CONNECTED) {
                    if (mBgRawId == -1) {
                        mBgLabel = "Fan"
                        mBgRawId = R.raw.boxfan
                        binding.bgNoiseSpin.setSelection(1)
                    }
                    soundPoolManager.stopPlayingBackground()
                    binding.playStatus.text = "Playing $mBgLabel"
                    soundPoolManager.playBackgroundSound(mBgRawId, 1F, binding.playStatus)
                }
            }
        })
        return broadcastReceiver
    }

    private fun getTextToSpeech() = TextToSpeech(applicationContext) { i ->
        // if No error is found then only it will run
        if (i != TextToSpeech.ERROR) {
            // To Choose language of speech
            textToSpeech.language = Locale.UK
        }
    }

    private fun purgeOldRecords(dateTime : LocalDateTime) {
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
        } else if  (bgChoice.toString() == "AC") {
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
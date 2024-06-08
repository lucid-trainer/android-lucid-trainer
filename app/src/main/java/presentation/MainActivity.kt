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
import sound.WILDSoundRoutine.Companion.CLIP_DIR
import sound.WILDSoundRoutine.Companion.FOREGROUND_DIR
import sound.WILDSoundRoutine.Companion.ROOT_DIR
import utils.AppConfig
import utils.FileManager
import utils.PromptMonitor
import viewmodel.DocumentViewModel
import viewmodel.DocumentViewModelFactory
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
        const val DREAM_EVENT = "dream"
        const val EVENT_LABEL_BUTTON = "button_press"
        const val EVENT_LABEL_WATCH = "watch_event"
        const val EVENT_LABEL_AWAKE = "awake_event"
        const val EVENT_LABEL_ASLEEP = "asleep_event"
        const val EVENT_LABEL_LIGHT = "light_event"
        const val EVENT_LABEL_REM = "rem_event"
        const val EVENT_LABEL_FOLLOW_UP = "follow_up_event"
        const val SLEEP_EVENT_PROMPT_DELAY = 30000L //3000L DEBUG VALUE

        const val WILD_FG_DIR = "$ROOT_DIR/$FOREGROUND_DIR"
        const val WILD_CLIP_DIR = "$ROOT_DIR/$CLIP_DIR"
    }

    //set up the AudioManager and SoundPool
    private lateinit var audioManager: AudioManager
    private lateinit var soundPoolManager: SoundPoolManager
    private lateinit var fileManager: FileManager
    private var  lastEventTimestamp = ""
    private var apJob: Job? = null
    private var isBTDisconnected: Boolean = false

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

        setupSound()

        // initialize viewModel
        val dao = ReadingDatabase.getInstance(application).readingDao
        val viewModelFactory = DocumentViewModelFactory(dao)
        viewModel = ViewModelProvider(
            this, viewModelFactory)[DocumentViewModel::class.java]

        this.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT

        // use a broadcast receiver to stop any playback when bluetooth is disconnected
        val broadcastReceiver: BroadcastReceiver = getBroadcastReceiver()

        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
        }
        this.registerReceiver(broadcastReceiver, filter)

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

    private fun clearSessionState() {
        promptMonitor.clear()
        fileManager.clearFilesInSession()
    }

    private fun processSleepStageEvents(sleepStage: String) {

        //stop a prompt/podcast if running too long
        if(sleepStage.contains("ASLEEP") && promptMonitor.isStopPromptWindow(viewModel.lastTimestamp.value)) {
            cancelStartCountDownPrompt(EVENT_LABEL_ASLEEP)
            promptMonitor.stopPromptWindow = LocalDateTime.parse(viewModel.lastTimestamp.value)
        }

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
        val minute = triggerDateTime.minute

        if (binding.chipAwake.isChecked) {
            val hoursAllowed = (hour == 4 && minute >= 30) || (hour == 5 && minute <= 30)

            val isAwakeEventAllowed = hoursAllowed && !soundPoolManager.isWildRoutineRunning()
                    && promptMonitor.isAwakeEventAllowed(viewModel.lastTimestamp.value)

            if (hoursAllowed) {
                val document = getDeviceDocument(EVENT_LABEL_AWAKE, isAwakeEventAllowed)
                logEvent(document)
            }

            if (isAwakeEventAllowed) {
                startCountDownPromptTimer(EVENT_LABEL_AWAKE)
                promptMonitor.stopPromptWindow =
                    LocalDateTime.parse(viewModel.lastTimestamp.value).plusMinutes(15)
            }
        }
    }

    private fun checkAndSubmitLightPromptEvent() {
        val triggerDateTime = LocalDateTime.parse(viewModel.lastTimestamp.value)
        val hour = triggerDateTime.hour
        val minute = triggerDateTime.minute

        if (binding.chipLight.isChecked) {
            val hoursAllowed =  (hour == 1 && minute > 29) || hour in 1..7

            val isLightPromptEventAllowed = hoursAllowed &&
                    promptMonitor.isLightEventAllowed(viewModel.lastTimestamp.value)

            if (hoursAllowed) {
                val intensityLevel = promptMonitor.promptIntensityLevel(viewModel.lastTimestamp.value)
                val document = getDeviceDocument(EVENT_LABEL_LIGHT, isLightPromptEventAllowed, intensityLevel)
                logEvent(document)
            }

            if (isLightPromptEventAllowed) {
                //we don't want it to stop the light/rem sleep prompt if stage switches back to ASLEEP
                promptMonitor.stopPromptWindow = LocalDateTime.parse(viewModel.lastTimestamp.value)
                startCountDownPromptTimer(EVENT_LABEL_LIGHT)
            }
        }
    }

    private fun checkAndSubmitREMPromptEvent() {
        val triggerDateTime = LocalDateTime.parse(viewModel.lastTimestamp.value)
        val hour = triggerDateTime.hour

        if (binding.chipRem.isChecked) {
            val hoursAllowed = hour in 1..8

            val isREMPromptEventAllowed = hoursAllowed &&
                    promptMonitor.isRemEventAllowed(viewModel.lastTimestamp.value)

            if (hoursAllowed) {
                val intensityLevel = promptMonitor.promptIntensityLevel(viewModel.lastTimestamp.value)
                val document = getDeviceDocument(EVENT_LABEL_REM, isREMPromptEventAllowed, intensityLevel)
                logEvent(document)
            }

            if (isREMPromptEventAllowed) {
                //we don't want it to stop the light/rem sleep prompt if stage switches back to ASLEEP
                promptMonitor.stopPromptWindow = LocalDateTime.parse(viewModel.lastTimestamp.value)
                startCountDownPromptTimer(EVENT_LABEL_REM)
            }
        }
    }

    private fun checkAndSubmitFollowUpPromptEvent() {
        val triggerDateTime = LocalDateTime.parse(viewModel.lastTimestamp.value)

        if (binding.chipLight.isChecked || binding.chipRem.isChecked) {
            val isFollowUpPromptEventNeeded = !soundPoolManager.isWildRoutineRunning() &&
                    promptMonitor.isFollowUpEventAllowed(viewModel.lastTimestamp.value)

            val intensityLevel = promptMonitor.promptIntensityLevel(viewModel.lastTimestamp.value)

            if (isFollowUpPromptEventNeeded) {
                val document = getDeviceDocument(EVENT_LABEL_FOLLOW_UP, true, intensityLevel)
                logEvent(document)

                //we don't want it to stop the follow-up sleep prompt if stage switches back to ASLEEP
                promptMonitor.stopPromptWindow = LocalDateTime.parse(viewModel.lastTimestamp.value)
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

    private fun playPrompts(eventLabel : String, hour: Int = -1, intensityLevel: Int = SoundPoolManager.DEFAULT_INTENSITY_LEVEL) {
        val soundList = mutableListOf<String>()
        var pMod = ""

        if(eventLabel == EVENT_LABEL_LIGHT || eventLabel == EVENT_LABEL_REM || eventLabel == EVENT_LABEL_FOLLOW_UP) {
            pMod = "p"
            promptMonitor.stopPromptWindow = LocalDateTime.parse(viewModel.lastTimestamp.value)
        } else {
            //assume we'll only play for 20 minutes max unless an asleep event occurs sooner
            promptMonitor.stopPromptWindow = LocalDateTime.parse(viewModel.lastTimestamp.value) .plusMinutes(20)
        }

        if (binding.chipSsild.isChecked) {
            soundList.add("s$pMod")
        }
        if (binding.chipMild.isChecked) {
            soundList.add("m$pMod")
        }
        if (binding.chipWild.isChecked) {
            soundList.add("w$pMod")
        }

        var playCount = getPlayCount(hour)

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

        Log.d("MainActivity", "setting soundlist to=$soundList")

        soundPoolManager.playSoundList(
            soundList, mBgRawId, mBgLabel, eventLabel, binding.playStatus, playCount, intensityLevel)
    }

    private fun processEvents(eventMap: Map<String, String>) {
        val triggerDateTime = LocalDateTime.parse(viewModel.lastTimestamp.value)
        val hour = triggerDateTime.hour

        var soundList : MutableList<String> = emptyList<String>().toMutableList()
        var playCount = getPlayCount(hour)

        //assume we'll only play for 20 minutes max unless an asleep event occurs sooner
        val endPromptWindow = LocalDateTime.parse(viewModel.lastTimestamp.value).plusMinutes(20)

        if(eventMap.containsKey(POD_EVENT) && (eventMap[POD_EVENT] != null)) {
            val podNumber = eventMap[POD_EVENT]!!.toInt()
            if(podNumber > 1) {
                playCount = podNumber - 1
                soundList.add("p")
            }
        }  else if(eventMap.containsKey(PLAY_EVENT) && (eventMap[PLAY_EVENT] != null)) {
            updateEventList(EVENT_LABEL_AWAKE, triggerDateTime.toString())

            CoroutineScope(Dispatchers.Default).launch {
                deviceDocumentRepository.postDevicePrompt("appdata", getDeviceDocument(EVENT_LABEL_AWAKE, true))
            }

            soundList = eventMap[PLAY_EVENT]!!.split(",").toMutableList()

            promptMonitor.stopPromptWindow = endPromptWindow

        } else if (eventMap.containsKey(DREAM_EVENT)) {
            Log.d("MainActivity", "stopping pod event")
            cancelStartCountDownPrompt(DREAM_EVENT)
            if (mBgRawId != -1) {
                soundPoolManager.stopPlayingBackground()
                binding.playStatus.text = "Playing $mBgLabel"
                soundPoolManager.playBackgroundSound(mBgRawId, 1F, binding.playStatus)
            }
        }

        if(soundList.isNotEmpty()) {
            soundPoolManager.stopPlayingForeground()
            soundPoolManager.stopPlayingBackground()
            soundPoolManager.playSoundList(
                soundList, mBgRawId, mBgLabel, EVENT_LABEL_WATCH, binding.playStatus, hour, playCount)
        }
    }

    //on auto event set 1 minute window to run prompt in with some randomness
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

                //determines the level of vibration from watch and volume level of sound prompt
                val intensityLevel = promptMonitor.promptIntensityLevel(viewModel.lastTimestamp.value)

                //capture in event list in the event list
                updateEventList(eventLabel, triggerDateTime.toString())

                //send a vibration event to the watch
                deviceDocumentRepository.postDevicePrompt("appdata",
                    getDeviceDocument(eventLabel, true, intensityLevel ))

                if(eventLabel != EVENT_LABEL_AWAKE) {
                    //give the watch a little time to pick up the vibration event
                    yield()
                    delay(timeMillis = SLEEP_EVENT_PROMPT_DELAY)
                }

                //Log.d("MainActivity", "play prompts")
                playPrompts(eventLabel, hour, intensityLevel)

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

        val document = getDeviceDocument("cancel from: $eventLabel", false, 0 )
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

    private fun getDeviceDocument(type: String, allowed: Boolean, intensity: Int = 1) : DeviceDocument {
        val triggerTimestamp =
            if (viewModel.lastTimestamp.value != null) viewModel.lastTimestamp.value!! else ""

        val lastPromptTimestamp = if(promptMonitor.allPromptEvents.isEmpty()) ""
            else promptMonitor.allPromptEvents.last().toString()

        //add any additional debug logging here
        val debugLog = ""

        return  DeviceDocument(
            LocalDateTime.now().toString(),
            triggerTimestamp,
            type,
            promptMonitor.lastAwakeDateTime.toString(),
            lastPromptTimestamp,
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

    private fun getBroadcastReceiver(): BroadcastReceiver {
        val broadcastReceiver: BroadcastReceiver = (object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                val scope = CoroutineScope(Dispatchers.Default)
                when (intent?.action) {
                    BluetoothDevice.ACTION_ACL_CONNECTED -> {
                        isBTDisconnected = false
                        //Log.d("MainActivity","bluetooth connected")
                    }

                    BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                        scope.launch {
                            //Log.d("MainActivity","bluetooth disconnected")
                            isBTDisconnected = true
                            delay(6000)
                            if (isBTDisconnected) {
                                soundPoolManager.stopPlayingAll(binding.playStatus)
                            }
                        }
                    }
                }
            }
        })
        return broadcastReceiver
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

    private fun getPlayCount(hour : Int) : Int {
        if (hour < 4) {
            return 3
        } else if (hour < 6) {
            return 2
        } else if (hour < 9) {
            return 1
        }

        return 4
    }

    override fun onNothingSelected(parent: AdapterView<*>?) {
        //do nothing
    }

}
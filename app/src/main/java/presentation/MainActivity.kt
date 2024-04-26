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
import sound.SoundPoolManager
import utils.AppConfig
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
        const val VOLUME_EVENT = "volume"
        const val PLAY_EVENT = "playsound"
        const val EVENT_LABEL_BUTTON = "button_press"
        const val EVENT_LABEL_WATCH = "watch_event"
        const val EVENT_LABEL_AWAKE = "awake_event"
        const val EVENT_LABEL_ASLEEP = "asleep_event"
        const val EVENT_LABEL_LIGHT = "light_event"
        const val EVENT_LABEL_REM = "rem_event"
        const val EVENT_LABEL_RESTLESS = "restless_event"
        const val SLEEP_EVENT_PROMPT_DELAY = 30000L //3000L DEBUG VALUE
    }

    //set up the AudioManager and SoundPool
    private lateinit var audioManager: AudioManager
    private lateinit var soundPoolManager: SoundPoolManager
    private var  lastEventTimestamp = ""
    private var apJob: Job? = null
    private var isBTDisconnected: Boolean = false

    //monitor event sleep stage estimate for prompts
    private val promptMonitor : PromptMonitor = PromptMonitor()

    var maxVolume = 0;
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

        setupSound()

        // initialize viewModel
        val dao = ReadingDatabase.getInstance(application).readingDao
        val viewModelFactory = DocumentViewModelFactory(dao)
        viewModel = ViewModelProvider(
            this, viewModelFactory)[DocumentViewModel::class.java]

        this.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT

        // stop any playback when bluetooth is disconnected
        val broadcastReceiver : BroadcastReceiver = (object :BroadcastReceiver() {
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
                            if(isBTDisconnected) {
                                soundPoolManager.stopPlayingAll(binding.playStatus)
                            }
                       }
                    }
                }
            }
        })

        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
        }
        this.registerReceiver(broadcastReceiver, filter)

        // clear any old data
        purgeOldRecords(viewModel.startingDateTime)

        // Create the observer which updates the latest reading from the database
        val lastTimestampObserver = Observer<String> { _ -> }
        val lastReadingStringObserver = Observer<String> { _ -> }
        val sleepStageObserver = Observer<String> { _ -> }
        val eventMapObserver = Observer<Map<String, String>> { _ -> }

        // Observe the LiveData, passing in this activity as the LifecycleOwner and the observer.
        viewModel.lastTimestamp.observe(this, lastTimestampObserver)
        viewModel.lastReadingString.observe(this, lastReadingStringObserver)
        viewModel.sleepStage.observe(this, sleepStageObserver)
        viewModel.eventMap.observe(this, eventMapObserver)

        binding.switchcompat.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                promptMonitor.clear()
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

                        if(!lastEventTimestamp.equals(viewModel.lastTimestamp.value)) {
                            val dateFormat = DateTimeFormatter.ofPattern("M/dd/yyyy hh:mm:ss")
                            val displayDate = LocalDateTime.parse(viewModel.lastTimestamp.value)
                                .format(dateFormat)
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

    private fun processSleepStageEvents(sleepStage: String) {
        binding.sleepStageTexview.setTextColor(Color.GREEN)

        val triggerDateTime = LocalDateTime.parse(viewModel.lastTimestamp.value)
        val hour = triggerDateTime.hour

        //stop an auto prompt if more than 12 minutes asleep since active
        if(sleepStage.contains("ASLEEP") && promptMonitor.isStopPromptWindow(viewModel.lastTimestamp.value)) {
            cancelStartCountDownPrompt(EVENT_LABEL_ASLEEP)
            promptMonitor.stopPromptWindow = LocalDateTime.parse(viewModel.lastTimestamp.value)
        }

        when(sleepStage) {
            "DEEP ASLEEP" -> promptMonitor.handleDeepAsleepEvent(viewModel.lastTimestamp.value)

            "AWAKE" -> {
                binding.sleepStageTexview.setTextColor(Color.RED)

                if (promptMonitor.promptEventWaiting != null && promptMonitor.promptEventWaiting != EVENT_LABEL_AWAKE) {
                    //if we have an event other than awake event waiting it's likely part of this general activity so cancel
                    cancelStartCountDownPrompt(EVENT_LABEL_AWAKE)
                }

                promptMonitor.handleAwakeEvent()
                checkAndSubmitAwakePromptEvent()
            }

            "RESTLESS" -> {
                binding.sleepStageTexview.setTextColor(Color.YELLOW)

                if (promptMonitor.promptEventWaiting != null && promptMonitor.promptEventWaiting != EVENT_LABEL_AWAKE) {
                    //if we have a prompt event waiting it's likely part of this general activity so cancel
                    cancelStartCountDownPrompt(EVENT_LABEL_RESTLESS)
                }

                promptMonitor.handleRestlessEvent(viewModel.lastTimestamp.value)
            }

            "ASLEEP" -> {
                promptMonitor.handleAsleepEvent()
            }

            "LIGHT ASLEEP" -> {
                checkAndSubmitLightPromptEvent(hour)
                promptMonitor.handleLightAsleepEvent()
            }

            "REM ASLEEP" -> {
                checkAndSubmitREMPromptEvent(hour)
                promptMonitor.handleRemAsleepEvent()
            }
        }
    }

    private fun checkAndSubmitAwakePromptEvent() {

        val triggerDateTime = LocalDateTime.parse(viewModel.lastTimestamp.value)
        val hour = triggerDateTime.hour
        val minute = triggerDateTime.minute

        if (binding.chipAuto.isChecked) {
            val hoursAllowed = (hour == 4 && minute >= 30) || (hour == 5 && minute <= 30)

            val isAwakeEventAllowed = hoursAllowed &&  promptMonitor.isAwakeEventAllowed(viewModel.lastTimestamp.value)

            if (hoursAllowed) {
                val document = getDeviceDocument(
                    EVENT_LABEL_AWAKE, 0, 0, isAwakeEventAllowed
                )
                logEvent(document)
            }

            if (isAwakeEventAllowed) {
                startCountDownPromptTimer(EVENT_LABEL_AWAKE)
                promptMonitor.stopPromptWindow =
                    LocalDateTime.parse(viewModel.lastTimestamp.value).plusMinutes(15)
            }
        }
    }

    private fun checkAndSubmitLightPromptEvent(hour: Int) {

        if (binding.chipAuto.isChecked) {
            val hoursAllowed = hour in 2..7

            val timeBetweenPrompts = when(hour) {
                2,6,7 -> 30L
                else -> 45L
            }

            val isLightPromptEventAllowed = hoursAllowed
                    && promptMonitor.isLightEventAllowed(viewModel.lastTimestamp.value, timeBetweenPrompts)

            if (hoursAllowed) {
                val document = getDeviceDocument(
                    EVENT_LABEL_LIGHT, 0, 45, isLightPromptEventAllowed
                )
                logEvent(document)
            }

            if (isLightPromptEventAllowed) {
                //we don't want it to stop the light/rem sleep prompt if stage switches back to ASLEEP
                promptMonitor.stopPromptWindow = LocalDateTime.parse(viewModel.lastTimestamp.value)
                startCountDownPromptTimer(EVENT_LABEL_LIGHT)
            }
        }
    }

    private fun checkAndSubmitREMPromptEvent(hour: Int) {

        if (binding.chipAuto.isChecked) {
            val hoursAllowed = hour in 2..7

            val timeBetweenPrompts = when(hour) {
                6,7 -> 20L
                4,5 -> 30L
                else -> 40L
            }

            val isREMPromptEventAllowed = hoursAllowed &&
                    promptMonitor.isRemEventAllowed(viewModel.lastTimestamp.value, timeBetweenPrompts)

            if (hoursAllowed) {
                val document = getDeviceDocument(
                    EVENT_LABEL_REM, 0, 10, isREMPromptEventAllowed
                )
                logEvent(document)
            }

            if (isREMPromptEventAllowed) {
                //we don't want it to stop the light/rem sleep prompt if stage switches back to ASLEEP
                promptMonitor.stopPromptWindow = LocalDateTime.parse(viewModel.lastTimestamp.value)
                startCountDownPromptTimer(EVENT_LABEL_REM)
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

        val bgSelector = binding.bgMusicSpin
        bgSelector.onItemSelectedListener = this

        binding.btnNoise.setOnClickListener {
            if (mBgRawId != -1) {
                soundPoolManager.stopPlayingBackground()
                binding.playStatus.text = "Playing ${mBgLabel}"
                soundPoolManager.playBackgroundSound(mBgRawId, 1F, binding.playStatus)
            } else {
                val text = "You need to choose a white noise selection"
                Toast.makeText(application, text, Toast.LENGTH_LONG).show()
            }
        }

        binding.btnPrompt.setOnClickListener {
            if(!binding.chipSsild.isChecked && !binding.chipMild.isChecked && !binding.chipWild.isChecked) {
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
                    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC,progress,0);
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                //do nothing
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                //do nothing
            }
        })
    }

    private fun playPrompts(eventLabel : String, hour: Int = -1) {
        val soundList = mutableListOf<String>()
        var pMod = ""

        if(eventLabel.equals(EVENT_LABEL_LIGHT) || eventLabel.equals(EVENT_LABEL_REM)) {
            pMod = "p"
            promptMonitor.stopPromptWindow = LocalDateTime.parse(viewModel.lastTimestamp.value)
        } else {
            //assume we'll only play for 15 minutes max unless an asleep event occurs sooner
            promptMonitor.stopPromptWindow = LocalDateTime.parse(viewModel.lastTimestamp.value) .plusMinutes(15)
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

        soundPoolManager.playSoundList(
            soundList, mBgRawId, mBgLabel, eventLabel, binding.playStatus, hour)
    }

    private fun processEvents(eventMap: Map<String, String>) {
        val triggerDateTime = LocalDateTime.parse(viewModel.lastTimestamp.value)
        val hour = triggerDateTime.hour

        if(eventMap.containsKey(VOLUME_EVENT) && (eventMap[VOLUME_EVENT] != null)) {
            val eventVolume = eventMap[VOLUME_EVENT]!!.toInt()
            val newVolume = maxVolume.times(eventVolume).div(10)
            //Log.d("MainActivity", "setting volume from event=$newVolume")
            binding.seekBar.progress = newVolume
        }

        if(eventMap.containsKey(PLAY_EVENT) && (eventMap[PLAY_EVENT] != null)) {
            updateEventList(EVENT_LABEL_AWAKE, triggerDateTime.toString())

            CoroutineScope(Dispatchers.Default).launch {
                deviceDocumentRepository.postDevicePrompt("appdata",
                    getDeviceDocument(EVENT_LABEL_AWAKE, hour, 0,true)
                )
            }

            val soundList = eventMap[PLAY_EVENT]!!.split(",")
            soundPoolManager.stopPlayingForeground()
            soundPoolManager.stopPlayingBackground()
            soundPoolManager.playSoundList(
                soundList, mBgRawId, mBgLabel, EVENT_LABEL_WATCH, binding.playStatus, hour)

            //assume we'll only play for 15 minutes max unless an asleep event occurs sooner
            promptMonitor.stopPromptWindow = LocalDateTime.parse(viewModel.lastTimestamp.value).plusMinutes(15)
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

                for (i in 1..2) {
                    yield()
                    delay(timeMillis = SLEEP_EVENT_PROMPT_DELAY)
                    //Log.d("MainActivity", "waiting to play  $eventLabel")
                }

                //capture in event list in the event list
                updateEventList(eventLabel, triggerDateTime.toString())

                //Log.d("MainActivity", "send vibration event")

                //send a vibration event to the watch
                deviceDocumentRepository.postDevicePrompt("appdata",
                    getDeviceDocument(eventLabel, hour, 0,true ))

                //Log.d("MainActivity", "play prompts")
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

        val document = getDeviceDocument("cancel from: $eventLabel", 0,0,false )
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

    private fun getDeviceDocument(type: String, hour: Int, minutesSinceLastCount: Long,
                                  allowed: Boolean) : DeviceDocument {

        var intensity = 1;
        if(type != EVENT_LABEL_AWAKE) {
            intensity = when (hour) {
                6, 7 -> 2
                else -> 3
            }
        }

        val triggerTimestamp =
            if (viewModel.lastTimestamp.value != null) viewModel.lastTimestamp.value!! else ""

        return  DeviceDocument(
            LocalDateTime.now().toString(),
            triggerTimestamp,
            type,
            intensity,
            promptMonitor.asleepEventCountSinceAwake,
            promptMonitor.deepAsleepEventCountSinceActive,
            minutesSinceLastCount,
            promptMonitor.lastTimestampSinceDeepAsleep.toString(),
            allowed
        )
    }

    private fun updateEventList(eventLabel: String, triggerTimestamp: String) {
        val now = LocalDateTime.parse(triggerTimestamp)

        when(eventLabel) {
            EVENT_LABEL_AWAKE -> promptMonitor.awakeEventList.add(now)
            EVENT_LABEL_LIGHT -> {
                promptMonitor.lightEventList.add(now)
                promptMonitor.allPromptEvents.add(now)
            }
            EVENT_LABEL_REM -> {
                promptMonitor.remEventList.add(now)
                promptMonitor.allPromptEvents.add(now)
            }
        }
    }

    private fun purgeOldRecords(dateTime : LocalDateTime) {
        // create a scope to access the database from a thread other than the main thread
        val scope = CoroutineScope(Dispatchers.Default)
        scope.launch {
            //Log.d("MainActivity", "dateTimeOfQuery=$dateTime")
            val dao = ReadingDatabase.getInstance(application).readingDao
            val cnt = dao.deleteOlder(dateTime)
            //Log.d("MainActivity", "recordsDeleted=$cnt")
        }
    }

    private fun purgeAllRecords() {
        // create a scope to access the database from a thread other than the main thread
        val scope = CoroutineScope(Dispatchers.Default)
        scope.launch {
            lastEventTimestamp = ""
            //Log.d("MainActivity", "delete all records")
            val dao = ReadingDatabase.getInstance(application).readingDao
            val cnt = dao.deleteAll()
            //Log.d("MainActivity", "recordsDeleted=$cnt")
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
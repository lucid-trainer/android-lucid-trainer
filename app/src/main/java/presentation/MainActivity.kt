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
        const val EVENT_LABEL_UNKNOWN = "unknown_event"
        const val SLEEP_EVENT_PROMPT_DELAY = 30000L //3000L DEBUG VALUE
    }

    //set up the AudioManager and SoundPool
    private lateinit var audioManager: AudioManager
    private lateinit var soundPoolManager: SoundPoolManager
    private var  lastEventTimestamp = ""
    private var apJob: Job? = null
    private var isBTDisconnected: Boolean = false

    //manage state for automatic prompts tied to awake event
    private var stopPromptWindow: LocalDateTime? = null
    private var promptEventWaiting: String? = null
    private var awakeEventList: MutableList<LocalDateTime> = emptyList<LocalDateTime>().toMutableList()
    private var lightEventList: MutableList<LocalDateTime> = emptyList<LocalDateTime>().toMutableList()
    private var asleepEventCountSinceAwake = 0
    private var deepAsleepEventCountSinceActive = 0
    private var lastTimestampSinceDeepAsleep: LocalDateTime? = null

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
                        Log.d("MainActivity","bluetooth connected")
                    }

                    BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                        scope.launch {
                            Log.d("MainActivity","bluetooth disconnected")
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
                awakeEventList.clear()
                lightEventList.clear()
                asleepEventCountSinceAwake = 0
                stopPromptWindow = null
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

                        val sleepStage = viewModel.sleepStage.value ?: ""
                        val dateFormat = DateTimeFormatter.ofPattern("M/dd/yyyy hh:mm:ss")
                        val displayDate = LocalDateTime.parse(viewModel.lastTimestamp.value).format(dateFormat)
                        var reading =  viewModel.lastReadingString.value

                        val formatter = DateTimeFormatter.ofPattern("HH:mm:ss")
                        if(awakeEventList.isNotEmpty()) {
                            val formatActiveEvents:List<String> = awakeEventList.map { dateTime -> dateTime.format(formatter)}
                            reading += "Active Events: $formatActiveEvents \n"
                        }

                        if(lightEventList.isNotEmpty()) {
                            val formatLightEvents:List<String> = lightEventList.map {dateTime -> dateTime.format(formatter)}
                            reading += "Light Events: $formatLightEvents \n"
                        }

                        binding.timestampTextview.text = displayDate
                        binding.readingTextview.text = reading
                        binding.sleepStageTexview.text = sleepStage

                        processSleepStageEvents(sleepStage)

                       if(!lastEventTimestamp.equals(viewModel.lastTimestamp.value)) {
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
        val triggerDateTime = LocalDateTime.parse(viewModel.lastTimestamp.value)
        val hour = triggerDateTime.hour

        //check for continuous deep asleep state before checking. If deep state for 20 minutes or so, we don't want to do a
        //prompt too close to it.
        if(sleepStage.contains("DEEP ASLEEP")) {
            if (++deepAsleepEventCountSinceActive > 40) {
                lastTimestampSinceDeepAsleep = LocalDateTime.parse(viewModel.lastTimestamp.value)
            }
        } else {
            deepAsleepEventCountSinceActive = 0
        }

        if (sleepStage.contains("AWAKE")) {
            binding.sleepStageTexview.setTextColor(Color.RED)

            checkAndSubmitAwakePromptEvent(hour)

            asleepEventCountSinceAwake = 0

        } else if (sleepStage.contains("UNKNOWN")) {
            //Unknown is activity level that looks closer to waking then sleep, so set the counter back here to allow prompts more time
            if(asleepEventCountSinceAwake <= 20) {
                asleepEventCountSinceAwake = 10
            }

            if(promptEventWaiting != null && promptEventWaiting == EVENT_LABEL_LIGHT) {
                //if we have a light event waiting it's likely part of this general activity so cancel
                cancelStartCountDownPrompt(EVENT_LABEL_UNKNOWN)
            }
        } else if (sleepStage.contains("ASLEEP")) {
            //stop the auto prompt if more than 15 minutes asleep since active
            if (stopPromptWindow != null && stopPromptWindow!! > LocalDateTime.parse(viewModel.lastTimestamp.value)
                && asleepEventCountSinceAwake > 30) {
                //assume an auto prompt is running and stop it
                cancelStartCountDownPrompt(EVENT_LABEL_ASLEEP)
                stopPromptWindow = LocalDateTime.parse(viewModel.lastTimestamp.value)
            }
            asleepEventCountSinceAwake++
            binding.sleepStageTexview.setTextColor(Color.GREEN)

        } else if (sleepStage.contains("LIGHT")) {
            //ensure that we've had at least one active event, appeared asleep for at least 25 minutes and that we haven't done a prompt in at least 50 minutes
            checkAndSubmitLightPromptEvent(hour)

            //we're treating this as asleep still
            asleepEventCountSinceAwake++
        }
    }

    private fun checkAndSubmitAwakePromptEvent(hour: Int) {

        if (binding.chipAuto.isChecked) {
            val hoursAllowed = hour in 3..5
            //randomize the time allowed between prompts a bit
            val minutesSinceLast = (10..20).shuffled().last().toLong()

            val isActiveEventAllowed =
                hoursAllowed && asleepEventCountSinceAwake > 0 &&
                        (awakeEventList.isEmpty() || LocalDateTime.parse(viewModel.lastTimestamp.value) >= awakeEventList.last()
                            .plusMinutes(60)) &&
                        (lightEventList.isEmpty() || LocalDateTime.parse(viewModel.lastTimestamp.value) >= lightEventList.last()
                            .plusMinutes(minutesSinceLast)) &&
                        (lastTimestampSinceDeepAsleep == null || LocalDateTime.parse(viewModel.lastTimestamp.value) >=
                                lastTimestampSinceDeepAsleep!!.plusMinutes(2))

            if(hoursAllowed && asleepEventCountSinceAwake >= 50) {
                logEvent(EVENT_LABEL_AWAKE, isActiveEventAllowed, minutesSinceLast)
            }

            if (isActiveEventAllowed) {
                Log.d("MainActivity", "starting awake prompt")


                //cancel any events that might be running
                cancelStartCountDownPrompt(EVENT_LABEL_AWAKE)

                startCountDownPromptTimer(EVENT_LABEL_AWAKE)
                stopPromptWindow =
                    LocalDateTime.parse(viewModel.lastTimestamp.value).plusMinutes(15)
            }
        }
    }

    private fun checkAndSubmitLightPromptEvent(hour: Int) {

        if (binding.chipAuto.isChecked) {
            val hoursAllowed = hour in 2..7

            //randomize the time allowed between prompts a bit
            val minutesSinceLast = (20..50).shuffled().last().toLong()

            val isLightPromptEventAllowed = hoursAllowed && asleepEventCountSinceAwake >= 50 &&
                    (awakeEventList.isEmpty() || LocalDateTime.parse(viewModel.lastTimestamp.value) >= awakeEventList.last()
                        .plusMinutes(30)) &&
                    (lightEventList.isEmpty() || LocalDateTime.parse(viewModel.lastTimestamp.value) >= lightEventList.last()
                        .plusMinutes(minutesSinceLast)) &&
                    (lastTimestampSinceDeepAsleep == null || LocalDateTime.parse(viewModel.lastTimestamp.value) >=
                            lastTimestampSinceDeepAsleep!!.plusMinutes(5))

            if (hoursAllowed) {
                logEvent(EVENT_LABEL_LIGHT, isLightPromptEventAllowed, minutesSinceLast)
            }

            if (isLightPromptEventAllowed) {
                //cancel any events that might be running
                cancelStartCountDownPrompt(EVENT_LABEL_LIGHT)

                //we don't want it to stop the light/rem sleep prompt if stage switches back to ASLEEP
                stopPromptWindow = LocalDateTime.parse(viewModel.lastTimestamp.value)

                Log.d("MainActivity", "playing light sleep prompt")
                startCountDownPromptTimer(EVENT_LABEL_LIGHT)
            }
        }
    }

    private fun logEvent(event: String, isPromptEventAllowed: Boolean, minutesSinceLast : Long) {
        val triggerTimestamp =
            if (viewModel.lastTimestamp.value != null) viewModel.lastTimestamp.value!! else ""

        CoroutineScope(Dispatchers.Default).launch {
            deviceDocumentRepository.postDevicePrompt(
                "logdata",
                getDeviceDocument(
                    event, triggerTimestamp, asleepEventCountSinceAwake, minutesSinceLast,
                    lastTimestampSinceDeepAsleep.toString(), isPromptEventAllowed
                )
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
            Log.d("MainActivity", "list size=" + viewModel.workingReadingList.size)
        }

        // Set the maximum volume of the SeekBar to the maximum volume of the MediaPlayer:
        maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        binding.seekBar.max = maxVolume
        Log.d("MainActivity", "max volume=$maxVolume")

        // Set the current volume of the SeekBar to the current volume of the MediaPlayer:

        // Set the current volume of the SeekBar to the current volume of the MediaPlayer:
        val currVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        binding.seekBar.progress = currVolume
        Log.d("MainActivity", "curr volume=$currVolume")

        binding.seekBar.setOnSeekBarChangeListener(object :
            OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar,
                                               progress: Int, fromUser: Boolean) {
                    Log.d("MainActivity", "volume=" + progress)
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

        if(eventLabel.equals(EVENT_LABEL_LIGHT)) {
            pMod = "p"
            stopPromptWindow = LocalDateTime.parse(viewModel.lastTimestamp.value)
        } else {
            //assume we'll only play for 15 minutes max unless an asleep event occurs sooner
            stopPromptWindow = LocalDateTime.parse(viewModel.lastTimestamp.value) .plusMinutes(15)
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
            Log.d("MainActivity", "setting volume from event=$newVolume")
            binding.seekBar.progress = newVolume
        }

        if(eventMap.containsKey(PLAY_EVENT) && (eventMap[PLAY_EVENT] != null)) {
            updateEventList(EVENT_LABEL_AWAKE, triggerDateTime.toString())

            val soundList = eventMap[PLAY_EVENT]!!.split(",")
            soundPoolManager.stopPlayingForeground()
            soundPoolManager.stopPlayingBackground()
            soundPoolManager.playSoundList(
                soundList, mBgRawId, mBgLabel, EVENT_LABEL_WATCH, binding.playStatus, hour)

            CoroutineScope(Dispatchers.Default).launch {
                deviceDocumentRepository.postDevicePrompt("appdata",
                    getDeviceDocument(EVENT_LABEL_AWAKE, triggerDateTime.toString(), asleepEventCountSinceAwake,0,
                        lastTimestampSinceDeepAsleep.toString(), true)
                )
            }

            //assume we'll only play for 15 minutes max unless an asleep event occurs sooner
            stopPromptWindow = LocalDateTime.parse(viewModel.lastTimestamp.value).plusMinutes(15)
        }
    }

    //on auto event set 1 minute window to run prompt in with some randomness
    private fun startCountDownPromptTimer(eventLabel : String) {
        //avoid stepping on a waiting or running job
        val isRunning = promptEventWaiting != null || (binding.playStatus.text.startsWith("Playing Event"))
        Log.d("MainActivity", "promptEventWaiting=$promptEventWaiting and ${binding.playStatus.text}")

        if(isRunning) {
            Log.d("MainActivity", "returning")
            return
        } else {
            Log.d("MainActivity", "autoPlay on")
        }

        val scope = CoroutineScope(Dispatchers.Default)

        if (apJob == null || apJob!!.isCompleted) {
            apJob = scope.launch {
                val triggerDateTime = LocalDateTime.parse(viewModel.lastTimestamp.value)
                val hour = triggerDateTime.hour

                //randomize the prompt start a bit
                var limit = (1..4).shuffled().last()

                if(eventLabel == EVENT_LABEL_LIGHT) {
                    limit = (4..8).shuffled().last()
                }

                //we always want to capture the event when it happens in the event list
                updateEventList(eventLabel, triggerDateTime.toString())

                promptEventWaiting = eventLabel
                for (i in 1..limit) {
                    yield()
                    delay(timeMillis = SLEEP_EVENT_PROMPT_DELAY)
                }

                Log.d("MainActivity", "playing prompt")

                //send a vibration event to the watch
                if(eventLabel == EVENT_LABEL_LIGHT) {
                    deviceDocumentRepository.postDevicePrompt("appdata",
                        getDeviceDocument(EVENT_LABEL_LIGHT, triggerDateTime.toString(), asleepEventCountSinceAwake,0,
                            lastTimestampSinceDeepAsleep.toString(), true ))
                } else {
                    deviceDocumentRepository.postDevicePrompt("appdata",
                        getDeviceDocument(EVENT_LABEL_AWAKE, triggerDateTime.toString(), asleepEventCountSinceAwake,0,
                            lastTimestampSinceDeepAsleep.toString(), true ))
                }

                playPrompts(eventLabel, hour)

                delay(timeMillis = 10000)
                promptEventWaiting = null
            }
        }
    }

    private fun cancelStartCountDownPrompt(eventLabel: String) {
        Log.d("MainActivity", "stopping auto prompt from $eventLabel")
        if(apJob != null && apJob!!.isActive) {
            apJob!!.cancel()
            promptEventWaiting = null
        }

        if(mBgLabel.isNotEmpty()) {
            binding.playStatus.text = "Playing $mBgLabel"
        } else {
            binding.playStatus.text = ""
        }

        soundPoolManager.stopPlayingForeground()
        soundPoolManager.stopPlayingAltBackground()
    }

    private fun getDeviceDocument(type: String, triggerTimestamp: String, asleepEventCount: Int,
                                  minutesSinceLastCount: Long, lastTimestampDeepAsleep: String, allowed: Boolean) : DeviceDocument {
        return  DeviceDocument(
            LocalDateTime.now().toString(),
            triggerTimestamp,
            type,
            asleepEventCountSinceAwake,
            minutesSinceLastCount,
            lastTimestampDeepAsleep,
            allowed
        )
    }

    private fun updateEventList(eventLabel: String, triggerTimestamp: String) {
        val now = LocalDateTime.parse(triggerTimestamp)
        if(eventLabel == EVENT_LABEL_AWAKE) {
            awakeEventList.add(now)
        } else {
            lightEventList.add(now)
        }
    }

    private fun purgeOldRecords(dateTime : LocalDateTime) {
        // create a scope to access the database from a thread other than the main thread
        val scope = CoroutineScope(Dispatchers.Default)
        scope.launch {
            Log.d("MainActivity", "dateTimeOfQuery=$dateTime")
            val dao = ReadingDatabase.getInstance(application).readingDao
            val cnt = dao.deleteOlder(dateTime)
            Log.d("MainActivity", "recordsDeleted=$cnt")
        }
    }

    private fun purgeAllRecords() {
        // create a scope to access the database from a thread other than the main thread
        val scope = CoroutineScope(Dispatchers.Default)
        scope.launch {
            lastEventTimestamp = ""
            Log.d("MainActivity", "delete all records")
            val dao = ReadingDatabase.getInstance(application).readingDao
            val cnt = dao.deleteAll()
            Log.d("MainActivity", "recordsDeleted=$cnt")
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
        } else if (bgChoice.toString() == "Waves") {
            mBgLabel = "Waves"
            R.raw.waves
        } else {
            -1
        }

        //Log.d("MainActivity", "bgChoice = $mBgRawId")
    }

    override fun onNothingSelected(parent: AdapterView<*>?) {
        //do nothing
    }
}
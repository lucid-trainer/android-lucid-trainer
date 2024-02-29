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
import sound.SoundPoolManager
import viewmodel.DocumentViewModel
import viewmodel.DocumentViewModelFactory
import java.time.LocalDateTime
import java.time.ZonedDateTime
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
        const val EVENT_LABEL_LIGHT = "light_event"
    }

    //set up the AudioManager and SoundPool
    private lateinit var audioManager: AudioManager
    private lateinit var soundPoolManager: SoundPoolManager
    private var  lastEventTimestamp = ""
    private var apJob: Job? = null
    private var isBTDisconnected: Boolean = false

    //manage state for automatic prompts tied to awake event
    private var stopPromptWindow: LocalDateTime? = null
    private var isPromptEventWaiting: Boolean = false
    private var awakeEventList: MutableList<LocalDateTime> = emptyList<LocalDateTime>().toMutableList()
    private var lightEventList: MutableList<LocalDateTime> = emptyList<LocalDateTime>().toMutableList()
    private var asleepEventCountSinceActive = 0

    var maxVolume = 0;
    private var mBgRawId = -1
    private var mBgLabel = ""

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
                asleepEventCountSinceActive = 0
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
        if (sleepStage.contains("AWAKE")) {
            binding.sleepStageTexview.setTextColor(Color.RED)

            //ensure that we've been asleep prior to this and haven't run any active events for at least 50 minutes
            val isActiveEventAllowed = asleepEventCountSinceActive > 0 && (awakeEventList.isEmpty() ||
                    LocalDateTime.now() >= awakeEventList.last().plusMinutes(50))

            if (binding.chipAuto.isChecked && isActiveEventAllowed) {
                startCountDownPromptTimer(EVENT_LABEL_AWAKE)
                stopPromptWindow = LocalDateTime.now().plusMinutes(15)
            }

            asleepEventCountSinceActive = 0

        } else if (sleepStage.contains("ASLEEP")) {
            if (stopPromptWindow != null && stopPromptWindow!! > LocalDateTime.now()) {
                //assume an auto prompt is running and stop it
                Log.d("MainActivity", "stopping auto prompt")
                stopPrompts()
            }

            stopPromptWindow = LocalDateTime.now()
            asleepEventCountSinceActive++;
            binding.sleepStageTexview.setTextColor(Color.GREEN)

        } else if (sleepStage.contains("LIGHT")) {
            //ensure that we've had an active event in the last 90 minutes and that we haven't done a prompt in at least 50 minutes
            val isLightPromptEventAllowed =
                (awakeEventList.isNotEmpty() && awakeEventList.last() >= LocalDateTime.now().plusMinutes(-90)) &&
                (lightEventList.isEmpty() || LocalDateTime.now() >= lightEventList.last().plusMinutes(50))

            //if it's been at least 10 minutes since the last active event and we've entered light sleep, run the prompt routine
            if (binding.chipAuto.isChecked && asleepEventCountSinceActive >= 20 && isLightPromptEventAllowed) {
                //just make sure no active prompt is running
                stopPrompts()

                //we don't want it to stop the light/rem sleep prompt if stage switches back to ASLEEP
                stopPromptWindow = LocalDateTime.now()

                Log.d("MainActivity", "playing light sleep prompt")
                startCountDownPromptTimer(EVENT_LABEL_LIGHT)
            }

            //we're treating this as asleep still
            asleepEventCountSinceActive++
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

    private fun stopPrompts() {
        soundPoolManager.stopPlayingForeground()
        soundPoolManager.stopPlayingAltBackground()
        binding.playStatus.text = "Playing ${mBgLabel}"
    }

    private fun playPrompts(eventLabel : String) {
        val soundList = mutableListOf<String>()
        var pMod = ""

        if(eventLabel.equals(EVENT_LABEL_LIGHT)) {
            pMod = "p"
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

        //assume we'll only play for 15 minutes max unless an asleep event occurs sooner
        stopPromptWindow = LocalDateTime.now().plusMinutes(15)

        soundPoolManager.playSoundList(
            soundList, mBgRawId, mBgLabel, eventLabel, binding.playStatus)
    }

    private fun processEvents(eventMap: Map<String, String>) {

        if(eventMap.containsKey(VOLUME_EVENT) && (eventMap[VOLUME_EVENT] != null)) {
            val eventVolume = eventMap[VOLUME_EVENT]!!.toInt()
            val newVolume = maxVolume.times(eventVolume).div(10)
            Log.d("MainActivity", "setting volume from event=$newVolume")
            binding.seekBar.progress = newVolume
        }

        if(eventMap.containsKey(PLAY_EVENT) && (eventMap[PLAY_EVENT] != null)) {
            val soundList = eventMap[PLAY_EVENT]!!.split(",")
            soundPoolManager.stopPlayingForeground()
            soundPoolManager.stopPlayingBackground()
            soundPoolManager.playSoundList(
                soundList, mBgRawId, mBgLabel, EVENT_LABEL_WATCH, binding.playStatus)

            //assume we'll only play for 15 minutes max unless an asleep event occurs sooner
            stopPromptWindow = LocalDateTime.now().plusMinutes(15)
        }
    }

    //on auto event set 1 minute window to run prompt in with some randomness
    private fun startCountDownPromptTimer(eventLabel : String) {
        val dateTime = ZonedDateTime.now(java.time.ZoneId.systemDefault())
        val hour = dateTime.hour

        //avoid stepping on a waiting or running job
        val isRunning = isPromptEventWaiting || (binding.playStatus.text.startsWith("Playing Event"))
        Log.d("MainActivity", "isPromptEventWaiting=$isPromptEventWaiting and ${binding.playStatus.text}")

        //only kick off prompt if a job isn't already running and it's in hours 1 or 3-7
        if(isRunning || hour !in 1..7) {
            Log.d("MainActivity", "returning")
            return
        } else {
            Log.d("MainActivity", "autoPlay on, hour=$hour")
            updateEventList(eventLabel)
        }

        val scope = CoroutineScope(Dispatchers.Default)
        val limit = (1..4).shuffled().last()

        if (apJob == null || apJob!!.isCompleted) {
            apJob = scope.launch {
                isPromptEventWaiting = true
                for (i in 1..limit) {
                    yield()
                    delay(timeMillis = 20000)
                    Log.d("MainActivity", "in loop for prompt play")
                }
                Log.d("MainActivity", "playing prompt")
                playPrompts(eventLabel)

                delay(timeMillis = 10000)
                isPromptEventWaiting = false
            }
        }
    }

    private fun updateEventList(eventLabel: String) {
        if(eventLabel.equals(EVENT_LABEL_AWAKE)) {
            awakeEventList.add(LocalDateTime.now())
        } else {
            lightEventList.add(LocalDateTime.now())
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

        Log.d("MainActivity", "bgChoice = $bgChoice")

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

        Log.d("MainActivity", "bgChoice = $mBgRawId")
    }

    override fun onNothingSelected(parent: AdapterView<*>?) {
        //do nothing
    }
}
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
import android.os.Handler
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
import com.olekdia.androidcommon.extensions.nextInt
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
import java.util.Random


class MainActivity : AppCompatActivity(), AdapterView.OnItemSelectedListener {

    // variable to initialize it later
    private lateinit var viewModel: DocumentViewModel

    // create a view binding variable
    private lateinit var binding: ActivityMainBinding

    companion object {
        const val VOLUME_EVENT = "volume"
        const val PLAY_EVENT = "playsound"
        const val DREAM_EVENT = "dream"
        const val EVENT_LABEL_BUTTON = "button_press"
        const val EVENT_LABEL_WATCH = "watch_event"
        const val EVENT_LABEL_DREAM = "dream_click_event"
    }

    //set up the AudioManager and SoundPool
    private lateinit var audioManager: AudioManager
    private lateinit var soundPoolManager: SoundPoolManager
    private var  lastEventTimestamp = ""
    private var apJob: Job? = null
    private var isBTDisconnected: Boolean = false

    //set up the handler for performing actions outside of a session
    var handler: Handler? = Handler()
    var runnable: Runnable? = null
    var delay = 1500000
    var randomNum: Random = Random()

    var maxVolume = 0;
    private var mBgRawId = -1
    private var mBgLabel = ""

    @SuppressLint("SourceLockedOrientationActivity")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        delay = 1500000 - randomNum.nextInt(1,420000)
        Log.d("MainActivity","delay = $delay")

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
        val broadcastReceiver : BroadcastReceiver = getBroadcastReceiver()

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

                        binding.timestampTextview.text = displayDate
                        binding.readingTextview.text = viewModel.lastReadingString.value
                        binding.sleepStageTexview.text = sleepStage

                        if(sleepStage.contains("AWAKE")) {
                            binding.sleepStageTexview.setTextColor(Color.RED)
                        } else {
                            binding.sleepStageTexview.setTextColor(Color.GREEN)
                        }

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

    private fun getBroadcastReceiver() = (object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val scope = CoroutineScope(Dispatchers.Default)
            when (intent?.action) {
                BluetoothDevice.ACTION_ACL_CONNECTED -> {
                    val currVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                    if (isBTDisconnected && currVolume < 4) {
                        scope.launch {
                            delay(3000)
                            //turn the volume back up
                            binding.seekBar.progress = maxVolume.times(6).div(10)
                            Log.d("MainActivity", "bt connected turning volume back up")
                        }
                    }
                    isBTDisconnected = false
                }

                BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                    scope.launch {
                        Log.d("MainActivity", "bluetooth disconnected")
                        isBTDisconnected = true
                        delay(6000)
                        if (isBTDisconnected) {
                            //turn the volume down
                            binding.seekBar.progress = maxVolume.times(2).div(10)
                            Log.d("MainActivity", "bt disconnected turning volume down")
                        }
                    }
                }
            }
        }
    })

    override fun onResume() {
        handler?.postDelayed(Runnable {
            runnable?.let { handler!!.postDelayed(it, delay.toLong()) }
            val hour = ZonedDateTime.now(java.time.ZoneId.systemDefault()).hour
            val enabled = binding.chipExperimental.isChecked

            if(enabled && (hour in 1..2 || hour in 5..7)) {
                val result = randomNum.nextInt(2)
                if(result == 0 || hour in 6..7) {
                    //turn the volume way down
                    Log.d("MainActivity","Coin flip succeeded $hour turning volume down")
                    binding.seekBar.progress = maxVolume.times(2).div(10)

                    //turn the volume back up in around 1-3 minutes
                    val scope = CoroutineScope(Dispatchers.Default)
                    scope.launch {
                        val onDelay = 180000 - randomNum.nextInt(120000)
                        delay(timeMillis = onDelay.toLong())
                        binding.seekBar.progress = maxVolume.times(6).div(10)
                        Log.d("MainActivity","turning volume back up $onDelay")
                    }
                } else {
                    Log.d("MainActivity","Coin flip failed $hour")
                }
            }

        }.also { runnable = it }, delay.toLong())
        super.onResume()
    }

    override fun onPause() {
        handler?.removeCallbacks(runnable!!) //stop handler when activity not visible
        super.onPause()
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
            if(!binding.chipSsild.isChecked && !binding.chipMild.isChecked) {
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

    private fun playPrompts(eventLabel : String) {
        val soundList = mutableListOf<String>()
        if (binding.chipSsild.isChecked) {
            soundList.add("s")
        }
        if (binding.chipMild.isChecked) {
            soundList.add("m")
        }
        soundPoolManager.stopPlayingForeground()
        soundPoolManager.stopPlayingBackground()
        soundPoolManager.playSoundList(
            soundList, R.raw.waves, mBgRawId, "Waves", mBgLabel, eventLabel, binding.playStatus)
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
                soundList, R.raw.waves, mBgRawId, "Waves", mBgLabel, EVENT_LABEL_WATCH, binding.playStatus)
        } else if (eventMap.containsKey(DREAM_EVENT)
            && binding.chipAuto.isChecked ) {

            val hour = ZonedDateTime.now(java.time.ZoneId.systemDefault()).hour
            if(hour < 6) {
                //automatically kick off prompts if between midnight and 6 am
                Log.d("MainActivity", "autoPlay on, hour=$hour")
                startCountDownAwakeTimer(EVENT_LABEL_DREAM)
            } else {
                Log.d("MainActivity", "autoPlay off, hour=$hour")
            }
        }
    }

    //on auto event set 5 minute window to run prompt in with some randomness
    private fun startCountDownAwakeTimer(eventLabel : String) {
        val scope = CoroutineScope(Dispatchers.Default)
        val limit = (4..10).shuffled().last()

        if (apJob == null || apJob!!.isCompleted) {
            apJob = scope.launch {
                for (i in 1..limit) {
                    yield()
                    delay(timeMillis = 30000)
                }
                playPrompts(eventLabel)
            }
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
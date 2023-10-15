package sound

import android.content.Context
import android.os.Environment
import android.util.Log
import android.widget.TextView
import com.olekdia.soundpool.SoundPoolCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.PrintWriter
import java.time.LocalDateTime
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter


class SoundPoolManager() {

    private lateinit var mSoundPoolCompat: SoundPoolCompat
    private var mBgId = -1;
    private var mFgId = -1;
    private var fgJob: Job? = null
    private var bgJob: Job? = null

    companion object {

        @Volatile
        private var INSTANCE: SoundPoolManager? = null
        var isFGSoundStopped = false
        var isLoaded = false
        var loadingErrorMessage = ""

        const val LOG_ENABLED = false
        const val LOG_FILE_DIR = "lucid-trainer"
        const val LOG_FILE_NAME = "sound-events.txt"

        fun getInstance(context: Context): SoundPoolManager {
            synchronized(this) {
                var instance = INSTANCE
                if (instance == null) {
                    instance = SoundPoolManager()
                    instance.initSoundPool(context)
                    INSTANCE = instance
                }
                return instance
            }
        }
    }

    private fun initSoundPool(context: Context) {
        mSoundPoolCompat = SoundPoolCompat(context.applicationContext, 5, 100000)

        mSoundPoolCompat.setOnLoadCompleteListener(object :
            SoundPoolCompat.OnLoadCompleteListener {
            override fun onLoadComplete(
                soundPool: SoundPoolCompat,
                sampleId: Int,
                isSuccess: Boolean,
                errorMsg: String?
            ) {
                Log.d("load completed soundID", "$sampleId, isSuccess: $isSuccess")
                isLoaded = isSuccess
                if (errorMsg != null) {
                    loadingErrorMessage = errorMsg
                }
            }
        })
        if(LOG_ENABLED) {
            resetLogFile()
        }
    }

    fun stopPlayingBackground() {
        mSoundPoolCompat.stop(mBgId)
        mSoundPoolCompat.unload(mBgId)
        bgJob?.let { cancelSoundPlayer(it) }

        mBgId = -1
    }

    fun stopPlayingForeground() {
        isFGSoundStopped = true
        mSoundPoolCompat.stop(mFgId)
        mSoundPoolCompat.unload(mFgId)
        fgJob?.let { cancelSoundPlayer(it) }

        mFgId = -1
    }

    fun stopPlayingAll(textView: TextView) {
        stopPlayingForeground()
        stopPlayingBackground()
        textView.text = ""
    }

    fun playBackgroundSound(rawResId : Int, volume: Float, textView: TextView) {
        val scope = CoroutineScope(Dispatchers.Default)

        Log.d("MainActivity", "checking bgJob=$bgJob")

        if (bgJob == null || !mSoundPoolCompat.isPlaying(mBgId)) {
            bgJob = scope.launch {
                if (mBgId != -1) {
                    mSoundPoolCompat.stop(mBgId)
                    mSoundPoolCompat.unload(mBgId)
                }

                mBgId = mSoundPoolCompat.load(rawResId)
                Log.d("MainActivity", "starting load for resource=$rawResId")

                isLoaded = false
                var loopCnt = 0
                while (!isLoaded && loopCnt < 3) {
                    Log.d("MainActivity", "waiting on loading spId=$mBgId")
                    loopCnt++
                    delay(timeMillis = 500)
                }

                mSoundPoolCompat.play(mBgId, volume, volume, -1, 1f)
            }
        } else {
            textView.text = ""
        }
    }

    fun playSoundList(soundList : List<String>, bgRawRes : Int, endBgRawRes : Int, bgLabel : String,
                      endBgLabel : String, eventLabel: String, textView : TextView) {

            val playCnt = getPlayCount()

            val soundRoutines = mutableListOf<SoundRoutine>()
            if (soundList.contains("s")) {
                soundRoutines.add(
                    SSILDSoundRoutine(playCnt, bgRawRes, endBgRawRes, eventLabel, bgLabel, endBgLabel))
            }
            if (soundList.contains("m")) {
                soundRoutines.add(
                    MILDSoundRoutine(playCnt, bgRawRes, endBgRawRes, eventLabel, bgLabel, endBgLabel))
            }
            playForegroundSounds(soundRoutines, textView)
    }


    private fun playForegroundSounds(soundRoutines : List<SoundRoutine>,  textView : TextView) {
        val scope = CoroutineScope(Dispatchers.Default)
        isFGSoundStopped = false

        if (fgJob == null || fgJob!!.isCompleted) {
            fgJob = scope.launch {
                var lastBgRawId = -1
                var lastBgLabel = ""
                for(soundRoutine in soundRoutines) {
                    //write events to internal_storage/Documents folder if enabled
                    if(LOG_ENABLED) {
                        writeToLogFile(
                            soundRoutine.eventLabel,
                            soundRoutine.bgLabel,
                            soundRoutine.fgLabel
                        )
                    }
                    //start the background of the sound routine at a lower volume and pause for a bit
                    textView.text = "Playing ${soundRoutine.bgLabel}"
                    playBackgroundSound(soundRoutine.bgRawId, .3F, textView)
                    for (i in 1..5) {
                        yield()
                        delay(timeMillis = 1000)
                    }

                    for (sound in soundRoutine.getRoutine()) {
                        //check if stop button pushed mid play or the sound file id is already initialized
                        if (!isFGSoundStopped) {

                            textView.text = "Playing ${soundRoutine.bgLabel} and ${soundRoutine.fgLabel} " +
                                    "routine for ${soundRoutine.repetition} cycles"

                            //play the sound file - playOnce handles loading and unloading the file
                            Log.d("MainActivity", "playing ${sound.rawResId}")
                            mFgId = mSoundPoolCompat.playOnce(sound.rawResId, .7F, .7F, 1F)

                            //give it a little time to load
                            isLoaded = false
                            var loopCnt = 0
                            while (!isLoaded && loopCnt < 3) {
                                loopCnt++
                                delay(timeMillis = 300)
                            }

                            if (mFgId != -1) {
                                yield()
                                var isPlaying = mSoundPoolCompat.isPlaying(mFgId)
                                while (isPlaying) {
                                    isPlaying = mSoundPoolCompat.isPlaying(mFgId)
                                    delay(timeMillis = 1000)
                                }
                            }

                            for (i in 1..sound.delayAfter) {
                                yield()
                                delay(timeMillis = 1000)
                            }

                            mFgId = -1
                        }
                        lastBgRawId = soundRoutine.endBgRawId
                        lastBgLabel = soundRoutine.endBgLabel
                    }
                    stopPlayingBackground()
                }

                if(lastBgLabel.isEmpty()) {
                    textView.text = ""
                } else {
                    playBackgroundSound(lastBgRawId, 1F, textView)
                    textView.text = "Playing $lastBgLabel"
                }
            }
        }
    }

    private fun cancelSoundPlayer(job : Job) {
        if(job.isActive) {
            job.cancel()
        }
    }

    private fun getPlayCount() : Int {
        val hour = ZonedDateTime.now(java.time.ZoneId.systemDefault()).hour

        if (hour < 4) {
            return 3
        } else if (hour < 6) {
            return 2
        } else if (hour < 9) {
            return 1
        }

        return 4
    }

    private fun resetLogFile() {
        val file = File(Environment.getExternalStoragePublicDirectory(
            Environment.DIRECTORY_DOCUMENTS), LOG_FILE_NAME
        )
        val outputStream = FileOutputStream(file, false)
        val printWriter = PrintWriter(outputStream)
        printWriter.print("") //New line
        printWriter.flush()
        printWriter.close()
    }

    private fun writeToLogFile(
        eventLabel: String,
        bgLabel: String,
        fgLabel: String
    ) {
        val dateTime = LocalDateTime.now().format(
            DateTimeFormatter.ofPattern("yyy-MM-dd'T'HH:mm:ss.SSS"))
        val line = "$dateTime,$eventLabel,$bgLabel,$fgLabel\n"
        try {
            //Log.d("MainActivity", "storage directory = ${Environment.getExternalStoragePublicDirectory(
            //    Environment.DIRECTORY_DOCUMENTS)}")
            val file = File(Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_DOCUMENTS), LOG_FILE_NAME
            )
            val outputStream = FileOutputStream(file, true)
            val printWriter = PrintWriter(outputStream)
            printWriter.print(line) //New line
            printWriter.flush()
            printWriter.close()
        } catch (e: IOException) {
            Log.e("$LOG_FILE_NAME error", e.message!!)
            e.printStackTrace()
        }
    }
}
package sound

import android.content.Context
import android.os.Environment
import android.util.Log
import android.widget.TextView
import com.lucidtrainer.R
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
import kotlin.reflect.typeOf

/*
   Uses library https://gitlab.com/olekdia/common/libraries/sound-pool
 */
class SoundPoolManager() {

    private lateinit var mSoundPoolCompat: SoundPoolCompat
    private var mBgId = -1;
    private var mFgId = -1;
    private var fgJob: Job? = null
    private var bgJob: Job? = null
    private var altBgJob: Job? = null
    private var altBgId = -1

    companion object {

        @Volatile
        private var INSTANCE: SoundPoolManager? = null
        var isFGSoundStopped = false
        var isBGSoundStopped = false
        var isLoadedMap = emptyMap<Int, Boolean>().toMutableMap()
        var loadingErrorMessage = ""

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
        mSoundPoolCompat = SoundPoolCompat(context.applicationContext, 8, 100000)

        mSoundPoolCompat.setOnLoadCompleteListener(object :
            SoundPoolCompat.OnLoadCompleteListener {
            override fun onLoadComplete(
                soundPool: SoundPoolCompat,
                sampleId: Int,
                isSuccess: Boolean,
                errorMsg: String?
            ) {
                Log.d("load completed soundID", "$sampleId, isSuccess: $isSuccess")
                isLoadedMap[sampleId] = isSuccess
                if (errorMsg != null) {
                    loadingErrorMessage = errorMsg
                }
            }
        })
    }

    fun playSoundList(soundList : List<String>, endBgRawRes : Int,
                      endBgLabel : String, eventLabel: String, textView : TextView) {

        val playCnt = getPlayCount()

        //default
        var bgRawRes = R.raw.waves
        var bgLabel = "Event Waves"

        val soundRoutines = mutableListOf<SoundRoutine>()
        if (soundList.contains("s")) {
            soundRoutines.add(
                SSILDSoundRoutine(playCnt, bgRawRes, endBgRawRes, .3F, 0F, .7F, eventLabel, bgLabel, endBgLabel))
        }
        if (soundList.contains("m")) {
            soundRoutines.add(
                MILDSoundRoutine(playCnt, bgRawRes, endBgRawRes,.3F, 0F, .7F,  eventLabel, bgLabel, endBgLabel))
        }
        if (soundList.contains("w") || soundList.contains("wp")) {

            var fgVolume = .35F
            var altBgVolume = .45F

            if(endBgRawRes > 0) {
                //just keep playing the current background
                bgRawRes = endBgRawRes

                //play the sound files a little quieter if not fan background
                if(bgRawRes != R.raw.boxfan) {
                    fgVolume = .3F
                    altBgVolume = .4F
                }
            } else {
                bgRawRes = R.raw.boxfan
            }

            if(soundList.contains("w")) {
                bgLabel = "Event Fan"
                soundRoutines.add(
                    WILDSoundRoutine(playCnt, bgRawRes, endBgRawRes, 1F, altBgVolume, fgVolume, eventLabel, bgLabel, endBgLabel))
            } else {
                //must be prompt routine
                fgVolume = .35F
                altBgVolume = .45F
                soundRoutines.add(
                    WILDPromptSoundRoutine(playCnt, bgRawRes, endBgRawRes, 1F, altBgVolume, fgVolume, eventLabel, bgLabel, endBgLabel))
            }
        }

        stopPlayingForeground()

        playForegroundSounds(soundRoutines, textView)
    }

    fun stopPlayingBackground() {

        stopPlayingAltBackground()

        Log.d("MainActivity","Stopping mBgId $mBgId")
        isBGSoundStopped = true
        mSoundPoolCompat.stop(mBgId)
        mSoundPoolCompat.unload(mBgId)
        mBgId = -1
        bgJob?.let { cancelSoundPlayer(it) }


    }

    fun stopPlayingAltBackground() {
        Log.d("MainActivity","Stopping altBgId $altBgId")
        mSoundPoolCompat.stop(altBgId)
        mSoundPoolCompat.unload(altBgId)
        altBgId = -1
        altBgJob?.let { cancelSoundPlayer(it) }
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

    fun playBackgroundSound(bgRawId: Int, volume: Float, textView: TextView) {
        val scope = CoroutineScope(Dispatchers.Default)
        isBGSoundStopped = false

        Log.d("MainActivity", "checking bgJob=$bgJob")

        if (bgJob == null || !mSoundPoolCompat.isPlaying(mBgId)) {
            bgJob = scope.launch {
                if (mBgId != -1) {
                    mSoundPoolCompat.stop(mBgId)
                    mSoundPoolCompat.unload(mBgId)
                }

                mBgId = mSoundPoolCompat.load(bgRawId)
                Log.d("MainActivity", "starting load for resource=$bgRawId")

                isLoadedMap[mBgId] = false
                var loopCnt = 0
                while (!isLoadedMap[mBgId]!! && loopCnt < 3) {
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

    private suspend fun playAltBackgroundSound(soundRoutine: SoundRoutine, textView: TextView) {
        val scope = CoroutineScope(Dispatchers.Default)

        playBackgroundSound(soundRoutine.bgRawId, soundRoutine.bgVolume, textView)

        //might need to reset the label
        textView.text = "Playing ${soundRoutine.bgLabel}"

        if (altBgJob == null || !mSoundPoolCompat.isPlaying(altBgId)) {
            altBgJob = scope.launch {
                delay(timeMillis = 1000)

                val startSounds = soundRoutine.getStartSounds()
                if(startSounds.isNotEmpty()) {
                    playAltSounds(startSounds, soundRoutine.altBgVolume)
                }

                val altBGSounds = soundRoutine.getAltBGSounds()
                if(altBGSounds.isNotEmpty()) {
                    do {
                        playAltSounds(altBGSounds, soundRoutine.altBgVolume)
                    } while (!isBGSoundStopped)
                }

                Log.d("MainActivity", "finishing altBgJob with altBgId ${altBgId}")
            }
        }
    }

    private suspend fun playAltSounds(
        altFiles: List<String>,
        volume: Float,
        pause: Boolean = true
    ) {
        for (altFile in altFiles) {
            val filePath = getFilePath(altFile)
            Log.d("MainActivity", "playing alt bg filePath $filePath")

            if (filePath != null) {
                Log.d("MainActivity", "starting load for file=$filePath")

                altBgId = mSoundPoolCompat.playOnce(filePath, volume, volume, 1F)
                Log.d("MainActivity", "file loading as id=$altBgId")

                waitForSoundPlayToComplete(altBgId)

                if(pause) {
                    delay(timeMillis = 30000)
                }

                Log.d("MainActivity", "sound completed for id=$altBgId")
            }
        }
    }

    private fun playForegroundSounds(soundRoutines : List<SoundRoutine>,  textView : TextView) {
        val scope = CoroutineScope(Dispatchers.Default)
        isFGSoundStopped = false


        if (fgJob == null || fgJob!!.isCompleted) {
            fgJob = scope.launch {
                var lastBgRawId = -1
                var lastBgLabel = ""
                for(soundRoutine in soundRoutines) {

                    textView.text = "Playing ${soundRoutine.bgLabel}"

                    //stop the background sound if it something other than the one for this routine
                    //val testVal = !soundRoutine.overrideBG() && mSoundPoolCompat.isPlaying(mBgId)
                    //Log.d("MainActivity", "testVal=$testVal ${soundRoutine.overrideBG()}");
                    if(soundRoutine.overrideBG() && mSoundPoolCompat.isPlaying(mBgId)) {
                        Log.d("MainActivity", "override bg and original playing background")
                        stopPlayingBackground()
                    } else {
                        Log.d("MainActivity", "stopping any alt background loop")
                        stopPlayingAltBackground()
                    }

                    //start any alternate background sounds and keep cycling through them until all are stopped
                    if(soundRoutine is WILDSoundRoutine || soundRoutine is WILDPromptSoundRoutine) {
                        playAltBackgroundSound(soundRoutine, textView)
                    } else {
                        playBackgroundSound(soundRoutine.bgRawId, soundRoutine.bgVolume, textView)
                    }

                    //pause for a bit more
                    for (i in 1..5) {
                        yield()
                        delay(timeMillis = 5000)
                    }

                    for (sound in soundRoutine.getRoutine()) {
                        //check if stop button pushed mid play or the sound file id is already initialized
                        if (!isFGSoundStopped) {

                            textView.text = "Playing ${soundRoutine.bgLabel} and ${soundRoutine.fgLabel} " +
                                    "routine for ${soundRoutine.repetition} cycles"

                            //play the sound file - playOnce handles loading and unloading the file
                            Log.d("MainActivity", "playing ${sound.rawResId}")
                            if(sound.filePathId != null) {
                                val filePath = getFilePath(sound.filePathId)
                                Log.d("MainActivity", "playing $filePath")
                                mFgId = mSoundPoolCompat.playOnce(filePath, soundRoutine.fgVolume, soundRoutine.fgVolume, 1F)
                                Log.d("MainActivity", "playing mFgId $mFgId")
                            } else {
                                mFgId = mSoundPoolCompat.playOnce(sound.rawResId, soundRoutine.fgVolume, soundRoutine.fgVolume, 1F)
                                Log.d("MainActivity", "playing mFgId $mFgId")
                            }

                            waitForSoundPlayToComplete(mFgId)

                            for (i in 1..sound.delayAfter) {
                                yield()
                                delay(timeMillis = 1000)
                            }

                            mFgId = -1
                        }
                        lastBgRawId = soundRoutine.endBgRawId
                        lastBgLabel = soundRoutine.endBgLabel
                    }

                    if(soundRoutine.overrideBG()) {
                        Log.d("MainActivity", "routine overrides bg so stop and restore original bg")
                        stopPlayingBackground()
                    } else {
                        Log.d("MainActivity", "just stop any alt backgrounds on the routine")
                        stopPlayingAltBackground()
                        lastBgRawId = -9999
                    }
                }

                if(lastBgLabel.isEmpty()) {
                    textView.text = ""
                } else {
                    if(lastBgRawId != -9999) {
                        playBackgroundSound(lastBgRawId, 1F, textView)
                        textView.text = "Playing $lastBgLabel"
                    } else {
                        textView.text = "Playing $lastBgLabel"
                    }
                }
            }
        }
    }

    private suspend fun waitForSoundPlayToComplete(sndId: Int) {
        //give it a little time to load
        isLoadedMap[sndId]  = false
        var loopCnt = 0
        while (!isLoadedMap[sndId]!! && loopCnt < 3) {
            loopCnt++
            delay(timeMillis = 300)
        }

        if (sndId != -1) {
            yield()
            var isPlaying = mSoundPoolCompat.isPlaying(sndId)
            while (isPlaying) {
                isPlaying = mSoundPoolCompat.isPlaying(sndId)
                delay(timeMillis = 1000)
            }
        }
    }

    private fun getFilePath(fileName : String): String? {
        val ex = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)

        val fileLocation = fileName.split("/")

        val file = File(
            File(ex.path + "/" + fileLocation[0] + "/" + fileLocation[1] + "/"),
               fileLocation[2])

        Log.d("MainActivity", "getting $file")
        Log.d("MainActivity", "file exists " + file.exists())

        var filePath = ""
        return if (file.exists()) {
            filePath = file.path
            Log.d("File Retrieval", "text=$filePath")

            filePath
        } else {
            null
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
}
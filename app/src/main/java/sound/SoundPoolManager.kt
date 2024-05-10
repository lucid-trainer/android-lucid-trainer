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
import utils.FileMonitor
import java.io.File
import java.time.LocalDateTime


/*
   Uses library https://gitlab.com/olekdia/common/libraries/sound-pool
 */
class SoundPoolManager {

    private lateinit var mSoundPoolCompat: SoundPoolCompat
    private var mBgId = -1
    private var mFgId = -1
    private var fgJob: Job? = null
    private var bgJob: Job? = null
    private var altBgJob: Job? = null
    private var altBgId = -1

    companion object {

        const val ADJUST_BG_VOL_FACTOR = .6F
        const val DEFAULT_INTENSITY_LEVEL = 2

        @Volatile
        private var INSTANCE: SoundPoolManager? = null
        var isFGSoundStopped = false
        var isBGSoundStopped = false
        var adjustAltBGVol = false
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
                //Log.d("load completed soundID", "$sampleId, isSuccess: $isSuccess")
                isLoadedMap[sampleId] = isSuccess
                if (errorMsg != null) {
                    loadingErrorMessage = errorMsg
                }
            }
        })
    }

    fun playSoundList(soundList : List<String>, endBgRawRes : Int, endBgLabel : String,
             eventLabel: String, textView : TextView, hour: Int, playCnt: Int, intensityLevel: Int = DEFAULT_INTENSITY_LEVEL) {

        //default
        var bgRawRes = if(endBgRawRes > 0) {
            //just keep playing the current background
            endBgRawRes
        } else {
            R.raw.brown
        }

        val bgLabel = getBackgroundSoundLabel(bgRawRes)

        val soundRoutines = mutableListOf<SoundRoutine>()

        if (soundList.contains("s")) {
            bgRawRes = R.raw.waves
            soundRoutines.add(
                SSILDSoundRoutine(playCnt, bgRawRes, endBgRawRes, .3F, 0F, .7F, eventLabel, "Waves", endBgLabel))
        }
        if (soundList.contains("m")) {
            bgRawRes = R.raw.waves
            soundRoutines.add(
                MILDSoundRoutine(playCnt, bgRawRes, endBgRawRes,.3F, 0F, .7F,  eventLabel, "Waves", endBgLabel))
        }
        if (soundList.contains("w") || soundList.contains("wp")) {
            val isPrompt = soundList.contains("wp")
            val soundRoutine = getWildSoundRoutine(bgRawRes, playCnt, endBgRawRes, eventLabel, bgLabel, endBgLabel, hour, intensityLevel, isPrompt)
            soundRoutines.add(soundRoutine)
        }
        if (soundList.contains("p")) {
            Log.d("MainActivity","adding PodSoundRoutine")

            val soundRoutine = getPodcastSoundRoutine(bgRawRes, playCnt, endBgRawRes, eventLabel, bgLabel, endBgLabel)
            soundRoutines.add(soundRoutine)
        }

        stopPlayingForeground()

        playSoundRoutines(soundRoutines, textView)
    }

    private fun getWildSoundRoutine(bgRawRes: Int, playCnt: Int, endBgRawRes: Int, eventLabel: String, bgLabel: String,
             endBgLabel: String, hour: Int, intensityLevel: Int, isPrompt: Boolean, ) : SoundRoutine {

        val volOffset = when (hour) {
            4, 5 -> 1
            6, 7, 8 -> 2
            else -> 0
        }

        //adjust the volumes based on background sound and time of night
        var (fgVolume, altBgVolume) = when (bgRawRes) {
            R.raw.green, R.raw.pink -> .48F - (volOffset * .035F) to .44F - (volOffset * .035F)
            R.raw.boxfan, R.raw.metal_fan -> .4F - (volOffset * .03F) to .36F - (volOffset * .03F)
            R.raw.ac -> .35F - (volOffset * .03F) to .3F - (volOffset * .03F)
            R.raw.brown, R.raw.waves -> .0925F - (volOffset * .005F) to .0825F - (volOffset * .005F)
            else -> .45F - (volOffset * .04F) to .4F - (volOffset * .04F)
        }

        //adjust the volumes down further if low intensity
        if (intensityLevel == 1) {
            fgVolume *= .8F
            altBgVolume *= .8F
        } else if (intensityLevel == 0) {
            fgVolume *= .5F
            altBgVolume *= .5F
        }

        Log.d("DimVolume", "WILD prompt volumes at $fgVolume and $altBgVolume offset $volOffset intensity $intensityLevel")

        return if (isPrompt) {
            WILDPromptSoundRoutine(playCnt, bgRawRes, endBgRawRes, 1F, altBgVolume, fgVolume, eventLabel, bgLabel, endBgLabel)
        } else {
            WILDSoundRoutine(playCnt, bgRawRes, endBgRawRes, 1F, altBgVolume, fgVolume, eventLabel, bgLabel, endBgLabel)
        }
    }

    private fun getPodcastSoundRoutine(bgRawRes: Int, playCnt: Int, endBgRawRes: Int, eventLabel: String, bgLabel: String,
                                       endBgLabel: String): SoundRoutine {

        val (fgVolume, bgVolume) = when (bgRawRes) {
            R.raw.green, R.raw.pink -> .3F to .075F
            R.raw.boxfan, R.raw.metal_fan, R.raw.ac -> .25F to .1F
            else -> .15F to .2F
        }

        return PodSoundRoutine(playCnt, bgRawRes, endBgRawRes, bgVolume, 0F, fgVolume, eventLabel, bgLabel, endBgLabel)
    }

    fun stopPlayingBackground() {

        stopPlayingAltBackground()

        //Log.d("MainActivity","Stopping mBgId $mBgId")
        isBGSoundStopped = true
        mSoundPoolCompat.stop(mBgId)
        mSoundPoolCompat.unload(mBgId)
        mBgId = -1
        bgJob?.let { cancelSoundPlayer(it) }


    }

    fun stopPlayingAltBackground() {
        //Log.d("MainActivity","Stopping altBgId $altBgId")
        adjustAltBGVol = false
        mSoundPoolCompat.stop(altBgId)
        mSoundPoolCompat.unload(altBgId)
        altBgId = -1
        altBgJob?.let { cancelSoundPlayer(it) }
    }

    fun stopPlayingForeground() {
        isFGSoundStopped = true
        adjustAltBGVol = false
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

        //Log.d("MainActivity", "checking bgJob=$bgJob")

        if (bgJob == null || !mSoundPoolCompat.isPlaying(mBgId)) {
            bgJob = scope.launch {
                if (mBgId != -1) {
                    mSoundPoolCompat.stop(mBgId)
                    mSoundPoolCompat.unload(mBgId)
                }

                mBgId = mSoundPoolCompat.load(bgRawId)
                //Log.d("MainActivity", "starting load for resource=$bgRawId")

                isLoadedMap[mBgId] = false
                var loopCnt = 0
                while (!isLoadedMap[mBgId]!! && loopCnt < 3) {
                    //Log.d("MainActivity", "waiting on loading spId=$mBgId")
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

                val pauseAfterStart = soundRoutine !is WILDPromptSoundRoutine

                val startSounds = soundRoutine.getStartSounds()
                if(startSounds.isNotEmpty()) {
                    playAltSounds(startSounds, soundRoutine.altBgVolume, pauseAfterStart)
                }

                val altBGSounds = soundRoutine.getAltBGSounds()
                if(altBGSounds.isNotEmpty()) {
                    val currVolume = soundRoutine.altBgVolume

                    do {
                        playAltSounds(altBGSounds, currVolume)
                    } while (!isBGSoundStopped)
                }

                //Log.d("MainActivity", "finishing altBgJob with altBgId ${altBgId}")
            }
        }
    }

    private suspend fun playAltSounds(
        altFiles: List<String>,
        volume: Float,
        pause: Boolean = true
    ) {

        var currVolume = volume

        for (altFile in altFiles) {

            val filePath =  FileMonitor.getFilePath(altFile)
            Log.d("MainActivity", "playing alt bg filePath $filePath")

            if (filePath != null) {
                //Log.d("MainActivity", "starting load for file=$filePath")

                //we're playing a main clip so just turn down the files until done
                if(adjustAltBGVol) {
                    currVolume *= ADJUST_BG_VOL_FACTOR
                }

                Log.d("DimVolume", "adjusted AltBg volume to $currVolume")

                altBgId = mSoundPoolCompat.playOnce(filePath, currVolume, currVolume, 1F)
                //Log.d("MainActivity", "file loading as id=$altBgId")

                waitForSoundPlayToComplete(altBgId)

                if(pause) {
                    delay(timeMillis = 30000)
                }

                //Log.d("MainActivity", "sound completed for id=$altBgId")
            }
        }
    }

    private fun playSoundRoutines(soundRoutines : List<SoundRoutine>, textView : TextView) {
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
                        //Log.d("MainActivity", "override bg and original playing background")
                        stopPlayingBackground()
                    } else {
                        //Log.d("MainActivity", "stopping any alt background loop")
                        stopPlayingAltBackground()
                    }

                    //start any alternate background sounds and keep cycling through them until all are stopped
                    if(soundRoutine is WILDSoundRoutine || soundRoutine is WILDPromptSoundRoutine) {
                        playAltBackgroundSound(soundRoutine, textView)
                    } else {
                        playBackgroundSound(soundRoutine.bgRawId, soundRoutine.bgVolume, textView)
                    }

                    //pause for a bit more
                    val pauseLimit =  if(soundRoutine is WILDSoundRoutine) 5 else 1
                    for (i in 1..pauseLimit) {
                        yield()
                        delay(timeMillis = 5000)
                    }

                    var currVolume = soundRoutine.fgVolume
                    var currBgVolume = soundRoutine.bgVolume

                    for (sound in soundRoutine.getRoutine()) {

                        //check for volume adjust value on the clip
                        Log.d("DimVolume", "before FG volume $currVolume BG volume $currBgVolume")
                        if(sound.fileVolAdjust != 0F) {
                            currVolume *= sound.fileVolAdjust
                            currBgVolume *= ADJUST_BG_VOL_FACTOR
                            stopPlayingBackground()
                            playBackgroundSound(soundRoutine.bgRawId, currBgVolume, textView)
                            adjustAltBGVol = true
                            delay(timeMillis = 1000)
                        } else if(currVolume != soundRoutine.fgVolume){
                            //reset to the original volumes
                            currVolume = soundRoutine.fgVolume
                            currBgVolume = soundRoutine.bgVolume
                            stopPlayingBackground()
                            playBackgroundSound(soundRoutine.bgRawId, currBgVolume, textView)
                            adjustAltBGVol = false
                            delay(timeMillis = 1000)
                        }

                        Log.d("DimVolume", "adjusted FG volume to $currVolume BG volume to $currBgVolume")

                        //check if stop button pushed mid play or the sound file id is already initialized
                        if (!isFGSoundStopped) {
                            var filePath = ""

                            //play the sound file - playOnce handles loading and unloading the file
                            //Log.d("MainActivity", "playing ${sound.rawResId}")
                            mFgId = if(sound.filePathId != null) {
                                filePath = FileMonitor.getFilePath(sound.filePathId).toString()
                                Log.d("MainActivity", "playing $filePath")
                                mSoundPoolCompat.playOnce(filePath, currVolume, currVolume, 1F)
                            } else {
                                Log.d("MainActivity", "playing mFgId $mFgId")
                                mSoundPoolCompat.playOnce(sound.rawResId, currVolume, currVolume, 1F)
                            }

                             var playStatus = "Playing ${soundRoutine.bgLabel} and ${soundRoutine.fgLabel} routine"
                             playStatus +=  if(filePath.isNotEmpty()) ", current file ${filePath.substringAfterLast("/")}"
                                else " for ${soundRoutine.playCount} cycles"
                             textView.text = playStatus


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
                        //Log.d("MainActivity", "routine overrides bg so stop and restore original bg")
                        stopPlayingBackground()
                    } else {
                        //Log.d("MainActivity", "just stop any alt backgrounds on the routine")
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

    private fun cancelSoundPlayer(job : Job) {
        if(job.isActive) {
            job.cancel()
        }
    }

    private fun getBackgroundSoundLabel(rawResId : Int): String {
        return when(rawResId) {
            R.raw.green -> "Green"
            R.raw.pink -> "Pink"
            R.raw.brown -> "Brown"
            R.raw.boxfan -> "Fan"
            R.raw.metal_fan -> "Metal Fan"
            R.raw.ac -> "AC"
            else -> "Unknown"
        }
    }
}
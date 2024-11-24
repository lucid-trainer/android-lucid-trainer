package sound

import android.content.Context
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
import utils.FileManager


/*
   Uses library https://gitlab.com/olekdia/common/libraries/sound-pool
 */
class SoundPoolManager {

    private lateinit var mSoundPoolCompat: SoundPoolCompat
    private lateinit var fileManager: FileManager
    private var mBgId = -1
    private var mFgId = -1
    private var fgJob: Job? = null
    private var bgJob: Job? = null
    private var altBgJob: Job? = null
    private var altBgId = -1
    private var allVolAdj = 0.85F

    companion object {

        const val ADJUST_BG_VOL_FACTOR = .55F
        const val ROOT_SOUNDS_DIR = "lt_sounds"
        const val THEMES_DIR = "themes"
        const val AUTO_THEME = "auto_theme"

        @Volatile
        private var INSTANCE: SoundPoolManager? = null

        var runningSoundRoutine: SoundRoutine? = null
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

        fileManager = FileManager.getInstance()!!
    }

    fun setAllVolAdj(allVolAdj : Float) {
        this.allVolAdj = allVolAdj
    }

    fun playSoundList(soundList : List<String>, endBgRawRes : Int, endBgLabel : String, eventLabel: String,
        textView : TextView, playCnt: Int, promptCount : Int = 1) {

        //default
        var bgRawRes = if(endBgRawRes > 0) {
            //just keep playing the current background
            endBgRawRes
        } else {
            R.raw.brown
        }

        val bgLabel = getBackgroundSoundLabel(bgRawRes)

        val soundRoutines = mutableListOf<SoundRoutine>()

        Log.d("MainActivity", "soundList = $soundList")
        for(soundType in soundList) {
            when(soundType) {
                "s" -> {
                    bgRawRes = R.raw.waves
                    soundRoutines.add(
                        SSILDSoundRoutine(playCnt, bgRawRes, endBgRawRes, .3F, 0F, .7F, eventLabel, bgLabel, endBgLabel))
                }

                "p" -> {
                    val soundRoutine = getPodcastSoundRoutine(bgRawRes, playCnt, endBgRawRes, eventLabel, bgLabel, endBgLabel)
                    soundRoutines.add(soundRoutine)
                }

                else -> {
                    if(soundType.isNotEmpty()) {
                        val soundRoutine = getSoundRoutine(bgRawRes, playCnt, endBgRawRes, eventLabel, bgLabel, endBgLabel, soundType, promptCount)
                        soundRoutines.add(soundRoutine)
                    }
                }
            }
        }

        //Log.d("MainActivity", "soundRoutines = $soundRoutines")

        stopPlayingForeground()

        playSoundRoutines(soundRoutines, textView)
    }

    private fun getSoundRoutine(bgRawRes: Int, playCnt: Int, endBgRawRes: Int, eventLabel: String, bgLabel: String,
             endBgLabel: String, type: String, promptCount: Int = 1) : SoundRoutine {

        //set the initial volumes based on background sound
        var (fgVolume, altBgVolume) = when (bgRawRes) {
            R.raw.green, R.raw.pink -> .52F to .48F
            R.raw.boxfan, R.raw.metal_fan -> .37F to .33F
            R.raw.ac -> .35F to .3F
            R.raw.brown, R.raw.waves -> .12F to .1F
            else -> .45F to .5F
        }

        //add the low/mid/high adjustment
        fgVolume *= allVolAdj
        altBgVolume *= allVolAdj

        val manualTheme = fileManager.getAllDirectoriesFromPath("$ROOT_SOUNDS_DIR/$THEMES_DIR").filter {!it.startsWith("auto")}.shuffled().last()

        //get the appropriate sound routine, adjusting volumes further depending on type
        val soundRoutine = when (type) {
            "m" -> {
                fgVolume *= .9F
                altBgVolume *= .9F
                MILDSoundRoutine(2, bgRawRes, endBgRawRes, 1F, altBgVolume, fgVolume, eventLabel, bgLabel, endBgLabel, manualTheme )
            }

            "ma" -> {
                fgVolume *= .6F
                altBgVolume *= .5F

                MILDSoundRoutine(1, bgRawRes, endBgRawRes, 1F, altBgVolume, fgVolume, eventLabel, bgLabel,  endBgLabel, AUTO_THEME)
            }

            "wa" -> {
                fgVolume *= .6F
                altBgVolume *= .5F
                WILDSoundRoutine(1, bgRawRes, endBgRawRes, 1F, altBgVolume, fgVolume, eventLabel, bgLabel, endBgLabel, AUTO_THEME)
            }

            "wp", "mp" -> {
                val fgLabel = if(type == "wp") "WILD" else "MILD"

                PromptSoundRoutine(1, bgRawRes, endBgRawRes, 1F, altBgVolume, fgVolume, eventLabel, bgLabel, endBgLabel, AUTO_THEME, fgLabel, promptCount)
            }

            //default is "w", a manual WILD sound routine
            else -> {
                fgVolume *= .9F
                altBgVolume *= .9F
                WILDSoundRoutine(2, bgRawRes, endBgRawRes, 1F, altBgVolume, fgVolume, eventLabel, bgLabel, endBgLabel, manualTheme)
            }
        }

        return soundRoutine

    }

    private fun getPodcastSoundRoutine(bgRawRes: Int, playCnt: Int, endBgRawRes: Int, eventLabel: String, bgLabel: String,
                                       endBgLabel: String): SoundRoutine {

        val (fgVolume, bgVolume) = when (bgRawRes) {
            R.raw.green, R.raw.pink, R.raw.boxfan, R.raw.metal_fan, R.raw.ac -> .38F to .5F
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

        runningSoundRoutine = null

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

        //adjust the volume a bit based on low/mid/high/none adjustment setting
        //this makes a small adjustment around the less granular standard device volume control
        val adjVol = volume * allVolAdj

        Log.d("MainActivity", "playing bg at vol $adjVol")

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

                mSoundPoolCompat.play(mBgId, adjVol, adjVol, -1, 1f)

            }
        } else {
            textView.text = ""
        }
    }

    private suspend fun playAltBackgroundSound(soundRoutine: SoundRoutine, textView: TextView, volumeAdj: Float = 1.0F) {
        val scope = CoroutineScope(Dispatchers.Default)

        playBackgroundSound(soundRoutine.bgRawId, soundRoutine.bgVolume, textView)

        //might need to reset the label
        textView.text = "Playing ${soundRoutine.bgLabel}"

        if (altBgJob == null || !mSoundPoolCompat.isPlaying(altBgId)) {
            altBgJob = scope.launch {
                delay(timeMillis = 10000)

                val delayBetween = if(soundRoutine is PromptSoundRoutine) 15000L else 30000L

                val startSounds = soundRoutine.getStartSounds()

                //play the start sounds once and not on restart
                if(volumeAdj == 1.0F && startSounds.isNotEmpty()) {
                    playAltSounds(startSounds, soundRoutine.altBgVolume, delayBetween)
                }

                val altBGSounds = soundRoutine.getAltBGSounds()
                if(altBGSounds.isNotEmpty()) {
                    val currVolume = soundRoutine.altBgVolume * volumeAdj

                    do {
                        playAltSounds(altBGSounds, currVolume, delayBetween)
                    } while (!isBGSoundStopped)
                }

                //Log.d("MainActivity", "finishing altBgJob with altBgId ${altBgId}")
            }
        }
    }

    private suspend fun playAltSounds(
        altFiles: List<String>,
        volume: Float,
        delayBetween: Long
    ) {

        var currVolume = volume

        for (altFile in altFiles) {

            val filePath = fileManager.getFilePath(altFile)
            Log.d("MainActivity", "playing alt bg filePath $filePath")

            if (filePath != null) {
                //Log.d("MainActivity", "starting load for file=$filePath")

                //we're playing a main clip so just turn down the files until done
                if(adjustAltBGVol) {
                    currVolume *= ADJUST_BG_VOL_FACTOR
                }

                //Log.d("DimVolume", "adjusted AltBg volume to $currVolume")

                altBgId = mSoundPoolCompat.playOnce(filePath, currVolume, currVolume, 1F)
                //Log.d("MainActivity", "file loading as id=$altBgId")

                waitForSoundPlayToComplete(altBgId)

                //Log.d("MainActivity", "delaying for $delayBetween")
                delay(timeMillis = delayBetween)

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

                    runningSoundRoutine = soundRoutine

                    textView.text = "Playing ${soundRoutine.bgLabel}"

                    Log.d("MainActivity", "playing $soundRoutine.javaClass.name} ${soundRoutine.fgVolume} ${soundRoutine.altBgVolume}");
                    if(soundRoutine.overrideBG() && mSoundPoolCompat.isPlaying(mBgId)) {
                        //Log.d("MainActivity", "override bg and original playing background")
                        stopPlayingBackground()
                    } else {
                        //Log.d("MainActivity", "stopping any alt background loop")
                        stopPlayingAltBackground()
                    }

                    //start any alternate background sounds and keep cycling through them until all are stopped
                    if(soundRoutine is WILDSoundRoutine || soundRoutine is MILDSoundRoutine) {
                        playAltBackgroundSound(soundRoutine, textView)
                    } else {
                        playBackgroundSound(soundRoutine.bgRawId, soundRoutine.bgVolume, textView)
                    }

                    //pause for a bit more
                    val delayCnt = if(soundRoutine is PodSoundRoutine) 1 else 4
                    for (i in 1..delayCnt) {
                        yield()
                        //Log.d("MainActivity", "pausing before playing prompt")
                        delay(timeMillis = 5000)
                    }

                    //start with default volumes set for the entire routine. As the routine plays we can make changes to them based on volume adjustment values
                    //on each sound clip as it occurs in the list. This allows for clips in a routine to be more or less audible over the background white noise
                    //depending on factors like the type of routine, the particular place we are in the routine, the current hour of the night, or other factors
                    var currVolume = soundRoutine.fgVolume
                    var currBgVolume = soundRoutine.bgVolume

                    for (sound in soundRoutine.getRoutine()) {
                        //Check for volume adjust values on the sound. The override value is always used if set, for special logic such as in a prompt routine where
                        //we want to increase the volume for one particular clip as count in a prompt session increases. If no override value, then we might have a clip
                        //with the regular adjust value set. In this case we want to both diminish the background sound and increase the foreground sound. Finally, if
                        //neither adjust value is set but we diminished the background sound in a previous clip, restore the background to normal but play the remaining
                        //foreground clips at a diminished value

                        Log.d("MainActivity", "before FG volume $currVolume BG volume $currBgVolume")

                        if(sound.fileVolAdjustOverride != 0F) {
                            currVolume *= sound.fileVolAdjustOverride
                        } else if(sound.fileVolAdjust != 0F) {
                            if(currBgVolume == soundRoutine.bgVolume) {
                                currVolume *= sound.fileVolAdjust
                                currBgVolume *= ADJUST_BG_VOL_FACTOR
                                stopPlayingBackground() // this will stop the alt background sounds as well to just focus on the fg clip
                                playBackgroundSound(soundRoutine.bgRawId, currBgVolume, textView)
                                adjustAltBGVol = true
                                delay(timeMillis = 1000)
                            }
                        } else if(currBgVolume != soundRoutine.bgVolume){
                            //turn the white noise sound back to normal but play the rest of the clips at diminished volume
                            val volumeAdj = .8F
                            currVolume = soundRoutine.fgVolume * volumeAdj
                            currBgVolume = soundRoutine.bgVolume
                            stopPlayingBackground()
                            playBackgroundSound(soundRoutine.bgRawId, currBgVolume, textView)
                            if(soundRoutine is WILDSoundRoutine || soundRoutine is MILDSoundRoutine) {
                                playAltBackgroundSound(soundRoutine, textView, volumeAdj)
                            }

                            adjustAltBGVol = false
                            delay(timeMillis = 1000)
                        }

                        //make sure the adjusted currVolume doesn't exceed max value
                        if(currVolume > 1.0) currVolume = 1.0F

                        Log.d("MainActivity", "adjusted FG volume to $currVolume BG volume to $currBgVolume")

                        //check if stop button pushed mid play or the sound file id is already initialized
                        if (!isFGSoundStopped) {
                            var filePath = ""

                            //play the sound file - playOnce handles loading and unloading the file
                            //Log.d("MainActivity", "playing ${sound.rawResId}")
                            mFgId = if(sound.filePathId != null) {
                                filePath = fileManager.getFilePath(sound.filePathId).toString()
                                Log.d("MainActivity", "playing file $filePath")
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

                            //reset the fg volume before next clip
                            currVolume = soundRoutine.fgVolume

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

                    runningSoundRoutine = null
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
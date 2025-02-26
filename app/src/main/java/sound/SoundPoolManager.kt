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
import utils.SpeechManager
import java.util.Locale


/*
   Uses library https://gitlab.com/olekdia/common/libraries/sound-pool
 */
class SoundPoolManager {

    private lateinit var mSoundPoolCompat: SoundPoolCompat
    private lateinit var fileManager: FileManager
    private lateinit var speechManager: SpeechManager

    private var mBgId = -1
    private var mFgId = -1
    private var fgJob: Job? = null
    private var bgJob: Job? = null
    private var altBgJob: Job? = null
    private var altBgId = -1
    private var allVolAdj = 0.85F


    companion object {

        const val ADJUST_BG_VOL_FACTOR = .35F
        const val ROOT_SOUNDS_DIR = "lt_sounds"
        const val THEMES_DIR = "themes"
        const val MILD_THEME = "mild_theme"

        @Volatile
        private var INSTANCE: SoundPoolManager? = null

        var runningSoundRoutine: SoundRoutine? = null
        var isFGSoundStopped = false
        var isBGSoundStopped = false
        var bGVolAdjustedForClip = false  //turn down alt bg sounds for clip
        private var currAltBgVolMax = 1F
        private var currAltBgVol = 1F
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
        speechManager = SpeechManager.getInstance()!!
    }

    fun setAllVolAdj(allVolAdj : Float) {
        this.allVolAdj = allVolAdj
    }

    fun playSoundList(soundList : List<String>, endBgRawRes : Int, endBgLabel : String, eventLabel: String,
        textView : TextView, playCount: Int, promptCount : Int = 1) {

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
                        SSILDSoundRoutine(playCount, bgRawRes, endBgRawRes, .3F, 0F, .7F, eventLabel, bgLabel, endBgLabel))
                }

                "p" -> {
                    val soundRoutine = getPodcastSoundRoutine(bgRawRes, playCount, endBgRawRes, eventLabel, bgLabel, endBgLabel)
                    soundRoutines.add(soundRoutine)
                }

                else -> {
                    if(soundType.isNotEmpty()) {
                        val soundRoutine = getSoundRoutine(bgRawRes, playCount, endBgRawRes, eventLabel, bgLabel, endBgLabel, soundType, promptCount)
                        soundRoutines.add(soundRoutine)
                    }
                }
            }
        }

        //Log.d("MainActivity", "soundRoutines = $soundRoutines")

        stopPlayingForeground()

        playSoundRoutines(soundRoutines, textView)
    }

    private fun getSoundRoutine(bgRawRes: Int, playCount: Int, endBgRawRes: Int, eventLabel: String, bgLabel: String,
             endBgLabel: String, type: String, promptCount: Int = 1) : SoundRoutine {

        //set the initial volumes based on background sound
        var (fgVolume, altBgVolume) = when (bgRawRes) {
            R.raw.green, R.raw.pink -> .52F to .48F
            R.raw.boxfan, R.raw.metal_fan -> .37F to .34F
            R.raw.ac -> .35F to .3F
            R.raw.brown, R.raw.waves -> .12F to .1F
            else -> .45F to .5F
        }

        //add the low/mid/high adjustment
        fgVolume *= allVolAdj
        altBgVolume *= allVolAdj

        val randomTheme = fileManager.getAllDirectoriesFromPath("$ROOT_SOUNDS_DIR/$THEMES_DIR").filter {!it.equals(MILD_THEME)}.shuffled().last()

        //get the appropriate sound routine, adjusting volumes further depending on type
        val soundRoutine = when (type) {
            "m" -> {
                fgVolume *= .8F
                altBgVolume *= .7F
                MILDSoundRoutine(playCount, bgRawRes, endBgRawRes, 1F, altBgVolume, fgVolume, eventLabel, bgLabel, endBgLabel, MILD_THEME )
            }

            "ma" -> {
                fgVolume *= .725F
                altBgVolume *= .625F

                MILDSoundRoutine(1, bgRawRes, endBgRawRes, 1F, altBgVolume, fgVolume, eventLabel, bgLabel,  endBgLabel, MILD_THEME)
            }

            "wa" -> {
                WILDSoundRoutine(playCount, bgRawRes, endBgRawRes, 1F, altBgVolume, fgVolume, eventLabel, bgLabel, endBgLabel, randomTheme)
            }

            "wp", "mp" -> {
                fgVolume *= .4F

                val fgLabel = if(type == "wp") "WILD" else "MILD"
                PromptSoundRoutine(1, bgRawRes, endBgRawRes, 1F, altBgVolume, fgVolume, eventLabel, bgLabel, endBgLabel, MILD_THEME, fgLabel, promptCount)
            }

            //default is "w", a manual WILD sound routine
            else -> {
                WILDSoundRoutine(playCount, bgRawRes, endBgRawRes, 1F, altBgVolume, fgVolume, eventLabel, bgLabel, endBgLabel, randomTheme)
            }
        }

        return soundRoutine

    }

    private fun getPodcastSoundRoutine(bgRawRes: Int, playCnt: Int, endBgRawRes: Int, eventLabel: String, bgLabel: String,
                                       endBgLabel: String): SoundRoutine {

        val (fgVolume, bgVolume) = when (bgRawRes) {
            R.raw.green, R.raw.pink, R.raw.boxfan, R.raw.metal_fan, R.raw.ac -> .4F to 1F
            else -> .15F to 1F
        }

        return PodSoundRoutine(playCnt, bgRawRes, endBgRawRes, bgVolume, 0F, fgVolume, eventLabel, bgLabel, endBgLabel)
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

                    var startingBgVolume = soundRoutine.bgVolume
                    currAltBgVolMax = soundRoutine.altBgVolume
                    currAltBgVol = soundRoutine.altBgVolume
                    Log.d("MainActivity", "236: setting currAltBgVol to $currAltBgVol")

                    if(!mSoundPoolCompat.isPlaying(mBgId)) {
                        //first start the white noise if it's not already running
                        playBackgroundSound(soundRoutine.bgRawId, startingBgVolume, textView, startingBgVolume)
                    }

                    Log.d("MainActivity", "playing $soundRoutine.javaClass.name} ${soundRoutine.fgVolume} ${soundRoutine.altBgVolume}")
                    if(soundRoutine.overrideBG()) {
                        //start a fade down before playing the routine
                        startingBgVolume = fadeDownBackgroundForRoutine(soundRoutine)
                    } else {
                        //Log.d("MainActivity", "stopping any alt background loop")
                        stopPlayingAltBackground()
                    }

                    //pause for a bit more
                    val delayCnt = if(soundRoutine is PromptSoundRoutine) 2 else 1
                    for (i in 1..delayCnt) {
                        yield()
                        //Log.d("MainActivity", "pausing before playing prompt")
                        delay(timeMillis = 5000)
                    }

                    //start both layers of background sounds (the white noise and the ambient sounds for the routine)
                    startBackgroundForRoutine(soundRoutine, textView, startingBgVolume)

                    //start with default volumes set for the entire routine. As the routine plays we can make changes to them based on volume adjustment values
                    //on each sound clip as it occurs in the list. This allows for clips in a routine to be more or less audible over the background white noise
                    //depending on factors like the type of routine, the particular place we are in the routine, the current hour of the night, or other factors
                    var playedSoundCnt = 0
                    var idxCnt = soundRoutine.getRoutine().size

                    for (sound in soundRoutine.getRoutine()) {
                        playedSoundCnt += 1

                        var (currBgVolume, currVolume) = adjustBackgroundForSound(sound, soundRoutine, startingBgVolume, textView)
                        startingBgVolume = currBgVolume

                        Log.d("MainActivity", "before number $playedSoundCnt of $idxCnt FG volume $currVolume BG volume $currBgVolume")

                        //check if stop button pushed mid play or the sound file id is already initialized
                        if (!isFGSoundStopped) {
                            var filePath = ""

                            //play the sound file - playOnce handles loading and unloading the file
                            //Log.d("MainActivity", "playing ${sound.rawResId}")
                            mFgId = if(sound.filePathId != null) {
                                filePath = fileManager.getFilePath(sound.filePathId).toString()
                                Log.d("MainActivity", "playing number $playedSoundCnt file $filePath")
                                mSoundPoolCompat.playOnce(filePath, currVolume, currVolume, 1F)
                            } else {
                                Log.d("MainActivity", "playing number $playedSoundCnt mFgId $mFgId")
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

                        lastBgLabel = soundRoutine.endBgLabel

                        //initialize any speech events if enough sounds in the routine are played.
                        //those are handled in MainActivity as it polls new watch events
                        if(soundRoutine.getSpeechEventsTrigger() == playedSoundCnt) {
                            val eventsCount = soundRoutine.getSpeechEventsCount()
                            val timeBetween = soundRoutine.getSpeechEventsTimeBetween()
                            if (eventsCount > 0 && timeBetween > 0) {
                                speechManager.setSoundRoutineEvents(eventsCount, timeBetween)
                            }
                        }
                    }

                    stopPlayingAltBackground()
                    lastBgRawId = -9999

                    runningSoundRoutine = null
                }

                if(lastBgLabel.isEmpty()) {
                    textView.text = ""
                } else {
                    if(lastBgRawId != -9999) {
                        playBackgroundSound(lastBgRawId, 1F, textView, 1F)
                        textView.text = "Playing $lastBgLabel"
                    } else {
                        textView.text = "Playing $lastBgLabel"
                    }
                }
            }
        } else {
            Log.d("MainActivity", "a sound routine is running so skipping")
        }
    }

    /*
        Check for volume adjust values on the sound. The override value is always used if set, for special logic such as in a prompt routine where
        we want to increase the volume for one particular clip as count in a prompt session increases. If no override value, then we might have a clip
        with the regular adjust value set. In this case we want to both diminish the background sound and increase the foreground sound. Finally, if
        neither adjust value is set but we diminished the background sound in a previous clip, restore the background to normal but play the remaining
        foreground clips at a diminished value
    */
    private suspend fun adjustBackgroundForSound(sound: Sound, soundRoutine: SoundRoutine, startingBgVolume: Float, textView: TextView): Pair<Float, Float> {
        var currVolume = soundRoutine.fgVolume
        var currBgVolume = startingBgVolume

        if (sound.fileVolAdjust != 0F) {
            //adjust the fg and altbg volume together to match
            currVolume *= sound.fileVolAdjust
            currAltBgVol *= sound.fileVolAdjust
        } else if (sound.isBgVolAdjust) {
            if (currBgVolume == soundRoutine.bgVolume) {
                currBgVolume *= ADJUST_BG_VOL_FACTOR
                stopPlayingAltBackground() // this will stop the alt bg sounds as well to just focus on the fg clip
                mSoundPoolCompat.setVolume(mBgId, currBgVolume, currBgVolume)
                bGVolAdjustedForClip = true
                delay(timeMillis = 1000)
            }
        } else if (bGVolAdjustedForClip) {
            //turn the white noise back up
            bGVolAdjustedForClip = false
            currVolume = soundRoutine.fgVolume
            if (soundRoutine is WILDSoundRoutine || soundRoutine is MILDSoundRoutine) {
                Log.d("MainActivity", "We should adjust the volume up slowly here")
                playBackgroundSound(soundRoutine.bgRawId, currBgVolume, textView, soundRoutine.bgVolume, 20)
                playAltBackgroundSound(soundRoutine, textView, false)  //skip start sounds
            } else {
                Log.d("MainActivity", "We're returning the volume to normal")
                mSoundPoolCompat.setVolume(mBgId, soundRoutine.bgVolume, soundRoutine.bgVolume)
            }

            delay(timeMillis = 1000)
        }

        //make sure the adjusted volumes don't exceed max value
        if(currVolume > 1.0) currVolume = 1.0F
        if(currBgVolume > 1.0) currBgVolume = 1.0F

        return Pair(currBgVolume, currVolume)
    }

    private suspend fun fadeDownBackgroundForRoutine(soundRoutine: SoundRoutine) : Float {
        val startVolume = soundRoutine.bgVolume * allVolAdj
        val finishVolume = startVolume * ADJUST_BG_VOL_FACTOR
        return when(soundRoutine) {
            is PromptSoundRoutine -> fadeBackgroundDown(20, 500, startVolume, finishVolume)
            is PodSoundRoutine -> fadeBackgroundDown(20, 800, startVolume, finishVolume * .65F)
            else -> fadeBackgroundDown(10, 1000, startVolume, finishVolume)
        }
    }

    private suspend fun fadeBackgroundDown(fadeDownCnt: Int, fadeDownDelay: Long, startVolume: Float, finishVolume: Float) : Float {
        //slowly up the volume of the background after delay
        var lastBgVol = startVolume
        var startAltBgVol = currAltBgVol
        for (i in 1..fadeDownCnt) {
            yield()
            if(isBGSoundStopped) {
                break
            }
            delay(timeMillis = fadeDownDelay)
            val cntFactor = i.toFloat() / fadeDownCnt.toFloat()

            //get amount to lower background sound by
            val bgFadeDownAmount = (startVolume - finishVolume) * cntFactor
            val currVol = startVolume - bgFadeDownAmount
            mSoundPoolCompat.setVolume(mBgId, currVol, currVol)
            lastBgVol = currVol
            Log.d("MainActivity", "for loop $i subtracting $bgFadeDownAmount to currVol $currVol with target $finishVolume")
        }

        //adjust alt Bg volume so when it starts up it will be at a lower amount
        currAltBgVol *= .5F
        //Log.d("MainActivity", "418: setting currAltBgVol to $currAltBgVol")

        return lastBgVol
    }


    private suspend fun startBackgroundForRoutine(
        soundRoutine: SoundRoutine,
        textView: TextView,
        startVolume: Float
    ) {
        when(soundRoutine) {
            is WILDSoundRoutine -> {
                if(isBGSoundStopped) {
                    playBackgroundSound(soundRoutine.bgRawId, soundRoutine.bgVolume, textView, soundRoutine.bgVolume)
                }
                playAltBackgroundSound(soundRoutine, textView)
            }

            is MILDSoundRoutine  -> {
                Log.d("MainActivity", "in start background routine")
                if(isBGSoundStopped) {
                    playBackgroundSound(soundRoutine.bgRawId, soundRoutine.bgVolume, textView, soundRoutine.bgVolume)
                }
                playBackgroundSound(soundRoutine.bgRawId, startVolume, textView, 1F, 20, 20000)
            }

            is PromptSoundRoutine  -> {
                Log.d("MainActivity", "in start background routine")
                if(isBGSoundStopped) {
                    playBackgroundSound(soundRoutine.bgRawId, soundRoutine.bgVolume, textView, soundRoutine.bgVolume)
                }
                playBackgroundSound(soundRoutine.bgRawId, startVolume, textView, 1F, 20, 3000)
            }

            is PodSoundRoutine -> {
                if(isBGSoundStopped) {
                    playBackgroundSound(soundRoutine.bgRawId, soundRoutine.bgVolume, textView, soundRoutine.bgVolume)
                }
                playBackgroundSound(soundRoutine.bgRawId, startVolume, textView, 1F, 25)
            }

            else -> {
                playBackgroundSound(soundRoutine.bgRawId, soundRoutine.bgVolume, textView, soundRoutine.bgVolume)
            }
        }
    }

    fun playBackgroundSound(bgRawId: Int, startVolume: Float, textView: TextView, targetVolume: Float, fadeUpCnt: Int = 0, fadeUpDelay: Long = 50000) {
        val scope = CoroutineScope(Dispatchers.Default)
        isBGSoundStopped = false

        var beginVolume = startVolume * allVolAdj
        var finishVolume = targetVolume * allVolAdj

        if (fadeUpCnt > 0) {
            Log.d("MainActivity", "fade up the background form $startVolume to $finishVolume")
            currAltBgVol *= .65F //the alt bg should have been stopped for the main clip, restart at lower level to include in fade-up
            //Log.d("MainActivity", "468: setting currAltBgVol to $currAltBgVol")
            fadeUpBackgroundForRoutine(fadeUpCnt, fadeUpDelay, finishVolume, startVolume, beginVolume)
        } else {
            loadAndPlayBackgroundRoutine(scope, bgRawId, finishVolume)
        }
    }

    private fun loadAndPlayBackgroundRoutine(
        scope: CoroutineScope,
        bgRawId: Int,
        finishVolume: Float
    ) {
        if (bgJob == null || !mSoundPoolCompat.isPlaying(mBgId)) {
            bgJob = scope.launch {
                if (mBgId != -1) {
                    mSoundPoolCompat.stop(mBgId)
                    mSoundPoolCompat.unload(mBgId)
                }

                if (!mSoundPoolCompat.isPlaying(mBgId)) {
                    mBgId = mSoundPoolCompat.load(bgRawId)
                }

                isLoadedMap[mBgId] = false
                var loopCnt = 0
                while (!isLoadedMap[mBgId]!! && loopCnt < 3) {
                    loopCnt++
                    delay(timeMillis = 500)
                }

                //we just want to start the background sound to run continously
                Log.d(
                    "MainActivity",
                    "no fade up, starting at targetVolume volume $finishVolume"
                )
                mSoundPoolCompat.play(mBgId, finishVolume, finishVolume, -1, 1f)
            }
        }
    }

    private fun fadeUpBackgroundForRoutine(
        fadeUpCnt: Int,
        fadeUpDelay: Long,
        finishVolume: Float,
        startVolume: Float,
        beginVolume: Float
    ) {
        //the background sound should already be running, slowly up the volume
        val scope = CoroutineScope(Dispatchers.Default)
        scope.launch {
            val beginAltBgVol = currAltBgVol

            for (i in 1..fadeUpCnt) {
                yield()
                if(isBGSoundStopped) {
                    break
                }
                delay(timeMillis = fadeUpDelay)
                val cntFactor = i.toFloat() / fadeUpCnt.toFloat()
                val fadeUpAmount = (finishVolume - startVolume) * cntFactor
                val currVol = beginVolume + fadeUpAmount
                Log.d("MainActivity", "for loop $i adding $fadeUpAmount to currVol $currVol with target $finishVolume")
                mSoundPoolCompat.setVolume(mBgId, currVol, currVol)

                //adjust the alt Bg volume back up a little each time as well.  We turned it down by half, this should restore it back
                val altFadeUpAmount = (currAltBgVolMax - beginAltBgVol) * cntFactor
                currAltBgVol = beginAltBgVol + altFadeUpAmount
                //Log.d("MainActivity", "530: setting currAltBgVol to $currAltBgVol")

                //Log.d("MainActivity", "for loop $i starting with $beginAltBgVol adding  $altFadeUpAmount curraltvol = $currAltBgVol ")
            }

            //now revert to target volume
            Log.d("MainActivity", "setting bg at targetVolume $finishVolume, altbg to $currAltBgVolMax ")
            mSoundPoolCompat.setVolume(mBgId, finishVolume, finishVolume)
            currAltBgVol = currAltBgVolMax
        }
    }

    private suspend fun playAltBackgroundSound(soundRoutine: SoundRoutine, textView: TextView, playStart : Boolean = true) {
        val scope = CoroutineScope(Dispatchers.Default)

        //might need to reset the label
        textView.text = "Playing ${soundRoutine.bgLabel}"

        if (altBgJob == null || !mSoundPoolCompat.isPlaying(altBgId)) {
            altBgJob = scope.launch {
                delay(timeMillis = 10000)
                val delayBetween = 20000L

                val startSounds = soundRoutine.getStartSounds()

                //play the start sounds once and not on restart
                if(playStart && startSounds.isNotEmpty()) {
                    playAltSounds(startSounds, soundRoutine.altBgVolume * 1.2F, delayBetween)
                }

                val altBGSounds = soundRoutine.getAltBGSounds()
                if(altBGSounds.isNotEmpty()) {
                    do {
                        playAltSounds(altBGSounds, currAltBgVol, delayBetween)
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

        for (altFile in altFiles) {
            yield()
            val filePath = fileManager.getFilePath(altFile)
            Log.d("MainActivity", "playing alt bg filePath $filePath")

            if (filePath != null) {
                Log.d("MainActivity", "current AltBg volume $currAltBgVol")

                altBgId = mSoundPoolCompat.playOnce(filePath, currAltBgVol, currAltBgVol, 1F)
                //Log.d("MainActivity", "file loading as id=$altBgId")

                waitForSoundPlayToComplete(altBgId)

                //Log.d("MainActivity", "delaying for $delayBetween")
                delay(timeMillis = delayBetween)

                //Log.d("MainActivity", "sound completed for id=$altBgId")
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

        Log.d("MainActivity", "waiting for sndId $sndId to stop")

        if (sndId != -1) {
            yield()
            var isPlaying = mSoundPoolCompat.isPlaying(sndId)
            while (isPlaying) {
                isPlaying = mSoundPoolCompat.isPlaying(sndId)
                delay(timeMillis = 1000)
            }
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

    fun stopPlayingAltBackground() {
        Log.d("MainActivity","Stopping altBgId $altBgId")
        bGVolAdjustedForClip = false
        mSoundPoolCompat.stop(altBgId)
        mSoundPoolCompat.unload(altBgId)
        altBgId = -1
        altBgJob?.let { cancelSoundPlayer(it) }
    }

    fun stopPlayingForeground() {
        Log.d("MainActivity","Stopping mFgId $mFgId")
        isFGSoundStopped = true
        bGVolAdjustedForClip = false
        mSoundPoolCompat.stop(mFgId)
        mSoundPoolCompat.unload(mFgId)
        fgJob?.let { cancelSoundPlayer(it) }

        runningSoundRoutine = null

        mFgId = -1
    }

    fun stopPlayingAll(textView: TextView) {
        Log.d("MainActivity","Stopping playing all")
        stopPlayingForeground()
        stopPlayingBackground()
        textView.text = ""
    }

    private fun cancelSoundPlayer(job : Job) {
        if(job.isActive) {
            job.cancel()
        }
    }
}
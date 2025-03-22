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


/*
   Uses library https://gitlab.com/olekdia/common/libraries/sound-pool
 */
class SoundPoolManager {

    private lateinit var mSoundPoolCompat: SoundPoolCompat
    private lateinit var fileManager: FileManager
    private lateinit var speechManager: SpeechManager
    private lateinit var volumeManager: SoundVolumeManager

    private var mBgId = -1
    private var mFgId = -1
    private var fgJob: Job? = null
    private var bgJob: Job? = null
    private var altBgJob: Job? = null
    private var altBgId = -1
    private var allVolAdj = 0.85F


    companion object {
        const val ROOT_SOUNDS_DIR = "lt_sounds"
        const val THEMES_DIR = "themes"
        const val MILD_THEME = "mild_theme"

        @Volatile
        private var INSTANCE: SoundPoolManager? = null
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
        volumeManager = SoundVolumeManager.getInstance(mSoundPoolCompat)
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
        //stop anything but the white noise background
        stopPlayingForeground()
        stopPlayingAltBackground()

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
                fgVolume *= .75F
                altBgVolume *= .6F

                MILDSoundRoutine(playCount, bgRawRes, endBgRawRes, 1F, altBgVolume, fgVolume, eventLabel, bgLabel,  endBgLabel, MILD_THEME)
            }

            "ma" -> {
                fgVolume *= .65F
                altBgVolume *= .5F

                MILDSoundRoutine(1, bgRawRes, endBgRawRes, 1F, altBgVolume, fgVolume, eventLabel, bgLabel,  endBgLabel, MILD_THEME)
            }

            "wa" -> {
                fgVolume *= .65F
                altBgVolume *= .5F

                WILDSoundRoutine(playCount, bgRawRes, endBgRawRes, 1F, altBgVolume, fgVolume, eventLabel, bgLabel, endBgLabel, randomTheme)
            }

            "wp", "mp" -> {
                fgVolume *= .625F

                val fgLabel = if(type == "wp") "WILD" else "MILD"
                MildPromptSoundRoutine(1, bgRawRes, endBgRawRes, 1F, altBgVolume, fgVolume, eventLabel, bgLabel, endBgLabel, MILD_THEME, fgLabel, promptCount)
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
        stopFadeUpBackground()

        Log.d("MainActivity","Stopping mBgId $mBgId")
        volumeManager.isBGSoundStopped = true
        mSoundPoolCompat.stop(mBgId)
        mSoundPoolCompat.unload(mBgId)
        mBgId = -1
        bgJob?.let { cancelSoundJob(it) }
    }

    private fun stopFadeUpBackground() {
        volumeManager.fadeBgJob?.let { cancelSoundJob(it) }
    }

    private fun stopFadeDownForeground() {
        volumeManager.fadeFgJob?.let { cancelSoundJob(it) }
    }

    private fun playSoundRoutines(soundRoutines : List<SoundRoutine>, textView : TextView) {
        val scope = CoroutineScope(Dispatchers.Default)
        volumeManager.isFGSoundStopped = false


        if (fgJob == null || fgJob!!.isCompleted) {
            fgJob = scope.launch {
                var lastBgRawId = -1
                var lastBgLabel = ""

                for(soundRoutine in soundRoutines) {
                    //stop any previous fade processes
                    stopFadeUpBackground()
                    stopFadeDownForeground()

                    textView.text = "Playing ${soundRoutine.bgLabel}"

                    var startingBgVolume = soundRoutine.bgVolume
                    var startingBgVolumeAdj = startingBgVolume * allVolAdj
                    volumeManager.currAltBgVolMax = soundRoutine.altBgVolume
                    volumeManager.currAltBgVol = soundRoutine.altBgVolume
                   // Log.d("MainActivity", "240: setting currAltBgVol to $currAltBgVol")

                    if(!mSoundPoolCompat.isPlaying(mBgId)) {
                        //first start the white noise if it's not already running
                        playBackgroundSound(soundRoutine.bgRawId, startingBgVolume, textView, startingBgVolume)
                    } else if(volumeManager.currBgVol > 0 && volumeManager.currBgVol != startingBgVolumeAdj){
                        //fade up the bg volume to match the routne
                        volumeManager.fadeBackgroundUpForReset(20, volumeManager.currBgVol, startingBgVolumeAdj, mBgId )
                    }

                    Log.d("MainActivity", "playing $soundRoutine.javaClass.name} ${soundRoutine.fgVolume} ${soundRoutine.altBgVolume}")
                    if(soundRoutine.fadeDownBg()) {
                        //start a fade down before playing the routine
                        startingBgVolume = volumeManager.fadeDownBackgroundForRoutine(soundRoutine, mBgId)
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
                        if (!volumeManager.isFGSoundStopped) {
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

                            if(soundRoutine.fadeDownFg()) {
                                //start a fade down on the playing fg sound (for long play files like podcasts)
                                volumeManager.fadeForegroundDown(25, currVolume, currVolume * .7F, mFgId)
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
        var currBgVol = startingBgVolume

        if (sound.fileVolAdjust != 0F) {
            //adjust the fg and altbg volume together to match
            currVolume *= sound.fileVolAdjust
            //Log.d("MainActivity", "361: setting currAltBgVol to $currAltBgVol")
        } else if (sound.isBgVolAdjust) {
            if (currBgVol == soundRoutine.bgVolume) {
                currBgVol *= .4F
                stopPlayingAltBackground() // this will stop the alt bg sounds as well to just focus on the fg clip
                volumeManager.setBgVol(mBgId, currBgVol)
                volumeManager.isBGVolAdjustedForClip = true
                delay(timeMillis = 1000)
            }
        } else if (volumeManager.isBGVolAdjustedForClip) {
            //turn the white noise back up
            volumeManager.isBGVolAdjustedForClip = false
            currVolume = soundRoutine.fgVolume
            if (soundRoutine is WILDSoundRoutine) {
                Log.d("MainActivity", "We should adjust the volume up slowly here")
                playBackgroundSound(soundRoutine.bgRawId, currBgVol, textView, soundRoutine.bgVolume, 20)
                playAltBackgroundSound(soundRoutine, textView, false)  //skip start sounds
            } else {
                Log.d("MainActivity", "We're returning the volume to normal")
                currBgVol = soundRoutine.bgVolume * allVolAdj
                volumeManager.setBgVol(mBgId, currBgVol)
            }

            delay(timeMillis = 1000)
        }

        //make sure the adjusted volumes don't exceed max value
        if(currVolume > 1.0) currVolume = 1.0F
        if(currBgVol > 1.0) currBgVol = 1.0F

        return Pair(currBgVol, currVolume)
    }




    private suspend fun startBackgroundForRoutine(
        soundRoutine: SoundRoutine,
        textView: TextView,
        startVolume: Float
    ) {
        when(soundRoutine) {
            is WILDSoundRoutine -> {
                if(volumeManager.isBGSoundStopped) {
                    playBackgroundSound(soundRoutine.bgRawId, soundRoutine.bgVolume, textView, soundRoutine.bgVolume)
                }
                playAltBackgroundSound(soundRoutine, textView)
            }

            is MILDSoundRoutine  -> {
                Log.d("MainActivity", "in start background routine")
                if(volumeManager.isBGSoundStopped) {
                    playBackgroundSound(soundRoutine.bgRawId, soundRoutine.bgVolume, textView, soundRoutine.bgVolume)
                }
                playAltBackgroundSound(soundRoutine, textView)
                playBackgroundSound(soundRoutine.bgRawId, startVolume, textView, 1F, 20, 30000)
            }

            is PromptSoundRoutine  -> {
                Log.d("MainActivity", "in start background routine")
                if(volumeManager.isBGSoundStopped) {
                    playBackgroundSound(soundRoutine.bgRawId, soundRoutine.bgVolume, textView, soundRoutine.bgVolume)
                }
                playAltBackgroundSound(soundRoutine, textView)
                playBackgroundSound(soundRoutine.bgRawId, startVolume, textView, 1F, 20, 2000)
            }

            is PodSoundRoutine -> {
                if(volumeManager.isBGSoundStopped) {
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
        volumeManager.isBGSoundStopped = false

        var finishVolume = targetVolume * allVolAdj

        if (fadeUpCnt > 0) {
            Log.d("MainActivity", "fade up the background from $startVolume to $finishVolume")
            volumeManager.currAltBgVol *= .7F //set the alt background to a lower level to fade up as well
            //Log.d("MainActivity", "484: setting currAltBgVol to $currAltBgVol")
            volumeManager.fadeBackgroundUp(fadeUpCnt, fadeUpDelay, finishVolume, startVolume, mBgId)
        } else {
            loadAndPlayBackgroundRoutine(scope, bgRawId, finishVolume)
        }
    }

    private fun loadAndPlayBackgroundRoutine(
        scope: CoroutineScope,
        bgRawId: Int,
        volume: Float
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
                volumeManager.currBgVol = volume

                //we just want to start the background sound to run continously
                Log.d(
                    "MainActivity",
                    "no fade up, starting at targetVolume volume $volumeManager.currBgVol"
                )
                mSoundPoolCompat.play(mBgId, volumeManager.currBgVol, volumeManager.currBgVol, -1, 1f)

            }
        }
    }

//    private fun setBgVol(currBgVol: Float) {
//        mSoundPoolCompat.setVolume(mBgId, currBgVol, currBgVol)
//    }

    private suspend fun playAltBackgroundSound(soundRoutine: SoundRoutine, textView: TextView, playStart : Boolean = true) {
        val scope = CoroutineScope(Dispatchers.Default)

        //might need to reset the label
        textView.text = "Playing ${soundRoutine.bgLabel}"

        if (altBgJob == null || !mSoundPoolCompat.isPlaying(altBgId)) {
            altBgJob = scope.launch {
                delay(timeMillis = 10000)
                //mild has an intro file and then long delay so hold off playing alt bg until then
                val delayBetween = if(soundRoutine is MILDSoundRoutine) 120_000L else 20_000L

                val startSounds = soundRoutine.getStartSounds()

                //play the start sounds once and not on restart
                if(playStart && startSounds.isNotEmpty()) {
                    playAltSounds(startSounds, soundRoutine.altBgVolume * 1.2F, delayBetween)
                }

                val altBGSounds = soundRoutine.getAltBGSounds()
                if(altBGSounds.isNotEmpty()) {
                    do {
                        playAltSounds(altBGSounds, volumeManager.currAltBgVol, delayBetween)
                    } while (!volumeManager.isBGSoundStopped)
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
                Log.d("MainActivity", "current AltBg volume $volumeManager.currAltBgVol")

                altBgId = mSoundPoolCompat.playOnce(filePath, volumeManager.currAltBgVol, volumeManager.currAltBgVol, 1F)
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
        volumeManager.isBGVolAdjustedForClip = false
        mSoundPoolCompat.stop(altBgId)
        mSoundPoolCompat.unload(altBgId)
        altBgId = -1
        altBgJob?.let { cancelSoundJob(it) }
    }

    fun stopPlayingForeground() {
        Log.d("MainActivity","Stopping mFgId $mFgId")
        volumeManager.isFGSoundStopped = true
        volumeManager.isBGVolAdjustedForClip = false
        mSoundPoolCompat.stop(mFgId)
        mSoundPoolCompat.unload(mFgId)
        stopFadeDownForeground()

        fgJob?.let { cancelSoundJob(it) }
        fgJob = null
        mFgId = -1
    }

    fun stopPlayingAll(textView: TextView) {
        Log.d("MainActivity","Stopping playing all")
        stopPlayingForeground()
        stopPlayingBackground()
        textView.text = ""
    }

    private fun cancelSoundJob(job : Job) {
        if(job.isActive) {
            job.cancel()
        }
    }
}
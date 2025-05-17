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

    private var fgJob: Job? = null
    private var bgJob: Job? = null
    private var altBgJob: Job? = null
    private var altBgId = -1
    private var allVolAdj = 0.85F
    var activeFgVolAdj = 1F
    private var immFgId = -1

    companion object {
        const val ROOT_SOUNDS_DIR = "lt_sounds"
        const val THEMES_DIR = "themes"
        const val MILD_THEME = "mild_theme"

        @Volatile
        private var INSTANCE: SoundPoolManager? = null
        var isLoadedMap = emptyMap<Int, Boolean>().toMutableMap()
        var loadingErrorMessage = ""
        var mBgId = -1
        var mFgId = -1

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

    fun playSoundList(soundList : List<String>, bgRawRes : Int, eventLabel: String, textView : TextView,
      playTier: Int, promptCount : Int = 1) {

        //default
        var bgRawId = if(bgRawRes > 0) {
            //just keep playing the current background
            bgRawRes
        } else {
            R.raw.boxfan
        }

        val bgLabel = getBackgroundSoundLabel(bgRawId)

        val soundRoutines = mutableListOf<SoundRoutine>()

        Log.d("MainActivity", "soundList = $soundList")
        for(soundType in soundList) {
            when(soundType) {
                "s" -> {
                    soundRoutines.add(
                        SSILDSoundRoutine(playTier, bgRawId, .3F, 0F, .7F, eventLabel, bgLabel))
                }

                "p" -> {
                    val soundRoutine = getPodcastSoundRoutine(bgRawId, playTier, eventLabel, bgLabel)
                    soundRoutines.add(soundRoutine)
                }

                else -> {
                    if(soundType.isNotEmpty()) {
                        val soundRoutine = getSoundRoutine(bgRawId, playTier, eventLabel, bgLabel, soundType, promptCount)
                        soundRoutines.add(soundRoutine)
                    }
                }
            }
        }

        //stop anything but the white noise background
        stopPlayingForeground()
        stopPlayingAltBackground()

        playSoundRoutines(soundRoutines, textView)
    }

    private fun getSoundRoutine(bgRawId: Int, playTier: Int, eventLabel: String, bgLabel: String,
             type: String, promptCount: Int = 1) : SoundRoutine {

        //set the initial volumes based on background sound
        var (fgVolume, altBgVolume) = when (bgRawId) {
            R.raw.green, R.raw.pink -> .52F to .4F
            R.raw.boxfan, R.raw.metal_fan -> .37F to .27F
            R.raw.ac -> .35F to .28F
            R.raw.brown, R.raw.waves -> .12F to .09F
            else -> .45F to .35F
        }

        //add the low/mid/high adjustment
        fgVolume *= allVolAdj
        altBgVolume *= allVolAdj
        val bgVolume = 1F * allVolAdj

        val randomTheme = fileManager.getAllDirectoriesFromPath("$ROOT_SOUNDS_DIR/$THEMES_DIR").filter {!it.equals(MILD_THEME)}.shuffled().last()

        //get the appropriate sound routine, adjusting volumes further depending on type
        val soundRoutine = when (type) {

            "m" -> {
                fgVolume *= .75F
                altBgVolume *= .6F

                MILDSoundRoutine(playTier, bgRawId, bgVolume, altBgVolume, fgVolume, eventLabel, bgLabel, MILD_THEME)
            }

            "ma" -> {
                fgVolume *= .65F
                altBgVolume *= .5F

                MILDSoundRoutine(1, bgRawId, bgVolume, altBgVolume, fgVolume, eventLabel, bgLabel, MILD_THEME)
            }

            "wa" -> {
                fgVolume *= .65F
                altBgVolume *= .5F

                WILDSoundRoutine(playTier, bgRawId, bgVolume, altBgVolume, fgVolume, eventLabel, bgLabel, randomTheme)
            }

            "wp", "mp" -> {
                val tierAdj = if(playTier == 3) .9F else if(playTier == 2) .65F else .4F

                fgVolume *= tierAdj

                //alt bg volume for prompts will be faded up along with backgound but for prompts we want to more match
                //foreground, so set to start at higher level
                altBgVolume += .4F*altBgVolume
                altBgVolume *= tierAdj

                val fgLabel = if(type == "wp") "WILD" else "MILD"
                MildPromptSoundRoutine(playTier, bgRawId, bgVolume, altBgVolume, fgVolume, eventLabel, bgLabel, MILD_THEME, fgLabel, promptCount)
            }

            //default is "w", a manual WILD sound routine
            else -> {
                WILDSoundRoutine(playTier, bgRawId, bgVolume, altBgVolume, fgVolume, eventLabel, bgLabel, randomTheme)
            }
        }

        return soundRoutine

    }

    private fun getPodcastSoundRoutine(bgRawId: Int, playCnt: Int, eventLabel: String, bgLabel: String): SoundRoutine {

        val (fgVolume, bgVolume) = when (bgRawId) {
            R.raw.green, R.raw.pink, R.raw.boxfan, R.raw.metal_fan, R.raw.ac -> .4F to 1F
            else -> .15F to 1F
        }

        return PodSoundRoutine(playCnt, bgRawId, bgVolume, 0F, fgVolume, eventLabel, bgLabel)
    }

    fun stopPlayingBackground() {
        stopPlayingAltBackground()
        stopFadeUpBackground()

        Log.d("MainActivity","Stopping background id $mBgId")
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
                    //stop any previous fade processes and reset the FgVol
                    stopFadeUpBackground()
                    stopFadeDownForeground()

                    textView.text = "Playing ${soundRoutine.bgLabel}"

                    var startingBgVolume = soundRoutine.bgVolume
                    volumeManager.currAltBgVolMax = soundRoutine.altBgVolume
                    volumeManager.currAltBgVol = soundRoutine.altBgVolume
                    // Log.d("MainActivity", "240: setting currAltBgVol to $currAltBgVol")

                    if(!mSoundPoolCompat.isPlaying(mBgId)) {
                        //first start the white noise if it's not already running
                        playBackgroundSound(soundRoutine.bgRawId, startingBgVolume, textView, startingBgVolume)
                    } else if(volumeManager.currBgVol > 0 && volumeManager.currBgVol < startingBgVolume){
                        //fade up the bg volume to match the routine
                        volumeManager.fadeBackgroundUpForReset(20, volumeManager.currBgVol, startingBgVolume )
                    }

                    Log.d("MainActivity", "playing $soundRoutine.javaClass.name} start fg vol:${soundRoutine.fgVolume} " +
                            "start bg vol: ${soundRoutine.altBgVolume}")
                    if(soundRoutine.fadeDownBg()) {
                        //start a fade down before playing the routine
                        Log.d("MainActivity","curr mBgId = $mBgId ${SoundPoolManager.mBgId}")
                        startingBgVolume = volumeManager.fadeDownBackgroundForRoutine(soundRoutine)
                    } else {
                        stopPlayingAltBackground()
                    }

                    //pause for a bit more
                    val delayCnt = if(soundRoutine is PromptSoundRoutine) 2 else 1
                    for (i in 1..delayCnt) {
                        yield()
                        delay(timeMillis = 5000)
                    }

                    //start both layers of background sounds (the white noise and the ambient sounds for the routine)
                    startBackgroundForRoutine(soundRoutine, textView, startingBgVolume)

                    //start with default volumes set for the entire routine. As the routine plays we can make changes to them based on volume adjustment values
                    //on each sound clip as it occurs in the list. This allows for clips in a routine to be more or less audible over the background white noise
                    //depending on factors like the type of routine, the particular place we are in the routine, the current hour of the night, or other factors
                    var playedSoundCnt = 0
                    var idxCnt = soundRoutine.getRoutine().size


//                    var startingFgVolume = if(soundRoutine.fadeDownFg() && volumeManager.isFgFadeDownRunning())
//                        volumeManager.currFgVol else soundRoutine.fgVolume
//                    startingFgVolume *= activeFgVolAdj //activity can trigger an adjustment to turn the fg volume down (just prompts for now)

                    var startingFgVolume = soundRoutine.fgVolume * activeFgVolAdj //activity can trigger an adjustment to turn the fg volume down (just prompts for now)

                    if(soundRoutine.fadeDownFg()) {
                        //start a fade down on the playing fg sound (for long play files like podcasts)
                        val routineSize = soundRoutine.getRoutine().size
                        val delay = if(soundRoutine is PodSoundRoutine || soundRoutine is WILDSoundRoutine) 50_000L
                            else if(routineSize > 10) 30_000 else 20_000
                        volumeManager.fadeForegroundDown(25, startingFgVolume, startingFgVolume * .5F, delay)
                    }

                    for (sound in soundRoutine.getRoutine()) {
                        //check if stop button pushed mid play or the sound file id is already initialized
                        if (!volumeManager.isFGSoundStopped) {
                            playedSoundCnt += 1

                            var filePath = ""

                            var currVolume = if(volumeManager.isFgFadeDownRunning()) volumeManager.currFgVol else startingFgVolume

                            //a sound can be set up to start or stop alt background play
                            if(sound.toggleAltBg != null) {
                                stopPlayingAltBackground()
                                if(sound.toggleAltBg == "ON") {
                                    playAltBackgroundSound(soundRoutine, textView)
                                }
                            }

                            //play the sound file - playOnce handles loading and unloading the file
                            //Log.d("MainActivity", "playing ${sound.rawResId}")
                            mFgId = if(sound.filePathId != null) {
                                filePath = fileManager.getFilePath(sound.filePathId).toString()
                                Log.d("MainActivity", "playing number $playedSoundCnt of $idxCnt file $filePath " +
                                        "at fg vol $currVolume")
                                mSoundPoolCompat.playOnce(filePath, currVolume, currVolume, 1F)
                            } else {
                                Log.d("MainActivity", "playing number $playedSoundCnt of $idxCnt res file id $mFgId " +
                                        "at fg vol $currVolume")
                                mSoundPoolCompat.playOnce(sound.rawResId, currVolume, currVolume, 1F)
                            }


                            var playStatus = "Playing ${soundRoutine.bgLabel} and ${soundRoutine.fgLabel} routine"
                             playStatus +=  if(filePath.isNotEmpty()) ", current file ${filePath.substringAfterLast("/")}"
                                else " for ${soundRoutine.playTier} cycles"
                             textView.text = playStatus


                            waitForSoundPlayToComplete(mFgId)

                            for (i in 1..sound.delayAfter) {
                                yield()
                                delay(timeMillis = 1000)
                            }

                            mFgId = -1
                        }

                        lastBgLabel = soundRoutine.bgLabel

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
                playBackgroundSound(soundRoutine.bgRawId, startVolume, textView, 1F, 25)
            }

            is MILDSoundRoutine  -> {
                //Log.d("MainActivity", "in start background routine")
                if(volumeManager.isBGSoundStopped) {
                    playBackgroundSound(soundRoutine.bgRawId, soundRoutine.bgVolume, textView, soundRoutine.bgVolume)
                }
                playAltBackgroundSound(soundRoutine, textView)
                playBackgroundSound(soundRoutine.bgRawId, startVolume, textView, 1F, 20, 30000)
            }

            is PromptSoundRoutine  -> {
                //Log.d("MainActivity", "in start background routine")
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
            //Log.d("MainActivity", "fade up the background from $startVolume to $finishVolume")
            volumeManager.currAltBgVol *= .7F //set the alt background to a lower level to fade up as well
            //Log.d("MainActivity", "484: setting currAltBgVol to $currAltBgVol")
            volumeManager.fadeBackgroundUp(fadeUpCnt, fadeUpDelay, finishVolume, startVolume)
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
                //Log.d("MainActivity", "no fade up, starting at targetVolume volume $volumeManager.currBgVol")
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
                val delayBetween = if(soundRoutine is MILDSoundRoutine) 120_000L
                   else if(soundRoutine is PromptSoundRoutine) 10_000L else 20_000L

                val startSounds = soundRoutine.getStartSounds()

                //play the start sounds once and not on restart
                if(playStart && startSounds.isNotEmpty()) {
                    val volume = soundRoutine.altBgVolume * 1.2F
                    playAltSounds(startSounds, volume, delayBetween)
                }

                val altBGSounds = soundRoutine.getAltBGSounds()
                if(altBGSounds.isNotEmpty()) {
                    do {
                        //activity can trigger an adjustment to turn the fg and alt bg volume down (just prompts for now)
                        val volume = volumeManager.currAltBgVol * activeFgVolAdj
                        playAltSounds(altBGSounds, volume , delayBetween)
                    } while (!volumeManager.isBGSoundStopped)
                }
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

            if (filePath != null) {

                Log.d("MainActivity", "playing alt bg file $filePath at current alt bg vol $volume")

                altBgId = mSoundPoolCompat.playOnce(filePath, volume, volume, 1F)

                waitForSoundPlayToComplete(altBgId)

                delay(timeMillis = delayBetween)
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
        Log.d("MainActivity","Stopping alt background id $altBgId")
        volumeManager.isBGVolAdjustedForClip = false
        mSoundPoolCompat.stop(altBgId)
        mSoundPoolCompat.unload(altBgId)
        altBgId = -1
        altBgJob?.let { cancelSoundJob(it) }
    }

    fun stopPlayingForeground() {
        Log.d("MainActivity","stopping foreground id $mFgId")
        volumeManager.isFGSoundStopped = true
        volumeManager.isBGVolAdjustedForClip = false
        mSoundPoolCompat.stop(mFgId)
        mSoundPoolCompat.unload(mFgId)

        mSoundPoolCompat.stop(immFgId)
        mSoundPoolCompat.unload(immFgId)
        immFgId = -1

        stopFadeDownForeground()

        fgJob?.let { cancelSoundJob(it) }
        fgJob = null
        mFgId = -1
    }

    fun stopPlayingAll(textView: TextView) {
        Log.d("MainActivity","Stopping all background and foreground")
        stopPlayingForeground()
        stopPlayingBackground()
        textView.text = ""
    }

    private fun cancelSoundJob(job : Job) {
        if(job.isActive) {
            job.cancel()
        }
    }

    //immediately plays a single sound file once
    fun playSound(filePath: String, volume: Float) {
        val filePathFull = fileManager.getFilePath(filePath)
        Log.d("MainActivity", "playing immediately sound $filePathFull at volume $volume")
        immFgId = mSoundPoolCompat.playOnce(filePathFull, volume, volume, 1F)
    }
}
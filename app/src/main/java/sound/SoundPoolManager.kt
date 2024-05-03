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
import utils.DimVolUpdateStatus
import java.io.File
import java.time.LocalDateTime

private const val dimBGVolBy = .07F
private const val dimFGVolBy = .04F
private const val volMin = .175F


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
                //Log.d("load completed soundID", "$sampleId, isSuccess: $isSuccess")
                isLoadedMap[sampleId] = isSuccess
                if (errorMsg != null) {
                    loadingErrorMessage = errorMsg
                }
            }
        })
    }

    fun playSoundList(soundList : List<String>, endBgRawRes : Int, endBgLabel : String,
             eventLabel: String, textView : TextView, hour: Int, playCnt: Int, intensityLevel: Int = 1) {

        //default
        var bgRawRes = if(endBgRawRes > 0) {
            //just keep playing the current background
            endBgRawRes
        } else {
            R.raw.brown
        }

        val bgLabel = "Event Brown"

        val soundRoutines = mutableListOf<SoundRoutine>()

        if (soundList.contains("s")) {
            bgRawRes = R.raw.waves
            soundRoutines.add(
                SSILDSoundRoutine(playCnt, bgRawRes, endBgRawRes, .3F, 0F, .7F, eventLabel, bgLabel, endBgLabel))
        }
        if (soundList.contains("m")) {
            bgRawRes = R.raw.waves
            soundRoutines.add(
                MILDSoundRoutine(playCnt, bgRawRes, endBgRawRes,.3F, 0F, .7F,  eventLabel, bgLabel, endBgLabel))
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
            2, 3 -> 1
            6, 7, 8 -> 2
            else -> 0
        }

        //adjust the volumes based background sound and time of night
        var (fgVolume, altBgVolume) = when (bgRawRes) {
            R.raw.green, R.raw.pink -> .45F - (volOffset * .035F) to .5F - (volOffset * .035F)
            R.raw.boxfan, R.raw.metal_fan -> .38F - (volOffset * .03F) to .42F - (volOffset * .03F)
            R.raw.ac -> .3F - (volOffset * .02F) to .36F - (volOffset * .02F)
            R.raw.brown, R.raw.waves -> .09F - (volOffset * .005F) to .1F - (volOffset * .006F)
            else -> .4F - (volOffset * .03F) to .43F - (volOffset * .03F)
        }

        //adjust the volumes down further if low intensity
        if (intensityLevel == 0) {
            fgVolume *= .6F
            altBgVolume *= .6F
        } else if (intensityLevel == -1) {
            fgVolume *= .3F
            altBgVolume *= .3F
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
                    //setup the volume diminish feature
                    val currVolume = soundRoutine.altBgVolume
                    var dimVolStatus : DimVolUpdateStatus? = null
                    val dimMinLimit = soundRoutine.dimMinLimit()
                    if(dimMinLimit > 0) {
                        val lastLimitTime = LocalDateTime.now()
                        dimVolStatus = DimVolUpdateStatus(dimMinLimit, lastLimitTime, currVolume)
                    }

                    do {
                        playAltSounds(altBGSounds, currVolume, true, dimVolStatus)
                    } while (!isBGSoundStopped)
                }

                //Log.d("MainActivity", "finishing altBgJob with altBgId ${altBgId}")
            }
        }
    }

    private suspend fun playAltSounds(
        altFiles: List<String>,
        volume: Float,
        pause: Boolean = true,
        dimVolStatus: DimVolUpdateStatus? = null
    ) {

        var currVolume = volume

        for (altFile in altFiles) {
            //if we've been looping longer than the dim minutes limit, drop the volume
            if( dimVolStatus != null && currVolume.compareTo(volMin) >= 0 && LocalDateTime.now() >
                    dimVolStatus.lastUpdateTime.plusMinutes(dimVolStatus.updateMinuteLimit)) {
                dimVolStatus.lastUpdateVol -= dimBGVolBy
                dimVolStatus.lastUpdateTime = LocalDateTime.now()
                currVolume = dimVolStatus.lastUpdateVol
                //Log.d("DimVolume", "dropped ALT BG volume to $currVolume")
            }

            val filePath = getFilePath(altFile)
            Log.d("MainActivity", "playing alt bg filePath $filePath")

            if (filePath != null) {
                //Log.d("MainActivity", "starting load for file=$filePath")

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

                    //we're about to play the foreground sounds, set up the volume diminish feature
                    var lastLimitTime = LocalDateTime.now()
                    var currVolume = soundRoutine.fgVolume
                    val dimMinLimit = soundRoutine.dimMinLimit()

                    for (sound in soundRoutine.getRoutine()) {
                        //if we've been looping longer than the dim minutes limit, drop the volume
                        if( dimMinLimit > 0 && (currVolume.compareTo(volMin) >= 0) && LocalDateTime.now() > lastLimitTime.plusMinutes(dimMinLimit)) {
                            currVolume -= dimFGVolBy
                            lastLimitTime = LocalDateTime.now()
                            //Log.d("DimVolume", "dropped FG bg volume to $currVolume")
                        }

                        //check if stop button pushed mid play or the sound file id is already initialized
                        if (!isFGSoundStopped) {

                            textView.text = "Playing ${soundRoutine.bgLabel} and ${soundRoutine.fgLabel} " +
                                    "routine for ${soundRoutine.playCount} cycles"

                            //play the sound file - playOnce handles loading and unloading the file
                            //Log.d("MainActivity", "playing ${sound.rawResId}")
                            mFgId = if(sound.filePathId != null) {
                                val filePath = getFilePath(sound.filePathId)
                                Log.d("MainActivity", "playing $filePath")
                                mSoundPoolCompat.playOnce(filePath, currVolume, currVolume, 1F)
                            } else {
                                Log.d("MainActivity", "playing mFgId $mFgId")
                                mSoundPoolCompat.playOnce(sound.rawResId, currVolume, currVolume, 1F)
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

    private fun getFilePath(fileName : String): String? {
        val ex = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)

        val fileLocation = fileName.split("/")

        val file = File(
            File(ex.path + "/" + fileLocation[0] + "/" + fileLocation[1] + "/"),
               fileLocation[2])

        //Log.d("MainActivity", "getting $file")
        //Log.d("MainActivity", "file exists " + file.exists())

        val filePath: String
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
}
package sound

import android.content.Context
import android.util.Log
import com.olekdia.soundpool.SoundPoolCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield

class SoundVolumeManager() {
    private lateinit var mSoundPoolCompat: SoundPoolCompat

    var isFGSoundStopped = false
    var isBGSoundStopped = false
    var isBGVolAdjustedForClip = false  //turn down alt bg sounds for clip
    var currBgVol = 1F
    var currFgVol = 1F
    var currAltBgVolMax = 1F
    var currAltBgVol = 1F

    var fadeBgJob: Job? = null
    var fadeFgJob: Job? = null

    companion object {

        @Volatile
        private var INSTANCE: SoundVolumeManager? = null

        fun getInstance(mSoundPoolCompat: SoundPoolCompat): SoundVolumeManager {
            synchronized(this) {
                var instance = INSTANCE
                if (instance == null) {
                    instance = SoundVolumeManager()
                    instance.initSoundPool(mSoundPoolCompat)
                    INSTANCE = instance
                }
                return instance
            }
        }

        fun getInstance() : SoundVolumeManager? {
            return INSTANCE
        }

    }

    private fun initSoundPool(mSoundPoolCompat: SoundPoolCompat) {
        this.mSoundPoolCompat = mSoundPoolCompat
    }

    suspend fun fadeDownBackgroundForRoutine(soundRoutine: SoundRoutine) : Float {
        var finishVolume = currBgVol * .2F

        return when(soundRoutine) {
            is PromptSoundRoutine -> {
                val promptCount = soundRoutine.promptCount
                finishVolume = when(promptCount) {
                    1 -> currBgVol * .7F
                    else -> currBgVol * .8F
                }
                fadeBackgroundDown(20, 600, finishVolume)
            }

            is MILDSoundRoutine -> {
                finishVolume = currBgVol * .17F
                fadeBackgroundDown(20, 600, finishVolume)
            }

            else -> fadeBackgroundDown(20, 600, finishVolume)
        }
    }


    private suspend fun fadeBackgroundDown(fadeDownCnt: Int, fadeDownDelay: Long, finishVolume: Float) : Float {
        //slowly lower the volume of the background after delay
        //Log.d("MainActivity", "$currBgVol minus $finishVolume divided by ${fadeDownCnt.toFloat()}")
        val bgFadeDownAmount = (currBgVol - finishVolume) / fadeDownCnt.toFloat()

        for (i in 1..fadeDownCnt) {
            yield()
            if(isBGSoundStopped) {
                break
            }
            delay(timeMillis = fadeDownDelay)
            currBgVol -= bgFadeDownAmount
            setBgVol(currBgVol)
            //Log.d("MainActivity", "for loop $i subtracting $bgFadeDownAmount to get currBgVol $currBgVol with target $finishVolume")
        }

        return currBgVol
    }

    fun fadeForegroundDown(fadeDownCnt: Int, startVolume: Float, finishVolume: Float, fadeDownStartDelay: Long, fadeDownDelay: Long = 50000L) : Float {
        val scope = CoroutineScope(Dispatchers.Default)
        var lastFgVol = startVolume
        val beginAltBgVol = currAltBgVol

        fadeFgJob = scope.launch {
            if (fadeDownStartDelay > 0) delay(timeMillis = fadeDownStartDelay)

            for (i in 1..fadeDownCnt) {
                yield()
                if (isFGSoundStopped) {
                    break
                }
                delay(timeMillis = fadeDownDelay)
                val cntFactor = i.toFloat() / fadeDownCnt.toFloat()

                //get amount to lower sound by
                val fgFadeDownAmount = (startVolume - finishVolume) * cntFactor
                currFgVol = startVolume - fgFadeDownAmount
                mSoundPoolCompat.setVolume(SoundPoolManager.mFgId, currFgVol, currFgVol)

                currAltBgVol = (currAltBgVol - (currAltBgVol * .03F)) //eh, just drop the alt bg vol a little each iteration

                lastFgVol = currFgVol
                Log.d("MainActivity", "for loop $i subtracting $fgFadeDownAmount to fg currVol $currFgVol with target $finishVolume")
            }
            //re-initialize volume
            currFgVol = 1F
            fadeFgJob = null
        }


        return lastFgVol
    }

    fun fadeBackgroundUp(fadeUpCnt: Int, fadeUpDelay: Long, finishVolume: Float, startVolume: Float, fadeUpStartDelay: Long) {
        //the background sound should already be running, slowly up the volume
        val scope = CoroutineScope(Dispatchers.Default)
        fadeBgJob = scope.launch {
            val beginAltBgVol = currAltBgVol

            val fadeUpAmount = (finishVolume - startVolume) / fadeUpCnt.toFloat()
            val altFadeUpAmount = (currAltBgVolMax - beginAltBgVol) / fadeUpCnt.toFloat()

            currBgVol = startVolume

            if (fadeUpStartDelay > 0) delay(timeMillis = fadeUpStartDelay)

            for (i in 1..fadeUpCnt) {
                delay(timeMillis = fadeUpDelay)
                Log.d("MainActivity", "fadeUpDelay = " + fadeUpDelay)
                currBgVol += fadeUpAmount
                Log.d("MainActivity", "for loop $i adding $fadeUpAmount to get currVol $currBgVol with target $finishVolume")
                setBgVol(currBgVol)

                //adjust the alt Bg volume back up a little each time as well.  We turned it down by half, this should restore it back

                currAltBgVol = beginAltBgVol + altFadeUpAmount
                //Log.d("MainActivity", "547: setting currAltBgVol to $currAltBgVol")
                yield()
            }

            //now revert to target volume
            //Log.d("MainActivity", "setting bg at targetVolume $finishVolume, altbg to $currAltBgVolMax ")
            currBgVol = finishVolume
            setBgVol(currBgVol)
            currAltBgVol = currAltBgVolMax
            //Log.d("MainActivity", "555: setting currAltBgVol to $currAltBgVol")
        }
    }

    suspend fun fadeBackgroundUpForReset(fadeUpCnt: Int, startVolume: Float, finishVolume: Float, fadeUpDelay: Long = 1000) : Float {
        //slowly up the volume of the background after delay
        val bgFadeUpAmount = (finishVolume - startVolume) / fadeUpCnt.toFloat()
        currBgVol = startVolume

        for (i in 1..fadeUpCnt) {
            yield()
            if(isBGSoundStopped) {
                break
            }
            delay(timeMillis = fadeUpDelay)
            currBgVol += bgFadeUpAmount
            setBgVol(currBgVol)
            //Log.d("MainActivity", "for loop $i in reset bgFadeUP adding $bgFadeUpAmount to currVol $currBgVol with target $finishVolume")
        }

        return currBgVol
    }


    fun setBgVol(currBgVol: Float) {
        val currMbgId = SoundPoolManager.mBgId
        mSoundPoolCompat.setVolume(SoundPoolManager.mBgId, currBgVol, currBgVol)
    }

    fun isFgFadeDownRunning(): Boolean {
        Log.d("MainActivity", "isFgRunning = $fadeFgJob $currFgVol")
        return fadeFgJob != null && currFgVol < 1F
    }
    
}
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

    suspend fun fadeDownBackgroundForRoutine(soundRoutine: SoundRoutine, mBgId: Int) : Float {
        var finishVolume = currBgVol * .5F

        return when(soundRoutine) {
            is PromptSoundRoutine -> {
                val promptCount = soundRoutine.promptCount
                finishVolume = when(promptCount) {
                    1 -> currBgVol * .65F
                    else -> currBgVol * .8F
                }
                fadeBackgroundDown(20, 600, finishVolume, mBgId)
            }

            is MILDSoundRoutine -> {
                finishVolume = currBgVol * .7F
                fadeBackgroundDown(20, 600, finishVolume, mBgId)
            }

            else -> fadeBackgroundDown(20, 600, finishVolume, mBgId)
        }
    }


    private suspend fun fadeBackgroundDown(fadeDownCnt: Int, fadeDownDelay: Long, finishVolume: Float, mBgId: Int) : Float {
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
            setBgVol(mBgId, currBgVol)
            //Log.d("MainActivity", "for loop $i subtracting $bgFadeDownAmount to get currBgVol $currBgVol with target $finishVolume")
        }

        return currBgVol
    }

    fun fadeForegroundDown(fadeDownCnt: Int, startVolume: Float, finishVolume: Float, mFgId: Int, fadeDownDelay: Long = 50000L ) : Float {
        val scope = CoroutineScope(Dispatchers.Default)
        var lastFgVol = startVolume

        fadeFgJob = scope.launch {
            for (i in 1..fadeDownCnt) {
                yield()
                if (isFGSoundStopped) {
                    break
                }
                delay(timeMillis = fadeDownDelay)
                val cntFactor = i.toFloat() / fadeDownCnt.toFloat()

                //get amount to lower sound by
                val fgFadeDownAmount = (startVolume - finishVolume) * cntFactor
                val currVol = startVolume - fgFadeDownAmount
                mSoundPoolCompat.setVolume(mFgId, currVol, currVol)

                lastFgVol = currVol
                //Log.d("MainActivity", "for loop $i subtracting $fgFadeDownAmount to fg currVol $currVol with target $finishVolume")
            }
        }

        return lastFgVol
    }

    fun fadeBackgroundUp(fadeUpCnt: Int, fadeUpDelay: Long, finishVolume: Float, startVolume: Float, mBgId: Int) {
        //the background sound should already be running, slowly up the volume
        val scope = CoroutineScope(Dispatchers.Default)
        fadeBgJob = scope.launch {
            val beginAltBgVol = currAltBgVol

            val fadeUpAmount = (finishVolume - startVolume) / fadeUpCnt.toFloat()
            val altFadeUpAmount = (currAltBgVolMax - beginAltBgVol) / fadeUpCnt.toFloat()

            currBgVol = startVolume

            for (i in 1..fadeUpCnt) {
                delay(timeMillis = fadeUpDelay)

                currBgVol += fadeUpAmount
                //Log.d("MainActivity", "for loop $i adding $fadeUpAmount to get currVol $currBgVol with target $finishVolume")
                setBgVol(mBgId, currBgVol)

                //adjust the alt Bg volume back up a little each time as well.  We turned it down by half, this should restore it back

                currAltBgVol = beginAltBgVol + altFadeUpAmount
                //Log.d("MainActivity", "547: setting currAltBgVol to $currAltBgVol")
                yield()
            }

            //now revert to target volume
            //Log.d("MainActivity", "setting bg at targetVolume $finishVolume, altbg to $currAltBgVolMax ")
            currBgVol = finishVolume
            setBgVol(mBgId, currBgVol)
            currAltBgVol = currAltBgVolMax
            //Log.d("MainActivity", "555: setting currAltBgVol to $currAltBgVol")
        }
    }

    suspend fun fadeBackgroundUpForReset(fadeDownCnt: Int, startVolume: Float, finishVolume: Float, mBgId: Int, fadeUpDelay: Long = 1000) : Float {
        //slowly up the volume of the background after delay
        val bgFadeUpAmount = (finishVolume - startVolume) / fadeDownCnt.toFloat()
        for (i in 1..fadeDownCnt) {
            yield()
            if(isBGSoundStopped) {
                break
            }
            delay(timeMillis = fadeUpDelay)
            currBgVol = startVolume + bgFadeUpAmount
            setBgVol(mBgId, currBgVol)
            //Log.d("MainActivity", "for loop $i in bgFadeUP adding $bgFadeUpAmount to currVol $currBgVol with target $finishVolume")
        }

        return currBgVol
    }


    fun setBgVol(mBgId: Int, currBgVol: Float) {
        mSoundPoolCompat.setVolume(mBgId, currBgVol, currBgVol)
    }
    
}
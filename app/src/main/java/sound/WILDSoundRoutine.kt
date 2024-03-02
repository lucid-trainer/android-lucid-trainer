package sound

import android.util.Log
import com.lucidtrainer.R

class WILDSoundRoutine(override var repetition: Int, override var bgRawId: Int, override var endBgRawId: Int,
                       override var bgVolume: Float, override var altBgVolume: Float, override var fgVolume: Float,
                       override val eventLabel : String, override var bgLabel : String, override var endBgLabel : String,
                       override val fgLabel : String = "WILD",
) : SoundRoutine {

    override fun getStartSounds(): List<String> {
        val startSounds : MutableList<String> = emptyList<String>().toMutableList()

        startSounds.add("hypnag/start/hypnag_start.ogg")

        return startSounds
    }

    override fun getAltBGSounds(): List<String> {
        var altBGSounds : MutableList<String> = emptyList<String>().toMutableList()

        val cntrs1 = (1..6).shuffled().slice(0..3)
        val cntrs2 = (1..8).shuffled().slice(0..3)

        Log.d("WILDSoundRoutine ", "calling getAltBGSounds")

        //we have the same number of sounds of each type so just alternate them
        for (i in 0..3) {
            altBGSounds.add("hypnag/bg/bg_music_" +  + cntrs1[i]+ ".ogg")
            altBGSounds.add("hypnag/bg/bg_sfx_" + cntrs2[i] + ".ogg")
        }

        val finalBGSounds : MutableList<String> = emptyList<String>().toMutableList()

        var cntList = (1..3).shuffled()
        var cntIdx = 0;
        for (i in 0 until altBGSounds.size step 2) {
            finalBGSounds.add(altBGSounds[i])
            Log.d("WILDSoundRoutine ", "$i adding ${altBGSounds[i]}")

            finalBGSounds.add(altBGSounds[i+1])
            Log.d("WILDSoundRoutine ", "$i adding ${altBGSounds[i+1]}")

            cntIdx = if(cntIdx == 3) 0 else cntIdx
            finalBGSounds.add("hypnag/bg/bg_prompt_${cntList[cntIdx]}.ogg");
            Log.d("WILDSoundRoutine ", "$i adding hypnag/bg/bg_prompt_${cntList[cntIdx]}.ogg")
            cntIdx++
        }

        return finalBGSounds
    }

    override fun getRoutine(): List<Sound> {
        val routine : MutableList<Sound> = emptyList<Sound>().toMutableList()
        var finalRoutine : MutableList<Sound> = emptyList<Sound>().toMutableList()

        val cntrs = (1..61).shuffled().slice(0..16)
        val cntrs2 = (1..5).shuffled().slice(0..2)

        for (cntr in cntrs) {
            routine.add(Sound(0, 20, "hypnag/fg/fg_" + cntr + ".ogg"))
            Log.d("WILDSoundRoutine ", "adding hypnag/fg/fg_$cntr.ogg")
        }

        for (cntr in cntrs2) {
            routine.add(Sound(0, 20, "hypnag/fg_rec/fg_" + cntr + ".ogg"))
            Log.d("WILDSoundRoutine ", "adding hypnag/fg_rec/fg_$cntr.ogg")
        }

        finalRoutine = routine.shuffled().toMutableList()

        return finalRoutine
    }
}
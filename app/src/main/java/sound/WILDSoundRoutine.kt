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

        val cntrs = (1..12).shuffled().slice(0..8)

        Log.d("WILDSoundRoutine ", "calling getAltBGSounds")

        var cntList = (1..3).shuffled()
        var cntIdx = 0;

        for (i in 0..8) {
            altBGSounds.add("hypnag/bg/bg_" +  + cntrs[i]+ ".ogg")

            cntIdx = if(cntIdx == 4) 0 else cntIdx
            if(i % 2 == 0) {
                altBGSounds.add("hypnag/bg/bg_prompt_${cntList[cntIdx]}.ogg")
            }
            cntIdx++
        }

        return altBGSounds
    }

    override fun getRoutine(): List<Sound> {
        val routine : MutableList<Sound> = emptyList<Sound>().toMutableList()
        var finalRoutine : MutableList<Sound> = emptyList<Sound>().toMutableList()

        val cntrs = (1..66).shuffled().slice(0..22)
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

    override fun dimMinLimit() : Long {
        return 2L
    }
}
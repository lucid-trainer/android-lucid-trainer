package sound

import android.util.Log
import com.lucidtrainer.R

class WILDPromptSoundRoutine(override var repetition: Int, override var bgRawId: Int, override var endBgRawId: Int,
                             override var bgVolume: Float, override var altBgVolume: Float, override var fgVolume: Float,
                             override val eventLabel : String, override var bgLabel : String, override var endBgLabel : String,
                             override val fgLabel : String = "WILD",
) : SoundRoutine {

    override fun getStartSounds(): List<String> {
        val startSounds : MutableList<String> = emptyList<String>().toMutableList()

        startSounds.add("hypnag/bg/bg_prompt_0.ogg")

        return startSounds
    }

    override fun getAltBGSounds(): List<String> {
        return emptyList()
    }

    override fun getRoutine(): List<Sound> {
        val routine : MutableList<Sound> = emptyList<Sound>().toMutableList()
        val limit = if(repetition > 2) 2 else repetition
        val randomDelay = (40..120).shuffled().last()

        val cntrs = (1..limit).shuffled()
        for (index in cntrs.indices) {
            val cntr = cntrs[index]
            val delayAfter = if(index == cntrs.size-1) randomDelay else 30
            routine.add(Sound(0, delayAfter, "hypnag/bg/bg_prompt_" + cntr + ".ogg"))
        }

        //play a couple of quiet sounds
        val cntrs2 = (1..limit+1).shuffled()
        for (cntr in cntrs2) {
            routine.add(Sound(0, randomDelay, "hypnag/bg/bg_qsfx_" + cntr + ".ogg"))
        }

        return routine
    }
}
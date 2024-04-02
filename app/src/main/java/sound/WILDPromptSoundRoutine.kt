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

        if(repetition >= 1) {
            startSounds.add("hypnag/bg/bg_prompt_0.ogg")
        }

        if(repetition >= 2) {
            startSounds.add("hypnag/bg/bg_sfx_5.ogg")
        }

        return startSounds
    }

    override fun getAltBGSounds(): List<String> {
        return emptyList()
    }

    override fun getRoutine(): List<Sound> {
        val routine : MutableList<Sound> = emptyList<Sound>().toMutableList()
        val limit = if(repetition > 3) 3 else repetition

        val cntrs = (1..limit).shuffled()
        Log.d("WILDPromptSoundRoutine ", "repetition = $repetition cntrs = $cntrs")
        for (cntr in cntrs) {
            routine.add(Sound(0, 30, "hypnag/bg/bg_prompt_" + cntr + ".ogg"))
        }

        return routine
    }
}
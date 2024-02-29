package sound

import android.util.Log
import com.lucidtrainer.R

class WILDPromptSoundRoutine(override var repetition: Int, override var bgRawId: Int, override var endBgRawId: Int,
                             override var bgVolume: Float, override var altBgVolume: Float, override var fgVolume: Float,
                             override val eventLabel : String, override var bgLabel : String, override var endBgLabel : String,
                             override val fgLabel : String = "WILD",
) : SoundRoutine {

    override fun getStartSounds(): List<String> {
        return emptyList()
    }

    override fun getAltBGSounds(): List<String> {
        return emptyList()
    }

    override fun getRoutine(): List<Sound> {
        val routine : MutableList<Sound> = emptyList<Sound>().toMutableList()

        val cntrs = (1..3).shuffled()
        for (cntr in cntrs) {
            routine.add(Sound(0, 30, "hypnag/bg/bg_prompt_" + cntr + ".ogg"))
        }

        routine.add(Sound(0, 10, "hypnag/bg/bg_prompt_1.ogg"))

        return routine
    }
}
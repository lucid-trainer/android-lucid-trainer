package sound

import android.util.Log
import com.lucidtrainer.R

class WILDSoundRoutine(override var playCount: Int, override var bgRawId: Int, override var endBgRawId: Int,
                       override var bgVolume: Float, override var altBgVolume: Float, override var fgVolume: Float,
                       override val eventLabel : String, override var bgLabel : String, override var endBgLabel : String,
                       override val fgLabel : String = "WILD",
) : SoundRoutine {

    override fun getStartSounds(): List<String> {
        val startSounds : MutableList<String> = emptyList<String>().toMutableList()

        startSounds.add("wild/start/wild_start.ogg")

        return startSounds
    }

    override fun getAltBGSounds(): List<String> {
        var altBGSounds : MutableList<String> = emptyList<String>().toMutableList()

        val cntrs = (1..12).shuffled().slice(0..8)

        for (i in 0..8) {
            altBGSounds.add("wild/bg/bg_" +  + cntrs[i]+ ".ogg")
        }

        return altBGSounds
    }

    override fun getRoutine(): List<Sound> {
        val routine : MutableList<Sound> = emptyList<Sound>().toMutableList()
        var finalRoutine : MutableList<Sound> = emptyList<Sound>().toMutableList()

        val cntrs = (1..70).shuffled().slice(0..25)

        for (cntr in cntrs) {
            routine.add(Sound(0, 20, "wild/fg/fg_" + cntr + ".ogg"))
            Log.d("WILDSoundRoutine ", "adding wild/fg/fg_$cntr.ogg")
        }

        finalRoutine = routine.shuffled().toMutableList()

        //add a couple of prompts at the beginning
        var cntList = (1..3).shuffled()
        finalRoutine.add(2, Sound(0, 20, "wild/prompt/prompt_${cntList[0]}.ogg"))

        finalRoutine.add(5, Sound(0, 20, "wild/prompt/prompt_${cntList[1]}.ogg"))

        return finalRoutine
    }

    override fun dimMinLimit() : Long {
        return 2L
    }
}
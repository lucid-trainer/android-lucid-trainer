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

        val cntrs = (1..70).shuffled().slice(0..9)

        for (cntr in cntrs) {
            routine.add(Sound(0, 20, "wild/fg/fg_" + cntr + ".ogg"))
            Log.d("WILDSoundRoutine ", "adding wild/fg/fg_$cntr.ogg")
        }

        finalRoutine = routine.shuffled().toMutableList()

        //add a prompt near start of the the routine
        val cnt = (1..3).shuffled().last()
        finalRoutine.add(3, Sound(0, 20, "wild/prompt/prompt_$cnt.ogg"))

        //add a longer more distinct sound clip towards the end and adjust volume on it
        val clipCnt = (1..5).shuffled().last()
        finalRoutine.add(7, Sound(0, 20, "wild/main/clip_$clipCnt.ogg", 1.5F))


        return finalRoutine
    }

    override fun dimMinLimit() : Long {
        return 2L
    }
}
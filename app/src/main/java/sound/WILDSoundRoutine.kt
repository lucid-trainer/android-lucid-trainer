package sound

import android.util.Log
import com.lucidtrainer.R
import utils.FileMonitor

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

        val files = FileMonitor.getFilesFromDirectory("bg").shuffled().slice(0..8)

        for (i in 0..8) {
            altBGSounds.add("wild/bg/${files[i]}")
        }

        return altBGSounds
    }

    override fun getRoutine(): List<Sound> {
        val routine : MutableList<Sound> = emptyList<Sound>().toMutableList()

        val files = FileMonitor.getFilesFromDirectory("fg").shuffled().slice(0..7)

        for (i in 0..7) {
            routine.add(Sound(0, 20, "wild/fg/${files[i]}"))
        }

        //add a prompt near start of the the routine
        val promptFile = FileMonitor.getFilesFromDirectory("prompt").shuffled().last()
        routine.add(3, Sound(0, 20, "wild/prompt/$promptFile"))

        //add a longer more distinct main sound clip towards the end and adjust volume on it
        val clipFile = FileMonitor.getFilesFromDirectory("main").shuffled().last()
        routine.add(6, Sound(0, 20, "wild/main/$clipFile", 1.25F))

        return routine
    }

    override fun dimMinLimit() : Long {
        return 2L
    }
}
package sound

import com.lucidtrainer.R
import utils.FileManager

class MILDSoundRoutine(override var playCount: Int, override var bgRawId: Int, override var endBgRawId: Int,
                       override var bgVolume: Float, override var altBgVolume: Float, override var fgVolume: Float,
                       override val eventLabel : String, override var bgLabel : String, override var endBgLabel : String,
                       override val fgLabel : String = "MILD"

) : SoundRoutine {

    private val fileManager = FileManager.getInstance()!!

    companion object {
        const val ROOT_DIR = "wild"
        const val FOREGROUND_DIR = "fg"
        const val ALT_BACKGROUND_DIR = "bg"
        const val PROMPT_DIR = "prompt"
        const val START_DIR = "start"
    }

    override fun getRoutine(): List<Sound> {
        val routine : MutableList<Sound> = emptyList<Sound>().toMutableList()

        routine.add(Sound(R.raw.mild_intro, 30))
        routine.add(Sound(R.raw.mild_dream_1, 50))
        routine.add(Sound(R.raw.mild_sign, 20))
        routine.add(Sound(R.raw.mild_replay, 50))

        if(playCount > 2) {
            routine.add(Sound(R.raw.mild_dream_2, 50))
            routine.add(Sound(R.raw.mild_sign, 20))
            routine.add(Sound(R.raw.mild_replay, 50))
        }

        routine.add(Sound(R.raw.mild_finish, 25))

        //add light wild routine (quiet, hard to hear sound to hopefully fall asleep to but no long main clip)
        addWildStartSound(routine)

        addForegroundSounds(routine)

        addPromptSound(routine)

        return routine
    }

    override fun getStartSounds(): List<String> {
        return emptyList()
    }

    override fun getAltBGSounds(): List<String> {
        var altBGSounds : MutableList<String> = emptyList<String>().toMutableList()

        val dir = "/$ROOT_DIR/$ALT_BACKGROUND_DIR"

        val files = fileManager.getFilesFromDirectory(dir).shuffled().slice(0..9)

        for (i in 0..9) {
            altBGSounds.add("${WILDSoundRoutine.ROOT_DIR}/${WILDSoundRoutine.ALT_BACKGROUND_DIR}/${files[i]}")
        }

        return altBGSounds
    }

    private fun addForegroundSounds(routine: MutableList<Sound>) {

        var dir = "$ROOT_DIR/$FOREGROUND_DIR"

        val files = fileManager.getUnusedFilesFromDirectory(dir, 15).shuffled().slice(0..14)

        //Log.d("WildRoutine", "used fg ${FileMonitor.getUnusedFilesFromDirectory(dir, 8).size}")

        for (file in files) {
            routine.add(Sound(0, 20, "$dir/$file"))
        }

        fileManager.addFilesUsed(dir, files)
    }

    private fun addWildStartSound(routine: MutableList<Sound>) {
        routine.add(Sound(0, 20, "$ROOT_DIR/$START_DIR/wild_start.ogg"))
    }

    private fun addPromptSound(routine: MutableList<Sound>) {
        //add a prompt near start of the the routine
        var dir = "$ROOT_DIR/$PROMPT_DIR"

        val file = fileManager.getFilesFromDirectory(dir).shuffled().last()

        val index = if(playCount > 2)  10 else 7

        routine.add(index, Sound(0, 20, "$dir/$file"))
    }
}
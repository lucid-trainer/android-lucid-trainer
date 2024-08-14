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
        const val ROOT_DIR = "lt_sounds"
        const val FOREGROUND_DIR = "fg"
        const val ALT_BACKGROUND_DIR = "bg"
        const val PROMPT_DIR = "prompt"
        const val START_DIR = "start"
    }

    override fun getRoutine(): List<Sound> {
        val routine : MutableList<Sound> = emptyList<Sound>().toMutableList()

        routine.add(Sound(R.raw.mild_intro, 30))
        routine.add(Sound(R.raw.mild_dream_1, 90))
        routine.add(Sound(R.raw.mild_replay, 60))
        routine.add(Sound(R.raw.mild_finish, 20))

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
        val altBGSounds : MutableList<String> = emptyList<String>().toMutableList()

        val dir = "/$ROOT_DIR/$ALT_BACKGROUND_DIR"

        val files = fileManager.getFilesFromDirectory(dir).shuffled().slice(0..9)

        for (i in 0..9) {
            altBGSounds.add("${WILDSoundRoutine.ROOT_DIR}/${WILDSoundRoutine.ALT_BACKGROUND_DIR}/${files[i]}")
        }

        return altBGSounds
    }

    private fun addForegroundSounds(routine: MutableList<Sound>) {

        val dir = "$ROOT_DIR/$FOREGROUND_DIR"

        val limit = if(playCount > 1) 12 else 8

        val files = fileManager.getUnusedFilesFromDirectory(dir, limit).shuffled().slice(0 until limit)

        //Log.d("WildRoutine", "used fg ${FileMonitor.getUnusedFilesFromDirectory(dir, 8).size}")

        for (file in files) {
            routine.add(Sound(0, 20, "$dir/$file"))
        }

        fileManager.addFilesUsed(dir, files)
    }

    private fun addWildStartSound(routine: MutableList<Sound>) {
        routine.add(Sound(0, 20, "$ROOT_DIR/$START_DIR/start.ogg"))
    }

    private fun addPromptSound(routine: MutableList<Sound>) {
        //add a prompt near start of the the routine
        var dir = "$ROOT_DIR/$PROMPT_DIR"

        val file = fileManager.getFilesFromDirectory(dir).shuffled().last()

        routine.add(6, Sound(0, 20, "$dir/$file"))

        val interfile = "${WILDSoundRoutine.ROOT_DIR}/${WILDSoundRoutine.START_DIR}/prompt_intermit.ogg"

        for(i in 5..routine.size) {
            if(i % 3 == 0) {
                routine.add(i-1, Sound(0, 20, "$interfile"))
            }
        }

        //add a few to extend out at the end
        val limit = if(playCount > 1) 4 else 3
        for(i in 1..limit) {
            val delayAfter = 40 + i*10
            routine.add(Sound(0, delayAfter, "$interfile"))
        }
    }
}
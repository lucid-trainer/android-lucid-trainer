package sound

import com.lucidtrainer.R
import utils.FileManager

class MILDSoundRoutine(override var playCount: Int, override var bgRawId: Int, override var endBgRawId: Int,
                       override var bgVolume: Float, override var altBgVolume: Float, override var fgVolume: Float,
                       override val eventLabel : String, override var bgLabel : String, override var endBgLabel : String,
                       override var theme: String, override val fgLabel : String = "MILD"

) : SoundRoutine {

    private val fileManager = FileManager.getInstance()!!

    override fun getRoutine(): List<Sound> {
        val routine : MutableList<Sound> = emptyList<Sound>().toMutableList()

        routine.add(Sound(R.raw.mild_intro, 100))
        routine.add(Sound(R.raw.mild_finish, 70))

        addStartSound(routine)

        addForegroundSounds(routine)

        if(playCount > 1) {
            addPromptSound(routine)
        }

        return routine
    }

    override fun getStartSounds(): List<String> {
        return emptyList()
    }

    override fun getAltBGSounds(): List<String> {
        val altBGSounds : MutableList<String> = emptyList<String>().toMutableList()

        val dir = "/$ROOT_DIR/$THEMES_DIR/$theme/$ALT_BACKGROUND_DIR"

        val files = fileManager.getFilesFromDirectory(dir).shuffled().slice(0..9)

        for (i in 0..9) {
            altBGSounds.add("$dir/${files[i]}")
        }

        return altBGSounds
    }

    private fun addForegroundSounds(routine: MutableList<Sound>) {

        val dir = "$ROOT_DIR/$THEMES_DIR/$theme/$FOREGROUND_DIR"

        val limit = if(playCount > 1) 9 else 6

        val files = fileManager.getUnusedFilesFromDirectory(dir, limit).shuffled().slice(0 until limit)

        //Log.d("WildRoutine", "used fg ${FileMonitor.getUnusedFilesFromDirectory(dir, 8).size}")

        for (file in files) {
            routine.add(Sound(0, 20, "$dir/$file"))
        }

        fileManager.addFilesUsed(dir, files)
    }

    private fun addStartSound(routine: MutableList<Sound>) {
        routine.add(Sound(0, 20, "$ROOT_DIR/$THEMES_DIR/$theme/$START_DIR/start.ogg"))
    }

    private fun addPromptSound(routine: MutableList<Sound>) {
        //add a prompt near start of the the routine
        var dir = "$ROOT_DIR/$PROMPT_DIR"

        val file = fileManager.getFilesFromDirectory(dir).filter{it.startsWith("prompt")}.shuffled().last()

        routine.add(6, Sound(0, 20, "$dir/$file"))
    }
}
package sound

import utils.FileManager

class PromptSoundRoutine(
    override var playCount: Int, override var bgRawId: Int, override var endBgRawId: Int,
    override var bgVolume: Float, override var altBgVolume: Float, override var fgVolume: Float,
    override val eventLabel: String, override var bgLabel: String, override var endBgLabel: String,
    override val fgLabel: String = "PROMPT",
) : SoundRoutine {

    private val fileManager = FileManager.getInstance()!!

    companion object {
        const val ROOT_DIR = "lt_sounds"
        const val START_DIR = "start"
        const val PROMPT_DIR = "prompt"
    }

    override fun getStartSounds(): List<String> {
        return emptyList()
    }

    override fun getAltBGSounds(): List<String> {
        return emptyList()
    }

    override fun getRoutine(): List<Sound> {
        val routine : MutableList<Sound> = emptyList<Sound>().toMutableList()
        val startDir = "$ROOT_DIR/$START_DIR"
        val dir = "$ROOT_DIR/$PROMPT_DIR"

        val files = fileManager.getFilesFromDirectory(dir).shuffled().take(2)

        if(playCount == 1) {
            routine.add(Sound(0, 0, "$startDir/prompt_start.ogg", 1.0F))
        } else if(playCount == 2) {
            routine.add(Sound(0, 0, "$startDir/prompt_start_short.ogg", 1.0F))
        }

        if(playCount == 1 && fgLabel == "MILD") {
            routine.add(Sound(0, 0, "$startDir/mild_prompt.ogg", 1.1F))
        } else {
            for (file in files) {
                routine.add(Sound(0, 20, "$dir/$file", 1.1F))
            }
        }

        routine.add(Sound(0, 0, "$startDir/silence.ogg"))

        return routine
    }
}
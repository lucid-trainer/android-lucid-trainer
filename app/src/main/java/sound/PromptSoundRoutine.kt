package sound

import utils.FileManager

class PromptSoundRoutine(
    override var playCount: Int, override var bgRawId: Int, override var endBgRawId: Int,
    override var bgVolume: Float, override var altBgVolume: Float, override var fgVolume: Float,
    override val eventLabel: String, override var bgLabel: String, override var endBgLabel: String,
    override val fgLabel: String = "WILD",
) : SoundRoutine {

    private val fileManager = FileManager.getInstance()!!

    companion object {
        const val ROOT_DIR = "wild"
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

        val file = fileManager.getFilesFromDirectory(dir).shuffled().last()
        routine.add(Sound(0, 0, "$startDir/ufo_prompt.ogg"))
        routine.add(Sound(0, 0, "$dir/$file", 1F))
        routine.add(Sound(0, 0, "$startDir/silence.ogg"))

        return routine
    }
}
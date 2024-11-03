package sound

import utils.FileManager

class PromptSoundRoutine(
    override var playCount: Int, override var bgRawId: Int, override var endBgRawId: Int,
    override var bgVolume: Float, override var altBgVolume: Float, override var fgVolume: Float,
    override val eventLabel: String, override var bgLabel: String, override var endBgLabel: String,
    override val theme: String, override val fgLabel: String = "PROMPT", private val promptCount: Int = 1
) : SoundRoutine {

    private val fileManager = FileManager.getInstance()!!

    override fun getStartSounds(): List<String> {
        return emptyList()
    }

    override fun getAltBGSounds(): List<String> {
        return emptyList()
    }

    override fun getRoutine(): List<Sound> {
        val routine : MutableList<Sound> = emptyList<Sound>().toMutableList()
        val promptDir = "$ROOT_DIR/$PROMPT_DIR"
        val fileVolAdjOverride = getVolIncr(promptCount)

        routine.add(Sound(0, 3, "$promptDir/start.ogg", 0F, fileVolAdjOverride))

        val promptInterFile = "$promptDir/intermit.ogg"
        routine.add(Sound(0, 3, promptInterFile, 0F, fileVolAdjOverride))

        val promptFile = fileManager.getFilesFromDirectory(promptDir).filter{it.startsWith("prompt")}.shuffled().last()
        routine.add(Sound(0, 3, "$promptDir/$promptFile", 0F, fileVolAdjOverride))

        routine.add(Sound(0, 0, "$promptDir/silence.ogg"))

        return routine
    }

    private fun getVolIncr(promptCount: Int): Float {
        return when(promptCount) {
            0 -> .25F
            1 -> .5F
            2 -> .8F
            3 -> 1.1F
            4 -> 1.5F
            else -> 2F
        }
    }
}
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

        if(promptCount == 3) {
            routine.add(Sound(0, 3, "$promptDir/prompt.ogg", 0F, fileVolAdjOverride))
        }

        val promptFile = fileManager.getFilesFromDirectory(promptDir).filter{it.startsWith("prompt_")}.shuffled().last()
        routine.add(Sound(0, 3, "$promptDir/$promptFile", 0F, fileVolAdjOverride))

        routine.add(Sound(0, 0, "$promptDir/silence.ogg"))

        return routine
    }

    private fun getVolIncr(promptCount: Int): Float {
        return when(promptCount) {
            1 -> .60F
            2 -> .70F
            3 -> .80F
            4 -> .70F
            5 -> .6F
            else -> .70F
        }
    }
}
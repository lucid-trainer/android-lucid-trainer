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

        if(promptCount <= 2) {
            routine.add(Sound(0, 3, "$promptDir/start.ogg", 0F, fileVolAdjOverride))
        }

        if(promptCount == 2) {
            val promptInterFile = "$promptDir/intermit.ogg"
            routine.add(Sound(0, 3, promptInterFile, 0F, fileVolAdjOverride))
        }

        val promptFile = fileManager.getFilesFromDirectory(promptDir).filter{it.startsWith("prompt")}.shuffled().last()
        routine.add(Sound(0, 3, "$promptDir/$promptFile", 0F, fileVolAdjOverride))

        routine.add(Sound(0, 0, "$promptDir/silence.ogg"))

        return routine
    }

    private fun getVolIncr(promptCount: Int): Float {
        return when(promptCount) {
            1 -> .35F
            2 -> .5F
            3 -> .75F
            4 -> .6F
            5 -> .45F
            else -> .3F
        }
    }
}
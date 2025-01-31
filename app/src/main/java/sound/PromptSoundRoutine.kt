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
        val fileVolAdj = getVolIncr(promptCount)

        if(playCount == 1) {
            routine.add(Sound(0, 5, "$promptDir/name.ogg", fileVolAdj))
        }

        if(playCount <= 2) {
            val promptFile =
                fileManager.getFilesFromDirectory(promptDir).filter { it.startsWith("random_") }
                    .shuffled().last()

            routine.add(Sound(0, 2, "$promptDir/$promptFile", fileVolAdj))
        }

        routine.add(Sound(0, 0, "$promptDir/silence.ogg"))  //need this to reset background vol

        routine.add(Sound(0, 0, "$promptDir/ambient.ogg", 0F, fileVolAdj))

        routine.add(Sound(0, 0, "$promptDir/silence.ogg"))

        return routine
    }

    private fun getVolIncr(promptCount: Int): Float {
        return .5F
    }

    override fun getSpeechEventsTrigger(): Int {
        return if(playCount == 1) 1 else 0
    }

    override fun getSpeechEventsCount(): Int {
        return 1
    }
}
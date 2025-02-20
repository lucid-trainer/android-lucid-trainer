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

        //for a prompt routine, keep around a minute in total length as they are chained and can be blocked if one
        //is running and another tries to start. The minimum time between prompts is managed in the PromptMonitor
        //SECONDS_BETWEEN_PROMPTS setting

        if(promptCount == 1) {
            routine.add(Sound(0, 5, "$promptDir/name.ogg", fileVolAdj))
        }

        if(promptCount <= 2) {
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
        return .55F
    }

    override fun getSpeechEventsTrigger(): Int {
        return if(promptCount == 1) 1 else 0
    }

    override fun getSpeechEventsCount(): Int {
        return 1
    }

    override fun getSpeechEventsTimeBetween() : Int {
        return 1
    }
}
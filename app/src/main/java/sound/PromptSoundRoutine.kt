package sound

import utils.FileManager

class PromptSoundRoutine(
    override var playCount: Int, override var bgRawId: Int, override var endBgRawId: Int,
    override var bgVolume: Float, override var altBgVolume: Float, override var fgVolume: Float,
    override val eventLabel: String, override var bgLabel: String, override var endBgLabel: String,
    override val fgLabel: String = "PROMPT", private val promptCount: Int = 1
) : SoundRoutine {

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

        routine.add(Sound(0, 3, "$startDir/prompt_start_short.ogg", ))

        //this clip will play a little louder as the count increases in a prompt session
        val promptInterFile = "$startDir/prompt_intermit.ogg"
        routine.add(Sound(0, 3, promptInterFile, 0F, getVolIncr(promptCount)))

        val promptFile = "$dir/prompt_$playCount.ogg"
        routine.add(Sound(0, 3, promptFile, getVolIncr(promptCount)))

        routine.add(Sound(0, 0, "$startDir/silence.ogg"))

        return routine
    }

    private fun getVolIncr(promptCount: Int): Float {
        return when(promptCount) {
            0 -> .8F
            1 -> 1.2F
            2 -> 1.6F
            3 -> 2F
            4 -> 2.4F
            else -> 2.8F
        }
    }
}
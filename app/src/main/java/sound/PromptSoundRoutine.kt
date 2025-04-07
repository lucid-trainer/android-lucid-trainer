package sound

import utils.FileManager

interface PromptSoundRoutine  : SoundRoutine {

    companion object  {
        const val promptDir = "$ROOT_DIR/$PROMPT_DIR"

        fun getVolAdjustSound(num : Int) : String {
            return "$promptDir/vol_adjust_$num.ogg"
        }
    }

    val promptCount: Int

    //we always want to start a prompt by resetting the background
    override fun fadeDownBg() : Boolean {
        return true
    }

    override fun getSpeechEventsCount(): Int {
        return 1
    }

    override fun getSpeechEventsTimeBetween() : Int {
        return 1
    }
}
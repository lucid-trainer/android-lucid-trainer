package sound

import utils.FileManager

interface PromptSoundRoutine  : SoundRoutine {

    companion object  {
        const val promptDir = "$ROOT_DIR/$PROMPT_DIR"

        fun getVolAdjustSound(num : Int) : String {
            return "$promptDir/vol_adjust.ogg"
        }
    }

    val promptCount: Int

    //we always want to start a prompt by resetting the background
    override fun fadeDownBg() : Boolean {
        return true
    }

}
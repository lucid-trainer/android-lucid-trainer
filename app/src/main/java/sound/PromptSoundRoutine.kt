package sound

import utils.FileManager

interface PromptSoundRoutine  : SoundRoutine {

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
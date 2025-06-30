package sound

import android.util.Log
import utils.FileManager

class SSILDPromptSoundRoutine (
    override var playTier: Int, override var bgRawId: Int, override var bgVolume: Float,
    override var altBgVolume: Float, override var fgVolume: Float, override val eventLabel: String,
    override var bgLabel: String, override val theme: String, override val fgLabel: String = "PROMPT", override val promptCount: Int = 1
) : PromptSoundRoutine {

    private val fileManager = FileManager.getInstance()!!

    private val ssildDir = "$ROOT_DIR/$SSILD_DIR"
    private val promptDir = PromptSoundRoutine.promptDir

    override fun getStartSounds(): List<String> {
        return emptyList()
    }

    override fun getAltBGSounds(): List<String> {

        val bgSounds : MutableList<String> = emptyList<String>().toMutableList()

        var altBgFile =
            fileManager.getFilesFromDirectory(promptDir).filter { it.startsWith("alt_background_") }
                .shuffled().last()

        bgSounds.add("$promptDir/$altBgFile")

        return bgSounds

    }

    override fun getRoutine(): List<Sound> {
        val routine : MutableList<Sound> = emptyList<Sound>().toMutableList()

        //for a prompt routine, keep around a minute in total length as they are chained and can be blocked if one
        //is running and another tries to start. The minimum time between prompts is managed in the PromptMonitor
        //SECONDS_BETWEEN_PROMPTS setting

        if(promptCount == 1) {
            routine.add(Sound(0, 3, "$ssildDir/ssild_sight_short.ogg", 2F))
        } else if(promptCount == 2) {
            routine.add(Sound(0, 3, "$ssildDir/ssild_hear_short.ogg", 2F))
        } else if(promptCount == 3) {
            routine.add(Sound(0, 3, "$ssildDir/ssild_feel_short.ogg", 2F))
        }

        routine.add(Sound(0, 7, "$promptDir/silence.ogg", 0F,"ON"))

        routine.add(Sound(0, 0, "$promptDir/foreground.ogg"))

        return routine
    }

}
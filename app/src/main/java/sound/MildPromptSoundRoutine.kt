package sound

import android.util.Log
import utils.FileManager

class MildPromptSoundRoutine (
    override var playTier: Int, override var bgRawId: Int, override var bgVolume: Float,
    override var altBgVolume: Float, override var fgVolume: Float, override val eventLabel: String,
    override var bgLabel: String, override val theme: String, override val fgLabel: String = "PROMPT", override val promptCount: Int = 1
) : PromptSoundRoutine {

    private val fileManager = FileManager.getInstance()!!

    private val promptDir = PromptSoundRoutine.promptDir

    override fun getStartSounds(): List<String> {
        return emptyList()
    }

    override fun getAltBGSounds(): List<String> {

        val bgSounds : MutableList<String> = emptyList<String>().toMutableList()

        val altBgFile =
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
            val promptFile =
                fileManager.getFilesFromDirectory(promptDir).filter { it.startsWith("random_") }
                    .shuffled().last()

            val fileVolAdjust = if(playTier == 1) .9F else if(playTier == 2) 1F else 1.2F
            routine.add(Sound(0, 0, "$promptDir/$promptFile",false, fileVolAdjust))
        }

        routine.add(Sound(0, 7, "$promptDir/silence.ogg"))

        val fileVolAdjust = if(playTier == 1) .8F else if(playTier == 2) .9F else 1F
        Log.d("MainActivity", "For prompt routine - playTier = $playTier, fileVolAdjust = $fileVolAdjust")

        routine.add(Sound(0, 0, "$promptDir/foreground.ogg", false, fileVolAdjust))

        return routine
    }

}
package sound

import android.util.Log
import utils.FileManager

class MILDSoundRoutine(override var playTier: Int, override var bgRawId: Int, override var bgVolume: Float,
                       override var altBgVolume: Float, override var fgVolume: Float, override val eventLabel : String,
                       override var bgLabel : String, override var theme: String, override val fgLabel : String = "MILD"

) : SoundRoutine {

    private val fileManager = FileManager.getInstance()!!

    private val promptDir = PromptSoundRoutine.promptDir
    private val mildDir = "$ROOT_DIR/$MILD_DIR"

    private val clipCnt = fileManager.getPromptClipCount()
    private val mildClipFile = "mild_clip_$clipCnt.ogg"

    override fun getRoutine(): List<Sound> {
        val routine : MutableList<Sound> = emptyList<Sound>().toMutableList()


       routine.add(Sound(0, 120, "$mildDir/instruction.ogg"))
        //Log.d("MainActivity", "mildDir=$mildDir, count = ${fileManager.getFilesFromDirectory(mildDir).size} ")

        addForegroundSounds(routine)

        return routine
    }

    override fun getStartSounds(): List<String> {
        return emptyList()
    }

    override fun getAltBGSounds(): List<String> {

        val bgSounds : MutableList<String> = emptyList<String>().toMutableList()

        val files = fileManager.getFilesFromDirectory(promptDir).filter { it.startsWith("alt_background_") }.shuffled()

        for (file in files) {
            bgSounds.add("$promptDir/$file")
        }

        return bgSounds
    }

    override fun executeAfterPlay() {
        fileManager.updatePromptClipCount()
    }

    private fun addForegroundSounds(routine: MutableList<Sound>) {

        routine.add(Sound(0, 10, "$mildDir/$mildClipFile", 1.2F))

        routine.add(Sound(0, 30, "$promptDir/foreground.ogg", 0F,"ON"))

        for(i in 1..6) {
            if(i == 2) {
                routine.add(Sound(0, 5, "$promptDir/prompt.ogg", 1.2F))
            }
            routine.add(Sound(0, 30, "$promptDir/foreground.ogg"))
        }
    }
}
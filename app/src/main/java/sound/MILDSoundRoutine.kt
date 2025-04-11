package sound

import android.util.Log
import utils.FileManager

class MILDSoundRoutine(override var playTier: Int, override var bgRawId: Int, override var bgVolume: Float,
                       override var altBgVolume: Float, override var fgVolume: Float, override val eventLabel : String,
                       override var bgLabel : String, override var theme: String, override val fgLabel : String = "MILD"

) : SoundRoutine {

    private val fileManager = FileManager.getInstance()!!

    private val promptDir = PromptSoundRoutine.promptDir

    override fun getRoutine(): List<Sound> {
        val routine : MutableList<Sound> = emptyList<Sound>().toMutableList()

        val mildDir = "$ROOT_DIR/$MILD_DIR"
        routine.add(Sound(0, 180, "$mildDir/instruction.ogg"))
        //Log.d("MainActivity", "mildDir=$mildDir, count = ${fileManager.getFilesFromDirectory(mildDir).size} ")

        addForegroundSounds(routine)

        return routine
    }

    override fun getStartSounds(): List<String> {
        val startSounds : MutableList<String> = emptyList<String>().toMutableList()

        startSounds.add("$ROOT_DIR/$THEMES_DIR/$theme/$START_DIR/silence.ogg")

        return startSounds
    }

    override fun getAltBGSounds(): List<String> {

        val bgSounds : MutableList<String> = emptyList<String>().toMutableList()

        val altBgFile =
            fileManager.getFilesFromDirectory(promptDir).filter { it.startsWith("alt_background_") }
                .shuffled().last()

        bgSounds.add("$promptDir/$altBgFile")

        return bgSounds
    }

    private fun addForegroundSounds(routine: MutableList<Sound>) {

        for(i in 1..10) {
            if(i == 2) {
                routine.add(Sound(0, 5, "$promptDir/vol_adjust_1.ogg"))
            } else if(i == 4) {
                routine.add(Sound(0, 5, "$promptDir/vol_adjust_2.ogg"))
            } else if (i == 5) {
                routine.add(Sound(0, 5, "$promptDir/vol_adjust_3.ogg"))
            }
            routine.add(Sound(0, 30, "$promptDir/foreground.ogg"))
        }
    }
}
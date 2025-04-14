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
        routine.add(Sound(0, 150, "$mildDir/instruction.ogg"))
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

        var fileVolAdjust = if(playTier == 1) .9F else if(playTier == 2) 1F else 1.2F

        fileVolAdjust = if(playTier == 1) .8F else if(playTier == 2) .9F else 1F
        Log.d("MainActivity", "For prompt routine - playTier = $playTier, fileVolAdjust = $fileVolAdjust")

        for(i in 1..10) {
            if(i == 2) {
                routine.add(Sound(0, 5, "$promptDir/vol_adjust_1.ogg", false, 1.1F))
            } else if(i == 4) {
                routine.add(Sound(0, 5, "$promptDir/vol_adjust_2.ogg", false, .9F))
            } else if (i == 5) {
                routine.add(Sound(0, 5, "$promptDir/vol_adjust_3.ogg", false, .75F))
            }
            routine.add(Sound(0, 30, "$promptDir/foreground.ogg", false, getVolAdjust(i)))
        }
    }

    override fun getVolAdjust(fileCount: Int): Float {

        return when {
            fileCount <= 1 -> .9F
            fileCount <= 2 -> .8F
            fileCount <= 3 -> .7F
            fileCount <= 4 -> .65F
            fileCount <= 5 -> .6F
            fileCount <= 6 -> .55F
            fileCount <= 7 -> .5F
            fileCount <= 8 -> .45F
            else -> .35F
        }
    }


    //we always want to start a prompt by resetting the background
    override fun fadeDownBg() : Boolean {
        return true
    }
}
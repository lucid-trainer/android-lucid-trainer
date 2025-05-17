package sound

import android.util.Log
import utils.FileManager

class WILDSoundRoutine(override var playTier: Int, override var bgRawId: Int, override var bgVolume: Float,
    override var altBgVolume: Float, override var fgVolume: Float, override val eventLabel : String,
    override var bgLabel : String, override var theme: String, override val fgLabel : String = "WILD"
) : SoundRoutine {

    private val fileManager = FileManager.getInstance()!!

    override fun getStartSounds(): List<String> {
        val startSounds : MutableList<String> = emptyList<String>().toMutableList()

        startSounds.add("$ROOT_DIR/$THEMES_DIR/$theme/$START_DIR/start.ogg")

        return startSounds
    }

    override fun getAltBGSounds(): List<String> {
        var altBGSounds : MutableList<String> = emptyList<String>().toMutableList()

        val dir = "/$ROOT_DIR/$THEMES_DIR/$theme/$ALT_BACKGROUND_DIR"

        //Log.d("MainActivity", "bg dir = $dir")

        val files = fileManager.getFilesFromDirectory(dir).shuffled().slice(0..9)
        //Log.d("MainActivity", "bg files = $files")
        for (i in 0..9) {
            altBGSounds.add("$ROOT_DIR/$THEMES_DIR/$theme/$ALT_BACKGROUND_DIR/${files[i]}")
        }

        return altBGSounds
    }

    override fun getRoutine(): List<Sound> {
        val routine : MutableList<Sound> = emptyList<Sound>().toMutableList()

        addForegroundSounds(routine)

        addPromptSound(routine)

        addClipSounds(routine)

        return routine
    }

    private fun addForegroundSounds(routine: MutableList<Sound>) {
        var dir = "$ROOT_DIR/$THEMES_DIR/$theme/$FOREGROUND_DIR"
        val promptDir = "$ROOT_DIR/$PROMPT_DIR"

        routine.add(Sound(0, 30, "$promptDir/silence.ogg"))

        val limit = 5
        val files = fileManager.getUnusedFilesFromDirectory(dir, limit).shuffled().slice(0 until limit)

        for (file in files) {
            routine.add(Sound(0, 20, "$dir/$file"))
        }

        fileManager.addFilesUsed(dir, files)
    }

    private fun addClipSounds(routine: MutableList<Sound>) {
        //add a longer more distinct main sound clip towards the end and adjust volume on it
        var startDir = "$ROOT_DIR/$THEMES_DIR/$theme"

        //start with a radio tuning sound
        routine.add(Sound(0, 0, "$startDir/start/tune.ogg"))

        val clip1File = fileManager.getUnusedFilesFromDirectory("$startDir/$CLIP1_DIR", 1).shuffled().last()
        routine.add(Sound(0, 20, "$startDir/$CLIP1_DIR/$clip1File"))
        fileManager.addFileUsed("$startDir/$CLIP1_DIR", clip1File)

        val clip2File = fileManager.getUnusedFilesFromDirectory("$startDir/$CLIP2_DIR", 1).shuffled().last()
        routine.add(Sound(0, 20, "$startDir/$CLIP2_DIR/$clip2File"))
        fileManager.addFileUsed("$startDir/$CLIP2_DIR", clip2File)
    }

    private fun addPromptSound(routine: MutableList<Sound>) {
        //add a prompt near start of the the routine
        var promptDir = "$ROOT_DIR/$PROMPT_DIR"
        val promptFile =
            fileManager.getFilesFromDirectory(promptDir).filter { it.startsWith("random_") }
                .shuffled().last()
        routine.add(Sound(0, 20, "$promptDir/$promptFile"))
    }
}
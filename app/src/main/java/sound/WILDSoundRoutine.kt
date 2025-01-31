package sound

import android.util.Log
import utils.FileManager

class WILDSoundRoutine(override var playCount: Int, override var bgRawId: Int, override var endBgRawId: Int,
                       override var bgVolume: Float, override var altBgVolume: Float, override var fgVolume: Float,
                       override val eventLabel : String, override var bgLabel : String, override var endBgLabel : String,
                       override var theme: String, override val fgLabel : String = "WILD"
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

        Log.d("MainActivity", "bg dir = $dir")

        val files = fileManager.getFilesFromDirectory(dir).shuffled().slice(0..9)
        Log.d("MainActivity", "bg files = $files")
        for (i in 0..9) {
            altBGSounds.add("$ROOT_DIR/$THEMES_DIR/$theme/$ALT_BACKGROUND_DIR/${files[i]}")
        }

        return altBGSounds
    }

    override fun getRoutine(): List<Sound> {
        val routine : MutableList<Sound> = emptyList<Sound>().toMutableList()

        addForegroundSounds(routine)

        addClipSound(routine)

        addPromptSound(routine)

        return routine
    }

    private fun addForegroundSounds(routine: MutableList<Sound>) {
        var dir = "$ROOT_DIR/$THEMES_DIR/$theme/$FOREGROUND_DIR"
        val promptDir = "$ROOT_DIR/$PROMPT_DIR"

        routine.add(Sound(0, 10, "$promptDir/silence.ogg"))

        val limit = when(playCount) {
            2 -> 10
            3 -> 20
            else -> 6
        }

        Log.d("MainActivity", "WILD fg limit = $limit for $playCount")

        val files = fileManager.getUnusedFilesFromDirectory(dir, limit).shuffled().slice(0 until limit)

        var i = 1;
        for (file in files) {
            routine.add(Sound(0, 20, "$dir/$file",0F, getVolAdjust(i)))
            i++
        }

        fileManager.addFilesUsed(dir, files)
    }

    private fun addClipSound(routine: MutableList<Sound>) {
        //add a longer more distinct main sound clip towards the end and adjust volume on it
        var startDir = "$ROOT_DIR/$THEMES_DIR/$theme"

        //start with a radio tuning sound
        routine.add(2, Sound(0, 0, "$startDir/start/tune.ogg"))

        val clipFile = fileManager.getUnusedFilesFromDirectory("$startDir/$CLIP_DIR", 1).shuffled().last()
        routine.add(3, Sound(0, 20, "$startDir/$CLIP_DIR/$clipFile", .95F))

        fileManager.addFileUsed("$startDir/$CLIP_DIR", clipFile)
    }

    private fun addPromptSound(routine: MutableList<Sound>) {
        //add a prompt near start of the the routine
        var dir = "$ROOT_DIR/$PROMPT_DIR"
        routine.add(4, Sound(0, 20, "$dir/prompt.ogg"))
    }

}
package sound

import android.util.Log
import com.lucidtrainer.R
import utils.FileManager

class MILDSoundRoutine(override var playCount: Int, override var bgRawId: Int, override var endBgRawId: Int,
                       override var bgVolume: Float, override var altBgVolume: Float, override var fgVolume: Float,
                       override val eventLabel : String, override var bgLabel : String, override var endBgLabel : String,
                       override var theme: String, override val fgLabel : String = "MILD"

) : SoundRoutine {

    private val fileManager = FileManager.getInstance()!!

    override fun getRoutine(): List<Sound> {
        val routine : MutableList<Sound> = emptyList<Sound>().toMutableList()

        val mildDir = "$ROOT_DIR/$MILD_DIR"
        routine.add(Sound(0, 30, "$mildDir/first_instruction.ogg", 0F, 1.5F))
        routine.add(Sound(0, 30, "$mildDir/second_instruction.ogg", 0F, 1.5F))

        addStartSound(routine)

        addForegroundSounds(routine)

        if(playCount > 1) {
            addClipSound(routine)
            addPromptSound(routine)
        }

        return routine
    }

    override fun getStartSounds(): List<String> {
        return emptyList()
    }

    override fun getAltBGSounds(): List<String> {
        val altBGSounds : MutableList<String> = emptyList<String>().toMutableList()

        val dir = "/$ROOT_DIR/$THEMES_DIR/$theme/$ALT_BACKGROUND_DIR"

        val files = fileManager.getFilesFromDirectory(dir).shuffled().slice(0..9)

        for (i in 0..9) {
            altBGSounds.add("$dir/${files[i]}")
        }

        return altBGSounds
    }

    private fun addForegroundSounds(routine: MutableList<Sound>) {

        val dir = "$ROOT_DIR/$THEMES_DIR/$theme/$FOREGROUND_DIR"

        val limit = when(playCount) {
            2 -> 10
            3 -> 20
            else -> 6
        }

        Log.d("MainActivity", "MILD fg limit = $limit")

        val files = fileManager.getUnusedFilesFromDirectory(dir, limit).shuffled().slice(0 until limit)

        //Log.d("WildRoutine", "used fg ${FileMonitor.getUnusedFilesFromDirectory(dir, 8).size}")

        var i = 1
        for (file in files) {
            routine.add(Sound(0, 20, "$dir/$file", 0F, getVolAdjust(i)))
            i++
        }

        fileManager.addFilesUsed(dir, files)
    }

    private fun addStartSound(routine: MutableList<Sound>) {
        routine.add(Sound(0, 20, "$ROOT_DIR/$THEMES_DIR/$theme/$START_DIR/start.ogg",0F, 1.2F))
    }

    private fun addPromptSound(routine: MutableList<Sound>) {
        //add a prompt near start of the the routine
        var dir = "$ROOT_DIR/$PROMPT_DIR"

        val file = fileManager.getFilesFromDirectory(dir).filter{it.startsWith("prompt")}.shuffled().last()

        routine.add(6, Sound(0, 20, "$dir/$file"))
    }

    private fun addClipSound(routine: MutableList<Sound>) {
        //add a longer more distinct main sound clip towards the end and adjust volume on it
        var startDir = "$ROOT_DIR/$THEMES_DIR/$theme"

        //start with a radio tuning sound
        routine.add(4, Sound(0, 0, "$startDir/start/tune.ogg"))

        val clipFile = fileManager.getUnusedFilesFromDirectory("$startDir/$CLIP_DIR", 1).shuffled().last()
        routine.add(5, Sound(0, 20, "$startDir/$CLIP_DIR/$clipFile", .85F))

        fileManager.addFileUsed("$startDir/$CLIP_DIR", clipFile)
    }
}
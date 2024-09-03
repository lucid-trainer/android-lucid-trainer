package sound

import android.util.Log
import utils.FileManager

class WILDSoundRoutine(override var playCount: Int, override var bgRawId: Int, override var endBgRawId: Int,
                       override var bgVolume: Float, override var altBgVolume: Float, override var fgVolume: Float,
                       override val eventLabel : String, override var bgLabel : String, override var endBgLabel : String,
                       override val fgLabel : String = "WILD",
) : SoundRoutine {

    private val fileManager = FileManager.getInstance()!!

    companion object {
        const val ROOT_DIR = "lt_sounds"
        const val FOREGROUND_DIR = "fg"
        const val ALT_BACKGROUND_DIR = "bg"
        const val PROMPT_DIR = "prompt"
        const val START_DIR = "start"
        const val CLIP_DIR = "main"
    }

    override fun getStartSounds(): List<String> {
        val startSounds : MutableList<String> = emptyList<String>().toMutableList()

        startSounds.add("$ROOT_DIR/$START_DIR/start.ogg")

        return startSounds
    }

    override fun getAltBGSounds(): List<String> {
        var altBGSounds : MutableList<String> = emptyList<String>().toMutableList()

        val dir = "/$ROOT_DIR/$ALT_BACKGROUND_DIR"

        val files = fileManager.getFilesFromDirectory(dir).shuffled().slice(0..9)

        for (i in 0..9) {
            altBGSounds.add("$ROOT_DIR/$ALT_BACKGROUND_DIR/${files[i]}")
        }

        return altBGSounds
    }

    override fun getRoutine(): List<Sound> {
        val routine : MutableList<Sound> = emptyList<Sound>().toMutableList()

        addForegroundSounds(routine)

        addPromptSound(routine)

        addClipSound(routine)

        return routine
    }

    private fun addForegroundSounds(routine: MutableList<Sound>) {
        var dir = "$ROOT_DIR/$FOREGROUND_DIR"

        val limit = if(playCount > 1) 15 else 9

        val files = fileManager.getUnusedFilesFromDirectory(dir, limit).shuffled().slice(0 until limit)

        for (file in files) {
            routine.add(Sound(0, 20, "$dir/$file"))
        }

        fileManager.addFilesUsed(dir, files)
    }

    private fun addPromptSound(routine: MutableList<Sound>) {
        //add a prompt near start of the the routine
        var dir = "$ROOT_DIR/$PROMPT_DIR"

        val file = fileManager.getFilesFromDirectory(dir).shuffled().last()
        val interfile = "$ROOT_DIR/$START_DIR/intermit.ogg"

        routine.add(3, Sound(0, 20, "$dir/$file"))
        Log.d("MainActivity", "add prompt file$dir/$file to routine")

        for(i in 1..routine.size) {
            if(i % 3 == 0) {
                routine.add(i-1, Sound(0, 20, "$interfile"))
                Log.d("MainActivity", "add $interfile to ${i-1} of routine")
            }
        }

        //add a few to extend out at the end
        val limit = if(playCount > 1) 5 else 3
        for(i in 1..limit) {
            val delayAfter = 40 + i*10
            routine.add(Sound(0, delayAfter, "$interfile"))
            Log.d("MainActivity", "add $i $interfile to end of routine")
        }
    }

    private fun addClipSound(routine: MutableList<Sound>) {
        //add a longer more distinct main sound clip towards the end and adjust volume on it
        var dir = "$ROOT_DIR/$CLIP_DIR"

        //start with a radio tuning sound
        routine.add(6, Sound(0, 0, "$ROOT_DIR/start/tune.ogg"))

        val clipFile = fileManager.getUnusedFilesFromDirectory(dir, 1).shuffled().last()
        routine.add(7, Sound(0, 20, "$dir/$clipFile", 1F))

        fileManager.addFileUsed(dir, clipFile)
    }

}
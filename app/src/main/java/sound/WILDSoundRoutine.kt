package sound

import utils.FileManager

class WILDSoundRoutine(override var playCount: Int, override var bgRawId: Int, override var endBgRawId: Int,
                       override var bgVolume: Float, override var altBgVolume: Float, override var fgVolume: Float,
                       override val eventLabel : String, override var bgLabel : String, override var endBgLabel : String,
                       override val fgLabel : String = "WILD",
) : SoundRoutine {

    private val fileManager = FileManager.getInstance()!!

    companion object {
        const val ROOT_DIR = "wild"
        const val FOREGROUND_DIR = "fg"
        const val ALT_BACKGROUND_DIR = "bg"
        const val PROMPT_DIR = "prompt"
        const val START_DIR = "start"
        const val CLIP_DIR = "main"
    }

    override fun getStartSounds(): List<String> {
        val startSounds : MutableList<String> = emptyList<String>().toMutableList()

        startSounds.add("$ROOT_DIR/$START_DIR/wild_start.ogg")

        return startSounds
    }

    override fun getAltBGSounds(): List<String> {
        var altBGSounds : MutableList<String> = emptyList<String>().toMutableList()

        val dir = "/$ROOT_DIR/$ALT_BACKGROUND_DIR"

        val files = fileManager.getFilesFromDirectory(dir).shuffled().slice(0..8)

        for (i in 0..8) {
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

        val files = fileManager.getUnusedFilesFromDirectory(dir, 8).shuffled().slice(0..7)

        //Log.d("WildRoutine", "used fg ${FileMonitor.getUnusedFilesFromDirectory(dir, 8).size}")

        for (file in files) {
            routine.add(Sound(0, 20, "$dir/$file"))
        }

        fileManager.addFilesUsed(dir, files)
    }

    private fun addPromptSound(routine: MutableList<Sound>) {
        //add a prompt near start of the the routine
        var dir = "$ROOT_DIR/$PROMPT_DIR"

        val file = fileManager.getFilesFromDirectory(dir).shuffled().last()
        routine.add(3, Sound(0, 20, "$dir/$file"))
    }

    private fun addClipSound(routine: MutableList<Sound>) {
        //add a longer more distinct main sound clip towards the end and adjust volume on it
        var dir = "$ROOT_DIR/$CLIP_DIR"

        //start with a radio tuning sound
        routine.add(6, Sound(0, 0, "$ROOT_DIR/start/wild_tune.ogg"))

        //Log.d("WildRoutine ", "unused clips size ${FileMonitor.getUnusedFilesFromDirectory(dir, 1).size}")
        //Log.d("WildRoutine ", "unused clips ${FileMonitor.getUnusedFilesFromDirectory(dir, 1)}")

        val clipFile = fileManager.getUnusedFilesFromDirectory(dir, 1).shuffled().last()
        routine.add(7, Sound(0, 20, "$dir/$clipFile", 1F))

        fileManager.addFileUsed(dir, clipFile)
    }

}
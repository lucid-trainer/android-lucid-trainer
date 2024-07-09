package sound

import android.util.Log
import presentation.MainActivity.Companion.EVENT_LABEL_REM
import utils.FileManager

class WILDPromptSoundRoutine(override var playCount: Int, override var bgRawId: Int, override var endBgRawId: Int,
                             override var bgVolume: Float, override var altBgVolume: Float, override var fgVolume: Float,
                             override val eventLabel : String, override var bgLabel : String, override var endBgLabel : String,
                             override val fgLabel : String = "WILD",
) : SoundRoutine {

    private val fileManager = FileManager.getInstance()!!

    companion object {
        const val ROOT_DIR = "wild"
        const val START_DIR = "start"
        const val PROMPT_DIR = "prompt"
    }

    override fun getStartSounds(): List<String> {
        val startSounds : MutableList<String> = emptyList<String>().toMutableList()

        val files= fileManager.getFilesFromDirectory("$ROOT_DIR/$START_DIR")
            .filter{it.contains("prompt") }.shuffled()

        if(files.isNotEmpty()) {
            val file = files.last()
            Log.d("WildPrompt", "file=$file")
            startSounds.add("$ROOT_DIR/$START_DIR/$file")
        }

        return startSounds
    }

    override fun getAltBGSounds(): List<String> {
        return emptyList()
    }

    override fun getRoutine(): List<Sound> {
        val routine : MutableList<Sound> = emptyList<Sound>().toMutableList()

        val dir = "$ROOT_DIR/$PROMPT_DIR"

        val file = fileManager.getFilesFromDirectory(dir).shuffled().last()
        routine.add(Sound(0, 10, "$dir/$file"))

        return routine
    }
}
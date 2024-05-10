package sound

import utils.FileMonitor

class WILDPromptSoundRoutine(override var playCount: Int, override var bgRawId: Int, override var endBgRawId: Int,
                             override var bgVolume: Float, override var altBgVolume: Float, override var fgVolume: Float,
                             override val eventLabel : String, override var bgLabel : String, override var endBgLabel : String,
                             override val fgLabel : String = "WILD",
) : SoundRoutine {

    companion object {
        const val ROOT_DIR = "wild"
        const val PROMPT_DIR = "prompt"
    }

    override fun getStartSounds(): List<String> {
        val startSounds : MutableList<String> = emptyList<String>().toMutableList()

        startSounds.add("wild/start/prompt_notice.ogg")

        return startSounds
    }

    override fun getAltBGSounds(): List<String> {
        return emptyList()
    }

    override fun getRoutine(): List<Sound> {
        val routine : MutableList<Sound> = emptyList<Sound>().toMutableList()

        val dir = "$ROOT_DIR/$PROMPT_DIR"

        val file = FileMonitor.getFilesFromDirectory(dir).shuffled().last()
        routine.add(Sound(0, 10, "$dir/$file"))

        return routine
    }
}
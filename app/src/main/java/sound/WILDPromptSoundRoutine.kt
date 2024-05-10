package sound

import utils.FileMonitor

class WILDPromptSoundRoutine(override var playCount: Int, override var bgRawId: Int, override var endBgRawId: Int,
                             override var bgVolume: Float, override var altBgVolume: Float, override var fgVolume: Float,
                             override val eventLabel : String, override var bgLabel : String, override var endBgLabel : String,
                             override val fgLabel : String = "WILD",
) : SoundRoutine {

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

        val file = FileMonitor.getFilesFromDirectory("prompt").shuffled().last()

        routine.add(Sound(0, 10, "wild/prompt/$file"))

        return routine
    }
}
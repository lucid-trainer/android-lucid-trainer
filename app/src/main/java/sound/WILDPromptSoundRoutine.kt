package sound

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
        //val limit = if(playCount > 2) 2 else playCount
        //val randomDelay = (20..40).shuffled().last()

//        val cntrs = (1..limit).shuffled()
//        for (index in cntrs.indices) {
//            val cntr = cntrs[index]
        val cnt = (1..3).shuffled().last()
        routine.add(Sound(0, 10, "wild/prompt/prompt_$cnt.ogg"))
//        }

        return routine
    }
}
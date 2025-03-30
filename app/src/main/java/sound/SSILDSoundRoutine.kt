package sound

import com.lucidtrainer.R

class SSILDSoundRoutine(override var playCount: Int, override var bgRawId: Int, override var endBgRawId: Int,
                        override var bgVolume: Float, override var altBgVolume: Float, override var fgVolume: Float,
                        override val eventLabel : String, override var bgLabel : String, override var endBgLabel : String,
                        override val theme: String = "", override val fgLabel : String = "SSILD"

) : SoundRoutine {

    override fun getStartSounds(): List<String> {
        return emptyList()
    }

    override fun getAltBGSounds(): List<String> {
        return emptyList()
    }

    override fun getRoutine(): List<Sound> {
        val routine : MutableList<Sound> = emptyList<Sound>().toMutableList()

        //short routine
        routine.add(Sound(R.raw.ssild_intro, 1))
        routine.add(Sound(R.raw.ssild_round_1, 3))
        routine.add(Sound(R.raw.ssild_hear, 3))
        routine.add(Sound(R.raw.ssild_feel, 3))
        routine.add(Sound(R.raw.ssild_round_2, 3))
        routine.add(Sound(R.raw.ssild_hear, 3))
        routine.add(Sound(R.raw.ssild_feel, 3))

        if(playCount > 2) {
            routine.add(Sound(R.raw.ssild_round_3, 3))

            routine.add(Sound(R.raw.ssild_hear, 3))
            routine.add(Sound(R.raw.ssild_feel, 3))
        }

        if(playCount > 3) {
            routine.add(Sound(R.raw.ssild_round_4, 3))
            routine.add(Sound(R.raw.ssild_hear, 3))
            routine.add(Sound(R.raw.ssild_feel, 3))
        }

        //long routine
        routine.add(Sound(R.raw.ssild_long_cycles, 0))
        routine.add(Sound(R.raw.ssild_round_1, 25))
        routine.add(Sound(R.raw.ssild_hear, 25))
        routine.add(Sound(R.raw.ssild_feel, 25))
        routine.add(Sound(R.raw.ssild_round_2, 25))
        routine.add(Sound(R.raw.ssild_hear, 25))
        routine.add(Sound(R.raw.ssild_feel, 25))

        if(playCount > 2) {
            routine.add(Sound(R.raw.ssild_round_3, 25))
            routine.add(Sound(R.raw.ssild_hear, 25))
            routine.add(Sound(R.raw.ssild_feel, 25))
        }

        if(playCount > 3) {
            routine.add(Sound(R.raw.ssild_round_4, 25))
            routine.add(Sound(R.raw.ssild_hear, 25))
            routine.add(Sound(R.raw.ssild_feel, 25))
        }

        return routine
    }
}
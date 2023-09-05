package sound

import com.lucidtrainer.R

class SSILDSoundRoutine(override var repetition: Int, override var bgRawId: Int, override var endBgRawId: Int,
                        override val eventLabel : String, override var bgLabel : String,
                        override var endBgLabel : String, override val fgLabel : String = "SSILD") : SoundRoutine {

    override fun getRoutine(): List<Sound> {
        val routine : MutableList<Sound> = emptyList<Sound>().toMutableList()

        //short routine
        routine.add(Sound(R.raw.ssild_intro, 1))
        routine.add(Sound(R.raw.ssild_round_1, 2))
        routine.add(Sound(R.raw.ssild_hear, 2))
        routine.add(Sound(R.raw.ssild_feel, 2))
        routine.add(Sound(R.raw.ssild_round_2, 2))
        routine.add(Sound(R.raw.ssild_hear, 2))
        routine.add(Sound(R.raw.ssild_feel, 2))

        if(repetition > 2) {
            routine.add(Sound(R.raw.ssild_round_3, 2))

            routine.add(Sound(R.raw.ssild_hear, 2))
            routine.add(Sound(R.raw.ssild_feel, 2))
        }

        if(repetition > 3) {
            routine.add(Sound(R.raw.ssild_round_4, 2))
            routine.add(Sound(R.raw.ssild_hear, 2))
            routine.add(Sound(R.raw.ssild_feel, 2))
        }

        //long routine
        routine.add(Sound(R.raw.ssild_long_cycles, 0))
        routine.add(Sound(R.raw.ssild_round_1, 25))
        routine.add(Sound(R.raw.ssild_hear, 25))
        routine.add(Sound(R.raw.ssild_feel, 25))
        routine.add(Sound(R.raw.ssild_round_2, 25))
        routine.add(Sound(R.raw.ssild_hear, 25))
        routine.add(Sound(R.raw.ssild_feel, 25))

        if(repetition > 2) {
            routine.add(Sound(R.raw.ssild_round_3, 25))
            routine.add(Sound(R.raw.ssild_hear, 25))
            routine.add(Sound(R.raw.ssild_feel, 25))
        }

        if(repetition > 3) {
            routine.add(Sound(R.raw.ssild_round_4, 25))
            routine.add(Sound(R.raw.ssild_hear, 25))
            routine.add(Sound(R.raw.ssild_feel, 25))
        }

        return routine
    }
}
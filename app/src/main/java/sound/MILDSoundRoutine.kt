package sound

import com.lucidtrainer.R

class MILDSoundRoutine(override var repetition: Int, override var bgRawId: Int, override var endBgRawId: Int,
                       override val eventLabel : String, override var bgLabel : String,
                       override var endBgLabel : String, override val fgLabel : String = "MILD") : SoundRoutine {

    override fun getRoutine(): List<Sound> {
        val routine : MutableList<Sound> = emptyList<Sound>().toMutableList()

        routine.add(Sound(R.raw.mild_intro, 30))
        routine.add(Sound(R.raw.mild_dream_1, 60))
        routine.add(Sound(R.raw.mild_sign, 25))
        routine.add(Sound(R.raw.mild_replay, 50))

        if(repetition > 2) {
            routine.add(Sound(R.raw.mild_dream_2, 60))
            routine.add(Sound(R.raw.mild_sign, 25))
            routine.add(Sound(R.raw.mild_replay, 50))
        }

        if(repetition > 3) {
            routine.add(Sound(R.raw.mild_dream_3, 60))
            routine.add(Sound(R.raw.mild_sign, 25))
            routine.add(Sound(R.raw.mild_replay, 50))
        }

        routine.add(Sound(R.raw.mild_finish, 25))

        return routine
    }
}
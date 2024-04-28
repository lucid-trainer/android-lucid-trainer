package sound

import com.lucidtrainer.R

class MILDSoundRoutine(override var playCount: Int, override var bgRawId: Int, override var endBgRawId: Int,
                       override var bgVolume: Float, override var altBgVolume: Float, override var fgVolume: Float,
                       override val eventLabel : String, override var bgLabel : String, override var endBgLabel : String,
                       override val fgLabel : String = "MILD"

) : SoundRoutine {

    override fun getStartSounds(): List<String> {
        return emptyList()
    }

    override fun getAltBGSounds(): List<String> {
        return emptyList()
    }

    override fun getRoutine(): List<Sound> {
        val routine : MutableList<Sound> = emptyList<Sound>().toMutableList()

        routine.add(Sound(R.raw.mild_intro, 30))
        routine.add(Sound(R.raw.mild_dream_1, 60))
        routine.add(Sound(R.raw.mild_sign, 25))
        routine.add(Sound(R.raw.mild_replay, 50))

        if(playCount > 2) {
            routine.add(Sound(R.raw.mild_dream_2, 60))
            routine.add(Sound(R.raw.mild_sign, 25))
            routine.add(Sound(R.raw.mild_replay, 50))
        }

        if(playCount > 3) {
            routine.add(Sound(R.raw.mild_dream_3, 60))
            routine.add(Sound(R.raw.mild_sign, 25))
            routine.add(Sound(R.raw.mild_replay, 50))
        }

        routine.add(Sound(R.raw.mild_finish, 25))

        return routine
    }
}
package sound

import com.lucidtrainer.R

class SSILDSoundRoutine(override var playTier: Int, override var bgRawId: Int,override var bgVolume: Float,
    override var altBgVolume: Float, override var fgVolume: Float, override val eventLabel : String,
    override var bgLabel : String, override val theme: String = "", override val fgLabel : String = "SSILD"
) : SoundRoutine {

    private val ssildDir = "$ROOT_DIR/$SSILD_DIR"
    private val promptDir = PromptSoundRoutine.promptDir

    override fun getStartSounds(): List<String> {
        return emptyList()
    }

    override fun getAltBGSounds(): List<String> {
        return emptyList()
    }

    override fun getRoutine(): List<Sound> {
        val routine : MutableList<Sound> = emptyList<Sound>().toMutableList()

        //intro
        routine.add(Sound(0, 10, "$ssildDir/ssild_intro.ogg", 0F))
        routine.add(Sound(0, 10, "$ssildDir/ssild_sight.ogg", 0F))
        routine.add(Sound(0, 10, "$ssildDir/ssild_hear.ogg", 0F))
        routine.add(Sound(0, 10, "$ssildDir/ssild_feel.ogg", 0F))

        //quick rounds
        routine.add(Sound(0, 10, "$ssildDir/ssild_fast_cycle.ogg", 0F))
        repeat(5) {
            routine.add(Sound(0, 3, "$ssildDir/ssild_sight_short.ogg", 0F))
            routine.add(Sound(0, 3, "$ssildDir/ssild_hear_short.ogg", 0F))
            routine.add(Sound(0, 3, "$ssildDir/ssild_feel_short.ogg", 0F))
        }

        routine.add(Sound(0, 10, "$promptDir/silence.ogg", 0F))

        //slow rounds
        routine.add(Sound(0, 10, "$ssildDir/ssild_slow_cycle.ogg", 0F))
        repeat(4) {
            routine.add(Sound(0, 20, "$ssildDir/ssild_sight_short.ogg", 0F))
            routine.add(Sound(0, 20, "$ssildDir/ssild_hear_short.ogg", 0F))
            routine.add(Sound(0, 20, "$ssildDir/ssild_feel_short.ogg", 0F))
        }

        return routine
    }
}
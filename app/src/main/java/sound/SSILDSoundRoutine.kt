package sound

import android.util.Log
import com.lucidtrainer.R
import utils.FileManager

class SSILDSoundRoutine(override var playTier: Int, override var bgRawId: Int,override var bgVolume: Float,
    override var altBgVolume: Float, override var fgVolume: Float, override val eventLabel : String,
    override var bgLabel : String, override val theme: String = "", override val fgLabel : String = "SSILD"
) : SoundRoutine {

    private val fileManager = FileManager.getInstance()!!

    private val ssildDir = "$ROOT_DIR/$SSILD_DIR"
    private val promptDir = PromptSoundRoutine.promptDir
    private val clipCnt = fileManager.getPromptClipCount()
    private val ssildClipFile = "ssild_clip_$clipCnt.ogg"

    override fun getStartSounds(): List<String> {
        return emptyList()
    }

    override fun getAltBGSounds(): List<String> {
        return emptyList()
    }

    override fun getRoutine(): List<Sound> {
        val routine : MutableList<Sound> = emptyList<Sound>().toMutableList()

        Log.d("MainActivity", "ssild playTier = $playTier")

        //intro
        routine.add(Sound(0, 10, "$ssildDir/ssild_intro.ogg", 0F))
        routine.add(Sound(0, 10, "$ssildDir/ssild_sight.ogg", 0F))
        routine.add(Sound(0, 10, "$ssildDir/ssild_hear.ogg", 0F))
        routine.add(Sound(0, 10, "$ssildDir/ssild_feel.ogg", 0F))

        //quick rounds
        routine.add(Sound(0, 5, "$ssildDir/ssild_fast_cycle.ogg", 0F))

        val tierLimit = if (playTier == 1) 2 else 3
        repeat(tierLimit) {
            routine.add(Sound(0, 3, "$ssildDir/ssild_sight_short.ogg", 0F))
            routine.add(Sound(0, 3, "$ssildDir/ssild_hear_short.ogg", 0F))
            routine.add(Sound(0, 3, "$ssildDir/ssild_feel_short.ogg", 0F))
        }

        routine.add(Sound(0, 5, "$promptDir/silence.ogg", 0F))

        routine.add(Sound(0, 5, "$ssildDir/ssild_stop_fast.ogg", 0F))

        //slow rounds
        routine.add(Sound(0, 10, "$ssildDir/ssild_slow_cycle.ogg", 0F))
        val slowTime = if (playTier == 1) 15 else 20
        repeat(tierLimit) {
            routine.add(Sound(0, slowTime, "$ssildDir/ssild_sight_short.ogg", 0F))
            routine.add(Sound(0, slowTime, "$ssildDir/ssild_hear_short.ogg", 0F))
            routine.add(Sound(0, slowTime, "$ssildDir/ssild_feel_short.ogg", 0F))
        }

        routine.add(Sound(0, 5, "$promptDir/silence.ogg", 0F))

        routine.add(Sound(0, 5, "$ssildDir/ssild_stop_slow.ogg", 0F))

        //auto suggest phase
        routine.add(Sound(0, 10, "$ssildDir/ssild_auto_suggest.ogg", 0F))
        repeat(2) {
            routine.add(Sound(0, 5, "$ssildDir/ssild_suggest_1.ogg", 0F))
            routine.add(Sound(0, 10, "$ssildDir/ssild_suggest_2.ogg", 0F))
        }

        //relax phase
        if(playTier != 1) {
            addClipSound(routine)
        }

        return routine
    }

    private fun addClipSound(routine: MutableList<Sound>) {
        //add a longer more distinct main sound clip towards the end and adjust volume on it
        var startDir = "$ROOT_DIR/$THEMES_DIR/$theme"

        //start with a radio tuning sound
        routine.add(Sound(0, 0, "$startDir/start/tune.ogg"))
        routine.add(Sound(0, 20, "$ssildDir/$ssildClipFile"))

//        val clipFile = fileManager.getUnusedFilesFromDirectory("$startDir/$CLIP1_DIR", 1).shuffled().last()
//        routine.add(Sound(0, 20, "$startDir/$CLIP1_DIR/$clipFile"))
//        fileManager.addFileUsed("$ssildDir/$CLIP1_DIR", ssildClipFile)
    }

    override fun executeAfterPlay() {
        fileManager.updatePromptClipCount()
    }

    override fun fadeDownFg() : Boolean {
        return false
    }
}
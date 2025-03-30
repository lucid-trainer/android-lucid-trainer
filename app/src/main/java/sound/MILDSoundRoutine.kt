package sound

import android.util.Log
import com.lucidtrainer.R
import utils.FileManager

class MILDSoundRoutine(override var playCount: Int, override var bgRawId: Int, override var endBgRawId: Int,
                       override var bgVolume: Float, override var altBgVolume: Float, override var fgVolume: Float,
                       override val eventLabel : String, override var bgLabel : String, override var endBgLabel : String,
                       override var theme: String, override val fgLabel : String = "MILD"

) : SoundRoutine {

    private val fileManager = FileManager.getInstance()!!

    override fun getRoutine(): List<Sound> {
        val routine : MutableList<Sound> = emptyList<Sound>().toMutableList()

        val mildDir = "$ROOT_DIR/$MILD_DIR"
        routine.add(Sound(0, 70, "$mildDir/instruction.ogg"))
        Log.d("MainActivity", "mildDir=$mildDir, count = ${fileManager.getFilesFromDirectory(mildDir).size} ")

        addStartSound(routine)
        addForegroundSounds(routine)

        return routine
    }

    override fun getStartSounds(): List<String> {
        val startSounds : MutableList<String> = emptyList<String>().toMutableList()

        startSounds.add("$ROOT_DIR/$THEMES_DIR/$theme/$START_DIR/silence.ogg")

        return startSounds
    }

    override fun getAltBGSounds(): List<String> {
        val altBGSounds : MutableList<String> = emptyList<String>().toMutableList()

        val dir = "/$ROOT_DIR/$THEMES_DIR/$theme/$ALT_BACKGROUND_DIR"
        //Log.d("MainActivity", "dir=$dir, count = ${fileManager.getFilesFromDirectory(dir).size} ")
        val files = fileManager.getFilesFromDirectory(dir).shuffled().slice(0..9)
        Log.d("MainActivity", "bg files = $files")

        for (i in 0..9) {
            altBGSounds.add("$dir/${files[i]}")
        }

        return altBGSounds
    }

    override fun getSpeechEventsTrigger(): Int {
        return when(playCount) {
            1 -> 7
            else -> 11
        }
    }

    override fun getSpeechEventsCount(): Int {
        return 3
    }

    override fun getSpeechEventsTimeBetween() : Int {
        return 2
    }

    private fun addForegroundSounds(routine: MutableList<Sound>) {
        var dir = "$ROOT_DIR/$THEMES_DIR/$theme/$FOREGROUND_DIR"

        val limit = when(playCount) {
            1 -> 8
            else -> 12
        }

        //Log.d("MainActivity", "altfg dir=$dir, count = ${fileManager.getFilesFromDirectory(dir).size} ")

        val files = fileManager.getUnusedFilesFromDirectory(dir, limit).shuffled().slice(0 until limit)

        var i = 1;
        for (file in files) {
            routine.add(Sound(0, 20, "$dir/$file",false, getVolAdjust(i)))
            i++
        }

        fileManager.addFilesUsed(dir, files)
    }

    private fun addStartSound(routine: MutableList<Sound>) {
        var dir = "$ROOT_DIR/$THEMES_DIR/$theme/$START_DIR"
        routine.add(Sound(0, 20, "$dir/start.ogg",false))
    }

    override fun getVolAdjust(fileCount: Int): Float {

        return when {
            fileCount <= 1 -> .95F
            fileCount <= 2 -> .9F
            fileCount <= 3 -> .85F
            fileCount <= 4 -> .8F
            fileCount <= 5 -> .75F
            fileCount <= 6 -> .7F
            fileCount <= 7 -> .64F
            fileCount <= 8 -> .58F
            fileCount <= 9 -> .52F
            fileCount <= 10 -> .48F
            else -> .42F
        }
    }

    //we always want to start a prompt by resetting the background
    override fun fadeDownBg() : Boolean {
        return true
    }
}
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
        routine.add(Sound(0, 70, "$mildDir/instruction.ogg", 0F, 1.6F))
        Log.d("MainActivity", "mildDir=$mildDir, count = ${fileManager.getFilesFromDirectory(mildDir).size} ")

        addStartSound(routine)
        addForegroundSounds(routine)

        return routine
    }

    override fun getStartSounds(): List<String> {
        return emptyList()
    }

    override fun getAltBGSounds(): List<String> {
        val altBGSounds : MutableList<String> = emptyList<String>().toMutableList()

        val dir = "/$ROOT_DIR/$THEMES_DIR/$theme/$ALT_BACKGROUND_DIR"
        //Log.d("MainActivity", "dir=$dir, count = ${fileManager.getFilesFromDirectory(dir).size} ")
        val files = fileManager.getFilesFromDirectory(dir).shuffled().slice(0..9)

        for (i in 0..9) {
            altBGSounds.add("$dir/${files[i]}")
        }

        return altBGSounds
    }

    override fun getSpeechEventsTrigger(): Int {
        return when(playCount) {
            1 -> 3
            else -> 6
        }
    }

    override fun getSpeechEventsCount(): Int {
        return 3
    }

    private fun addForegroundSounds(routine: MutableList<Sound>) {
        var dir = "$ROOT_DIR/$THEMES_DIR/$theme/$FOREGROUND_DIR"

        val limit = when(playCount) {
            1 -> 6
            else -> 10
        }

        //Log.d("MainActivity", "altfg dir=$dir, count = ${fileManager.getFilesFromDirectory(dir).size} ")

        val files = fileManager.getUnusedFilesFromDirectory(dir, limit).shuffled().slice(0 until limit)

        var i = 1;
        for (file in files) {
            routine.add(Sound(0, 20, "$dir/$file",0F, getVolAdjust(i)))
            i++
        }

        fileManager.addFilesUsed(dir, files)
    }

    private fun addStartSound(routine: MutableList<Sound>) {
        var dir = "$ROOT_DIR/$THEMES_DIR/$theme/$START_DIR"
        routine.add(Sound(0, 20, "$dir/start.ogg",0F))
    }

    override fun getVolAdjust(fileCount: Int): Float {
        return when {
            fileCount <= 1 -> 1F
            fileCount <= 3 -> .9F
            fileCount <= 5 -> .8F
            fileCount <= 8 -> .7F
            fileCount <= 10 -> .6F
            else -> .5F
        }
    }

}
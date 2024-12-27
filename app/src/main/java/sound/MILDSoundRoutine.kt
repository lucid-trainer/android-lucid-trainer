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
        routine.add(Sound(0, 30, "$mildDir/first_instruction.ogg", 0F, 1.25F))
        routine.add(Sound(0, 30, "$mildDir/second_instruction.ogg", 0F, 1.25F))
        Log.d("MainActivity", "mildDir=$mildDir, count = ${fileManager.getFilesFromDirectory(mildDir).size} ")

        addRoutineForegroundSounds(routine)
        addStartSound(routine)
        addRelaxForegroundSounds(routine)

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

    private fun addRoutineForegroundSounds(routine: MutableList<Sound>) {

        val dir = "$ROOT_DIR/$THEMES_DIR/$theme/$FOREGROUND_DIR"
        val files = fileManager.getFilesFromDirectory(dir).sorted().take(2)

        //Log.d("WildRoutine", "used fg ${FileMonitor.getUnusedFilesFromDirectory(dir, 8).size}")

        for (file in files) {
            routine.add(Sound(0, 20, "$dir/$file", 0F))
        }


        fileManager.addFilesUsed(dir, files)
    }

    private fun addRelaxForegroundSounds(routine: MutableList<Sound>) {
        var dir = "$ROOT_DIR/$THEMES_DIR/$theme/$ALT_FOREGROUND_DIR"

        val limit = when(playCount) {
            1 -> 12
            else -> 15
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
            fileCount <= 1 -> .8F
            fileCount <= 3 -> .7F
            fileCount <= 5 -> .6F
            fileCount <= 8 -> .5F
            fileCount <= 10 -> .4F
            else -> .3F
        }
    }

}
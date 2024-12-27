package sound

const val ROOT_DIR = "lt_sounds"
const val THEMES_DIR = "themes"
const val FOREGROUND_DIR = "fg"
const val ALT_FOREGROUND_DIR = "fg2"
const val ALT_BACKGROUND_DIR = "bg"
const val PROMPT_DIR = "prompt"
const val START_DIR = "start"
const val CLIP_DIR = "clip"
const val MILD_DIR = "mild"

interface SoundRoutine {
    var playCount : Int
    var bgRawId: Int
    var endBgRawId: Int
    var bgVolume: Float
    var altBgVolume: Float
    var fgVolume: Float
    val eventLabel: String
    val bgLabel: String
    val endBgLabel: String
    val fgLabel: String
    val theme: String
    fun getStartSounds(): List<String>
    fun getAltBGSounds() : List<String>
    fun getRoutine() : List<Sound>

    fun overrideBG() : Boolean {
      return bgRawId != endBgRawId
    }

    fun getVolAdjust(fileCount: Int): Float {
        return when {
            fileCount <= 3 -> 1F
            fileCount <= 6 -> .9F
            fileCount <= 8 -> .8F
            fileCount <= 10 -> .7F
            fileCount <= 12 -> .6F
            fileCount <= 14 -> .5F
            fileCount <= 16 -> .4F
            fileCount <= 18 -> .3F
            else -> .2F
        }
    }
}
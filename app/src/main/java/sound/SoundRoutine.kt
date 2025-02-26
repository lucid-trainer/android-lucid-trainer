package sound

const val ROOT_DIR = "lt_sounds"
const val THEMES_DIR = "themes"
const val FOREGROUND_DIR = "fg"
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
    fun getSpeechEventsTrigger() : Int = 0
    fun getSpeechEventsCount() : Int = 0
    fun getSpeechEventsTimeBetween() : Int = 0
    fun getRoutine() : List<Sound>

    fun overrideBG() : Boolean {
      return bgRawId != endBgRawId
    }

    fun getVolAdjust(fileCount: Int): Float {
        return when {
            fileCount <= 3 -> 1F
            fileCount <= 6 -> .95F
            fileCount <= 9 -> .9F
            fileCount <= 12 -> .85F
            fileCount <= 15 -> .8F
            fileCount <= 18 -> .75F
            else -> .7F
        }
    }
}
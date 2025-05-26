package sound

const val ROOT_DIR = "lt_sounds"
const val THEMES_DIR = "themes"
const val FOREGROUND_DIR = "fg"
const val ALT_BACKGROUND_DIR = "bg"
const val PROMPT_DIR = "prompt"
const val START_DIR = "start"
const val CLIP1_DIR = "clip1"
const val CLIP2_DIR = "clip2"
const val MILD_DIR = "mild"

interface SoundRoutine {
    var playTier : Int  //used within each routine to control volume level, sound files retrieved, etc.
    var bgRawId: Int  //the background/white noise sound from resources
    var bgVolume: Float  //the volume to start the routine for background noise
    var altBgVolume: Float //the volume to play alt background noises (sound files layered over the white noise)
    var fgVolume: Float //the general volume to play foreground sound files (can be overridden by individual files)
    val eventLabel: String //the text to display in the view for the routine as it plays
    val bgLabel: String //the text for the current white noise background
    val fgLabel: String //the text for the current routine being played
    val theme: String //the current lt_sounds theme being used for sound files
    fun getStartSounds(): List<String>  //returns a list of sounds to play at the start of the routine
    fun getAltBGSounds() : List<String> //returns a list of alt background sounds to play in a loop on top of white noise
    fun getRoutine() : List<Sound> //returns a list of foreground sound files to play on top of background and alt background layers
    fun executeAfterPlay() {}
    fun fadeDownBg() : Boolean = true //whether the routine supports initial fade down of background before starting
    fun fadeDownFg() : Boolean = true //wheth the routine includes initial fade down of foreground as playing (such as podcast routine with one long playing sound)

}
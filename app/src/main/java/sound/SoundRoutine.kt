package sound

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
    fun getStartSounds(): List<String>
    fun getAltBGSounds() : List<String>
    fun getRoutine() : List<Sound>

    fun overrideBG() : Boolean {
      return bgRawId != endBgRawId
    }
}
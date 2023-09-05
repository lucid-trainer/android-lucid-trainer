package sound

interface SoundRoutine {
    var repetition : Int
    var bgRawId: Int
    var endBgRawId: Int
    val eventLabel: String
    val bgLabel: String
    val endBgLabel: String
    val fgLabel: String
    fun getRoutine() : List<Sound>
}
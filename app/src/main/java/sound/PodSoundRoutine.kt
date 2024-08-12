package sound

import android.util.Log

class PodSoundRoutine(override var playCount: Int, override var bgRawId: Int, override var endBgRawId: Int,
                      override var bgVolume: Float, override var altBgVolume: Float, override var fgVolume: Float,
                      override val eventLabel : String, override var bgLabel : String, override var endBgLabel : String,
                      override val fgLabel : String = "POD",
) : SoundRoutine {

    companion object {
        const val ROOT_DIR = "lt_sounds"
        const val POD_DIR = "podcasts"
    }


    override fun getStartSounds(): List<String> {
        return emptyList()
    }

    override fun getAltBGSounds(): List<String> {
        return emptyList()
    }

    override fun getRoutine(): List<Sound> {
        val routine : MutableList<Sound> = emptyList<Sound>().toMutableList()

        //Log.d("PodRoutine", "adding $ROOT_DIR/$POD_DIR/pod_$playCount.mp3")

        //we'll use playCount here to pick which podcast to play
        routine.add(Sound(0, 5, "$ROOT_DIR/$POD_DIR/pod_$playCount.mp3"))
        return routine
    }

    //we always want to treat podcasts like the bg file has been overridden so we can turn down sound
    override fun overrideBG() : Boolean {
        return true
    }

}
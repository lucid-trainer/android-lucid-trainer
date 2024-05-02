package sound

import android.util.Log

class PodSoundRoutine(override var playCount: Int, override var bgRawId: Int, override var endBgRawId: Int,
                      override var bgVolume: Float, override var altBgVolume: Float, override var fgVolume: Float,
                      override val eventLabel : String, override var bgLabel : String, override var endBgLabel : String,
                      override val fgLabel : String = "POD",
) : SoundRoutine {

    override fun getStartSounds(): List<String> {
        val startSounds : MutableList<String> = emptyList<String>().toMutableList()

        return startSounds
    }

    override fun getAltBGSounds(): List<String> {
        var altBGSounds : MutableList<String> = emptyList<String>().toMutableList()
        return altBGSounds
    }

    override fun getRoutine(): List<Sound> {
        val routine : MutableList<Sound> = emptyList<Sound>().toMutableList()

        //we'll use playCount here to keep from replaying the last pod
        routine.add(Sound(0, 5, "wild/pods/pod_" + playCount + ".mp3"))
        Log.d("PodSoundRoutine ", "adding wild/pods/pod_$playCount.mp3")

        return routine
    }

    override fun dimMinLimit() : Long {
        return 2L
    }

    //we always want to treat podcasts like the bg file has been overridden so we can turn down sound
    override fun overrideBG() : Boolean {
        return true
    }

}
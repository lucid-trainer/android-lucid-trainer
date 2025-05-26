package sound

data class Sound(val rawResId: Int, val delayAfter: Int, val filePathId: String? = null, val volAdjust: Float = 0F, val toggleAltBg: String? = null, )


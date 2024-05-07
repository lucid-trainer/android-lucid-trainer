package sound

data class Sound(val rawResId: Int, val delayAfter: Int, val filePathId: String? = null, val fileVolAdjust: Float = 0F)


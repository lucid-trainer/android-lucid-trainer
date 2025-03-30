package sound

data class Sound(val rawResId: Int, val delayAfter: Int, val filePathId: String? = null, val isBgVolAdjust: Boolean = false, val fileVolAdjust: Float = 0F)


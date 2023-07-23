package network.response

data class Document(
    val _id: String,
    val hr: String,
    val hrArray: List<Int>,
    val hrVar: String,
    val isSleep: String,
    val move: String,
    val positionArray: List<PositionArray>,
    val sessionId: String,
    val timestamp: String
)
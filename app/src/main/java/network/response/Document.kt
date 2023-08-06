package network.response

import androidx.room.ColumnInfo
import database.Reading

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

fun Document.transform(): Reading {
    val reading =  Reading()
    this.apply {
        reading.sessionId = sessionId
        reading.heartRate = hr.toInt()
        reading.heartRateVar = hrVar.toDouble()
        reading.isSleep = isSleep
        reading.movement = move.toDouble()
        reading.timestamp = timestamp
    }
    return reading
}

fun List<Document>.transform(): List<Reading> {
    return this.map {
        it.transform()
    }
}
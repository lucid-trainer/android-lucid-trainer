package network.response

import android.util.Log
import com.google.gson.Gson
import database.Reading
import java.time.LocalDateTime

data class Document(
    val _id: String,
    val hr: String?,
    val hrArray: List<Int>?,
    val hrVar: String?,
    val isSleep: String?,
    val move: String?,
    val positionArray: List<Position>?,
    val sessionId: String?,
    val timestamp: String?
)

fun Document.transform(): Reading {
    val gson = Gson()
    val reading =  Reading()
    this.apply {
        if (sessionId != null) {
            reading.sessionId = sessionId
        }
        reading.heartRate = hr?.toInt() ?: 0
        reading.heartRateVar = hrVar?.toDouble() ?: 0.0
        // reading.hrArray = hrArray.joinToString { it.toString()}
        if (isSleep != null) {
            reading.isSleep = isSleep
        }
        reading.movement = move?.toDouble() ?: 0.0
        if (timestamp != null) {
            reading.timestamp = timestamp

            // parse it to a LocalDateTime (date & time without zone or offset)
            val localDateTime: LocalDateTime? = LocalDateTime.parse(timestamp)
            reading.dateTime = localDateTime

        }
        reading.positionArray = gson.toJson(positionArray)

    }
    return reading
}

fun List<Document>.transform(): List<Reading> {
    return this.map {
        it.transform()
    }
}
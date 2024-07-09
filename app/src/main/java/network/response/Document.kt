package network.response

import com.google.gson.Gson
import database.Reading
import java.time.LocalDateTime

data class Document(
    val _id: String,
    val hr: String?,
    val hrArray: String?,
    val hrVar: String?,
    val isSleep: String?,
    val moveZ: String?,
    val positionArray: List<Position>?,
    val sessionId: String?,
    val timestamp: String?,
    val event: String?
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

        if (hrArray != null) {
            reading.hrArray = hrArray
        }
        if (isSleep != null) {
            reading.isSleep = isSleep
        }

        reading.position = gson.toJson(positionArray?.get(0))

        reading.accelMovement = moveZ?.toDouble() ?: 0.0

        if (timestamp != null) {
            reading.timestamp = timestamp

            // parse it to a LocalDateTime (date & time without zone or offset)
            val localDateTime: LocalDateTime? = LocalDateTime.parse(timestamp)
            reading.dateTime = localDateTime
        }

        if (event != null) {
            reading.event = event
        }

    }
    return reading
}

fun List<Document>.transform(): List<Reading> {
    return this.map {
        it.transform()
    }
}
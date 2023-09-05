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
    val accelmove: String?,
    val accelx: String?,
    val accely: String?,
    val accelz: String?,
    val gyromove: String?,
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

        reading.accelMovement = accelmove?.toDouble() ?: 0.0

        reading.gyroMovement = gyromove?.toDouble() ?: 0.0

        setPosition(accelx, accely, accelz, reading)

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

private fun setPosition(
    accelx: String?,
    accely: String?,
    accelz: String?,
    reading: Reading
) {
    if (!accelx.isNullOrEmpty() && !accely.isNullOrEmpty() && !accelz.isNullOrEmpty()) {
        val accelXArray: List<Double> = accelx.split(",").map { it.trim().toDouble() }
        val accelYArray: List<Double> = accely.split(",").map { it.trim().toDouble() }
        val accelZArray: List<Double> = accelz.split(",").map { it.trim().toDouble() }

        val endIdx = accelXArray.size - 1

        reading.position = "{ \"x\": \"" + accelXArray[endIdx] + "\"" +
                "\"y\": \"" + accelYArray[endIdx] + "\"" +
                "\"z\": \"" + accelZArray[endIdx] + "\"}"
    }
}

fun List<Document>.transform(): List<Reading> {
    return this.map {
        it.transform()
    }
}
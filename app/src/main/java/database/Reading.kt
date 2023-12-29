package database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@Entity(tableName = "reading_table")
@Parcelize
data class Reading (

    @PrimaryKey
    @ColumnInfo(name = "timestamp")
    var timestamp: String = "",

    @ColumnInfo(name = "session_id")
    var sessionId: String = "",

    @ColumnInfo(name = "heart_rate")
    var heartRate: Int = 0,

    @ColumnInfo(name = "hr_array")
    var hrArray: String = "",

    @ColumnInfo(name = "heart_rate_var")
    var heartRateVar: Double = 0.00,

    @ColumnInfo(name = "accel_movement")
    var accelMovement: Double = 0.00,

    @ColumnInfo(name = "position")
    var position: String = "",

    @ColumnInfo(name = "dateTime")
    var dateTime: LocalDateTime? = null,

    @ColumnInfo(name = "is_sleep")
    var isSleep: String = "",

    @ColumnInfo(name = "event")
    var event: String = ""

) : Parcelable {
    val dateTimeFormatted : String
        get() = dateTime?.format(DateTimeFormatter.ofPattern("yyy-MM-dd'T'HH:mm:ss.SSS")) ?: "N/A"

    val eventMap : Map<String, String>
        get() = eventConverter(event)
}

fun eventConverter(event: String) : Map<String, String>  {
    return if(event.isNullOrEmpty()) emptyMap()
    else event.split(";").associate { it.substringBefore(".") to it.substringAfter(".") }
}

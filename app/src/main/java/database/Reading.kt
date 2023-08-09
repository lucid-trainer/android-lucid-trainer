package database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import android.os.Parcelable
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.parcelize.Parcelize
import network.response.Position
import java.lang.reflect.Type
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

    @ColumnInfo(name = "movement")
    var movement: Double = 0.00,

    @ColumnInfo(name = "position_array")
    var positionArray: String = "",

    @ColumnInfo(name = "dateTime")
    var dateTime: LocalDateTime? = null,

    @ColumnInfo(name = "is_sleep")
    var isSleep: String = ""


) : Parcelable {
    val dateTimeFormatted : String
        get() = dateTime?.format(DateTimeFormatter.ofPattern("yyy-MM-dd'T'HH:mm:ss.SSS")) ?: "N/A"

    val hrArrayFormatted : List<Int>
        get() = hrArray.split(",").map{ it.trim().toInt() }

    val positionFormatted : List<Position>
        get() = positionConverter(positionArray)
}

fun positionConverter(positionArray: String) : List<Position>  {
    val gson = Gson()
    val positionListType: Type? = object : TypeToken<List<Position>>() {}.type
    return gson.fromJson(positionArray, positionListType)
}
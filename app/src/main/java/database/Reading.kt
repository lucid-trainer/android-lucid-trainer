package database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "reading_table")
data class Reading (

    @PrimaryKey(autoGenerate = true)
    var readingId: Long = 0L,

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

    @ColumnInfo(name = "timestamp")
    var timestamp: String = "",

    @ColumnInfo(name = "is_sleep")
    var isSleep: String = ""
)
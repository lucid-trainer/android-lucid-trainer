package database

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import java.time.LocalDateTime

@Dao
interface ReadingDao {

    @Upsert
    suspend fun insert(reading : Reading): Long

    @Update
    suspend fun update(reading: Reading)

    @Delete
    suspend fun delete(reading: Reading)

    @Query("DELETE FROM reading_table WHERE dateTime <= :dateTime")
    fun deleteOlder(dateTime : LocalDateTime) : Int

    @Query("DELETE FROM reading_table")
    fun deleteAll() : Int

    @Query("SELECT * FROM reading_table ORDER BY dateTime DESC LIMIT 1")
    fun getLatest() : LiveData<Reading>

}
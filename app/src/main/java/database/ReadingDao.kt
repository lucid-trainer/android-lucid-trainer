package database

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update

@Dao
interface ReadingDao {

    @Insert
    suspend fun insert(reading : Reading): Long

    @Update
    suspend fun update(reading: Reading)

    @Delete
    suspend fun delete(reading: Reading)

    @Query("SELECT * FROM reading_table WHERE readingId = :readingId")
    fun get(readingId : Long) : LiveData<Reading>

    @Query("SELECT * FROM reading_table ORDER BY readingId DESC LIMIT 1")
    fun getLatest() : LiveData<Reading>

}
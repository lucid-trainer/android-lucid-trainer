package database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(entities = [Reading::class], version = 1, exportSchema = false)
@TypeConverters(TimestampConverter::class)
abstract class ReadingDatabase : RoomDatabase() {
    abstract val readingDao: ReadingDao

    companion object {
        @Volatile
        private var INSTANCE: ReadingDatabase? = null

        fun getInstance(context: Context): ReadingDatabase {
            synchronized(this) {
                var instance = INSTANCE
                if (instance == null) {
                    instance = Room.databaseBuilder(
                        context.applicationContext,
                        ReadingDatabase::class.java,
                        "reading_database"
                    ).build()
                    INSTANCE = instance
                }
                return instance
            }
        }
    }
}
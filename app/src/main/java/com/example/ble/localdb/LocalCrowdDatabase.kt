package com.example.ble.localdb

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.ble.CrowdReading
import java.util.concurrent.Executors

@Entity(tableName = "readings")
data class ReadingEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val lat: Double,
    val lng: Double,
    val geohash: String,
    val score: Float,
    val level: String
) {
    fun toReading() = CrowdReading(timestamp = timestamp, lat = lat, lng = lng, geohash = geohash, score = score, level = level)
}

@Dao
interface ReadingDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(reading: ReadingEntity)

    @Query("SELECT * FROM readings WHERE geohash = :geohash ORDER BY timestamp DESC LIMIT :limit")
    fun forGeohash(geohash: String, limit: Int): List<ReadingEntity>

    @Query("SELECT * FROM readings ORDER BY timestamp DESC LIMIT :limit")
    fun recent(limit: Int): List<ReadingEntity>

    @Query("DELETE FROM readings WHERE timestamp < :cutoff")
    fun deleteOlderThan(cutoff: Long)

    @Query("DELETE FROM readings WHERE id NOT IN (SELECT id FROM readings ORDER BY timestamp DESC LIMIT :limit)")
    fun keepNewest(limit: Int)
}

@Database(entities = [ReadingEntity::class], version = 1, exportSchema = false)
abstract class LocalCrowdDatabase : RoomDatabase() {
    abstract fun readings(): ReadingDao

    companion object {
        private val executor = Executors.newSingleThreadExecutor()
        @Volatile private var instance: LocalCrowdDatabase? = null

        fun get(context: Context): LocalCrowdDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, LocalCrowdDatabase::class.java, "crowd-local.db")
                .setQueryExecutor(executor)
                .setTransactionExecutor(executor)
                .build().also { instance = it }
        }
    }
}

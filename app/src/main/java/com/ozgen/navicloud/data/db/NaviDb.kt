package com.ozgen.navicloud.data.db

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "servers")
data class ServerEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val baseUrl: String,
    val username: String,
    val password: String,
)

@Dao
interface ServerDao {
    @Query("SELECT * FROM servers ORDER BY id")
    fun observeAll(): Flow<List<ServerEntity>>

    @Query("SELECT * FROM servers WHERE id = :id")
    suspend fun byId(id: Long): ServerEntity?

    @Insert
    suspend fun insert(server: ServerEntity): Long

    @Delete
    suspend fun delete(server: ServerEntity)
}

@Entity(tableName = "downloads")
data class DownloadEntity(
    @PrimaryKey val songId: String,
    val serverId: Long,
    val title: String,
    val artist: String?,
    val album: String?,
    val albumId: String?,
    val coverArt: String?,
    val duration: Int,
    val track: Int?,
    val filePath: String,
    val sizeBytes: Long,
    val downloadedAt: Long,
)

@Dao
interface DownloadDao {
    @Query("SELECT * FROM downloads WHERE serverId = :serverId ORDER BY downloadedAt DESC")
    fun observeAll(serverId: Long): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM downloads WHERE songId = :songId")
    suspend fun byId(songId: String): DownloadEntity?

    @Query("SELECT songId FROM downloads")
    fun observeIds(): Flow<List<String>>

    @Query("SELECT COALESCE(SUM(sizeBytes), 0) FROM downloads")
    fun observeTotalSize(): Flow<Long>

    @Query("SELECT COUNT(*) FROM downloads")
    fun observeCount(): Flow<Int>

    @Query("SELECT * FROM downloads")
    suspend fun all(): List<DownloadEntity>

    @Query("DELETE FROM downloads")
    suspend fun deleteAll()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(download: DownloadEntity)

    @Query("DELETE FROM downloads WHERE songId = :songId")
    suspend fun delete(songId: String)
}

@Database(
    entities = [ServerEntity::class, DownloadEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class NaviDb : RoomDatabase() {
    abstract fun serverDao(): ServerDao
    abstract fun downloadDao(): DownloadDao
}

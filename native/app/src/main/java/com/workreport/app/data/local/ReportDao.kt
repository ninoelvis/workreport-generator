package com.workreport.app.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ReportDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(report: ReportEntity): Long

    @Delete
    suspend fun delete(report: ReportEntity)

    @Query("SELECT * FROM reports ORDER BY created_at DESC")
    fun observeAll(): Flow<List<ReportEntity>>

    /** 按文件名模糊搜索（用户需求：通过文件名搜索） */
    @Query(
        """
        SELECT * FROM reports
        WHERE file_name LIKE '%' || :keyword || '%'
           OR display_name LIKE '%' || :keyword || '%'
        ORDER BY created_at DESC
        """
    )
    fun search(keyword: String): Flow<List<ReportEntity>>

    @Query("SELECT * FROM reports WHERE id = :id")
    suspend fun findById(id: Long): ReportEntity?

    @Query("SELECT * FROM reports WHERE file_name = :fileName LIMIT 1")
    suspend fun findByFileName(fileName: String): ReportEntity?

    @Query("SELECT COUNT(*) FROM reports")
    suspend fun count(): Int
}

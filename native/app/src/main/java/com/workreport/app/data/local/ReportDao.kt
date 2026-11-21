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

    /**
     * 按文件名模糊搜索。
     *
     * ⚠️ `ESCAPE '\'` 必须留着：用户搜 `50%` 时，`%` 不能被当成通配符。
     * 转义在 [com.workreport.app.util.LikeEscape] 里做。
     */
    @Query(
        """
        SELECT * FROM reports
        WHERE file_name LIKE '%' || :keyword || '%' ESCAPE '\'
           OR display_name LIKE '%' || :keyword || '%' ESCAPE '\'
        ORDER BY created_at DESC
        """
    )
    fun searchByName(keyword: String): Flow<List<ReportEntity>>

    /**
     * 按内容搜索：文件名 + 标题 + 时间范围 + 本周完成 + 下周计划。
     *
     * ⚠️ 搜的是 **done_works / plan_works（原始条目）**，
     * **不是** `done_works_exported`（那是格式化并脱敏之后的文本）。
     * 搜原始条目才对得上用户自己写的话 ——
     * 搜脱敏后的文本，用户写「张三」却搜不到，因为里面已经被换成拼音了。
     */
    @Query(
        """
        SELECT * FROM reports
        WHERE file_name LIKE '%' || :keyword || '%' ESCAPE '\'
           OR display_name LIKE '%' || :keyword || '%' ESCAPE '\'
           OR report_title LIKE '%' || :keyword || '%' ESCAPE '\'
           OR time_range LIKE '%' || :keyword || '%' ESCAPE '\'
           OR done_works LIKE '%' || :keyword || '%' ESCAPE '\'
           OR plan_works LIKE '%' || :keyword || '%' ESCAPE '\'
        ORDER BY created_at DESC
        """
    )
    fun searchAll(keyword: String): Flow<List<ReportEntity>>


    @Query("SELECT * FROM reports WHERE id = :id")
    suspend fun findById(id: Long): ReportEntity?

    @Query("SELECT * FROM reports WHERE file_name = :fileName LIMIT 1")
    suspend fun findByFileName(fileName: String): ReportEntity?

    @Query("SELECT COUNT(*) FROM reports")
    suspend fun count(): Int

    /** 全部记录（用来和存放目录里的外部文件去重） */
    @Query("SELECT * FROM reports")
    suspend fun all(): List<ReportEntity>
}

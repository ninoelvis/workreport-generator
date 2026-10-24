package com.workreport.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [ReportEntity::class],
    version = 3,
    exportSchema = true,
)
abstract class ReportDatabase : RoomDatabase() {

    abstract fun reportDao(): ReportDao

    companion object {
        const val NAME = "workreport.db"

        /**
         * v1 → v2：补齐历史报告的完整内容字段，用于「点开历史报告直接预览 Excel」。
         *
         * 旧记录这几列会是空字符串，界面上会提示「该报告由旧版本创建」并降级展示。
         * 列名、类型、默认值必须和 [ReportEntity] 完全一致，否则 Room 的 schema 校验会失败。
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE reports ADD COLUMN report_title TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE reports ADD COLUMN time_range TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE reports ADD COLUMN done_works TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE reports ADD COLUMN plan_works TEXT NOT NULL DEFAULT ''")
            }
        }

        /**
         * v2 → v3：补齐「真正写进 Excel 的那两格」。
         *
         * 预览区要显示的必须是 Excel 里的原文（已格式化 + 已脱敏），
         * 而 v2 存的只是用户写的原始条目 —— 两者对不上，
         * 所以历史报告的预览看起来「内容缺失严重」。
         *
         * 旧记录这两列为空，读取时自动降级为「解压缩包读 Excel」，结果一样是 Excel 原文。
         */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE reports ADD COLUMN done_works_exported TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE reports ADD COLUMN plan_works_exported TEXT NOT NULL DEFAULT ''")
            }
        }
    }
}

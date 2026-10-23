package com.workreport.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [ReportEntity::class],
    version = 2,
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
    }
}

package com.example.sortmate.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.sortmate.data.local.dao.BillDao
import com.example.sortmate.data.local.dao.ChoreDao
import com.example.sortmate.data.local.dao.HouseholdDao
import com.example.sortmate.data.local.dao.MemberDao
import com.example.sortmate.data.local.entity.BillEntity
import com.example.sortmate.data.local.entity.ChoreEntity
import com.example.sortmate.data.local.entity.HouseholdEntity
import com.example.sortmate.data.local.entity.MemberEntity

@Database(
    entities = [
        HouseholdEntity::class,
        MemberEntity::class,
        BillEntity::class,
        ChoreEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class SortMateDatabase : RoomDatabase() {

    abstract fun householdDao(): HouseholdDao
    abstract fun memberDao(): MemberDao
    abstract fun billDao(): BillDao
    abstract fun choreDao(): ChoreDao

    companion object {
        private const val DATABASE_NAME = "sortmate.db"

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE households ADD COLUMN campusLatitude REAL",
                )
                database.execSQL(
                    "ALTER TABLE households ADD COLUMN campusLongitude REAL",
                )
                database.execSQL(
                    "ALTER TABLE households ADD COLUMN campusRadiusMeters REAL NOT NULL DEFAULT 100",
                )
            }
        }

        fun build(context: Context): SortMateDatabase =
            Room.databaseBuilder(
                context.applicationContext,
                SortMateDatabase::class.java,
                DATABASE_NAME,
            )
                .addMigrations(MIGRATION_1_2)
                .build()
    }
}

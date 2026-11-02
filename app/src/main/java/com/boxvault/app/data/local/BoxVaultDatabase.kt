package com.boxvault.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.boxvault.app.data.local.dao.BoxDao
import com.boxvault.app.data.local.dao.ItemDao
import com.boxvault.app.data.local.dao.PhotoDao
import com.boxvault.app.data.local.dao.RoomDao
import com.boxvault.app.data.local.entity.BoxEntity
import com.boxvault.app.data.local.entity.ItemEntity
import com.boxvault.app.data.local.entity.PhotoEntity
import com.boxvault.app.data.local.entity.RoomEntity

@Database(
    entities = [
        RoomEntity::class,
        BoxEntity::class,
        ItemEntity::class,
        PhotoEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class BoxVaultDatabase : RoomDatabase() {

    abstract fun roomDao(): RoomDao
    abstract fun boxDao(): BoxDao
    abstract fun itemDao(): ItemDao
    abstract fun photoDao(): PhotoDao

    companion object {
        const val DATABASE_NAME = "boxvault_local.db"

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `rooms` (
                        `id` TEXT NOT NULL,
                        `name` TEXT NOT NULL,
                        `colorHex` TEXT NOT NULL,
                        `iconName` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_rooms_name` ON `rooms` (`name`)")
                db.execSQL("ALTER TABLE `boxes` ADD COLUMN `roomId` TEXT DEFAULT NULL REFERENCES `rooms`(`id`) ON DELETE SET NULL")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_boxes_roomId` ON `boxes` (`roomId`)")
            }
        }

        @Volatile
        private var INSTANCE: BoxVaultDatabase? = null

        fun getInstance(context: Context): BoxVaultDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    BoxVaultDatabase::class.java,
                    DATABASE_NAME
                )
                    .addMigrations(MIGRATION_1_2)
                    .fallbackToDestructiveMigrationOnDowngrade()
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}

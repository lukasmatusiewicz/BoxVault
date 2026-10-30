package com.boxvault.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.boxvault.app.data.local.dao.BoxDao
import com.boxvault.app.data.local.dao.ItemDao
import com.boxvault.app.data.local.dao.PhotoDao
import com.boxvault.app.data.local.entity.BoxEntity
import com.boxvault.app.data.local.entity.ItemEntity
import com.boxvault.app.data.local.entity.PhotoEntity

@Database(
    entities = [
        BoxEntity::class,
        ItemEntity::class,
        PhotoEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class BoxVaultDatabase : RoomDatabase() {

    abstract fun boxDao(): BoxDao
    abstract fun itemDao(): ItemDao
    abstract fun photoDao(): PhotoDao

    companion object {
        const val DATABASE_NAME = "boxvault_local.db"

        @Volatile
        private var INSTANCE: BoxVaultDatabase? = null

        fun getInstance(context: Context): BoxVaultDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    BoxVaultDatabase::class.java,
                    DATABASE_NAME
                )
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}


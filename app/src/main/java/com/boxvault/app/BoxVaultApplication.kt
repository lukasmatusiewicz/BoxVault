package com.boxvault.app

import android.app.Application
import com.boxvault.app.data.backup.BackupManager
import com.boxvault.app.data.local.BoxVaultDatabase
import com.boxvault.app.data.preferences.UserPreferencesManager
import com.boxvault.app.data.repository.BoxVaultRepository
import com.boxvault.app.data.storage.PhotoStorageManager
import com.boxvault.app.util.sensory.SensoryFeedbackManager

class BoxVaultApplication : Application() {

    val userPreferencesManager: UserPreferencesManager by lazy {
        UserPreferencesManager(this)
    }

    val sensoryFeedbackManager: SensoryFeedbackManager by lazy {
        SensoryFeedbackManager(this, userPreferencesManager)
    }

    val database: BoxVaultDatabase by lazy {
        BoxVaultDatabase.getInstance(this)
    }

    val photoStorageManager: PhotoStorageManager by lazy {
        PhotoStorageManager(this)
    }

    val repository: BoxVaultRepository by lazy {
        BoxVaultRepository(
            boxDao = database.boxDao(),
            itemDao = database.itemDao(),
            photoDao = database.photoDao(),
            photoStorageManager = photoStorageManager,
            boxVaultDatabase = database,
            roomDao = database.roomDao()
        )
    }

    val backupManager: BackupManager by lazy {
        BackupManager(
            context = this,
            repository = repository,
            photoStorageManager = photoStorageManager
        )
    }
}

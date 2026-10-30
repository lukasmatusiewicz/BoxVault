package com.boxvault.app

import android.app.Application
import com.boxvault.app.data.local.BoxVaultDatabase
import com.boxvault.app.data.repository.BoxVaultRepository
import com.boxvault.app.data.storage.PhotoStorageManager

class BoxVaultApplication : Application() {

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
            photoStorageManager = photoStorageManager
        )
    }
}


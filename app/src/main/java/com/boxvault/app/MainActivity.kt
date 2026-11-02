package com.boxvault.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.boxvault.app.ui.navigation.BoxVaultNavHost
import com.boxvault.app.ui.theme.BoxVaultTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val app = application as BoxVaultApplication

        setContent {
            val themeMode by app.userPreferencesManager.themeMode.collectAsStateWithLifecycle()
            BoxVaultTheme(themeMode = themeMode) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    BoxVaultNavHost(
                        repository = app.repository,
                        photoStorageManager = app.photoStorageManager,
                        backupManager = app.backupManager,
                        userPreferencesManager = app.userPreferencesManager,
                        sensoryFeedbackManager = app.sensoryFeedbackManager
                    )
                }
            }
        }
    }
}

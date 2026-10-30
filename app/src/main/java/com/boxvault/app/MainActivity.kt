package com.boxvault.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.boxvault.app.ui.navigation.BoxVaultNavHost
import com.boxvault.app.ui.theme.BoxVaultTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val app = application as BoxVaultApplication

        setContent {
            BoxVaultTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    BoxVaultNavHost(
                        repository = app.repository,
                        photoStorageManager = app.photoStorageManager
                    )
                }
            }
        }
    }
}

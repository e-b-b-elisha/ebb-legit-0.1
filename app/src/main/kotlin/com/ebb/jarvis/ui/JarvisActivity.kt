package com.ebb.jarvis.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ebb.jarvis.ui.theme.JarvisTheme

/**
 * The home screen itself. Registered for CATEGORY_HOME, so pressing home lands here;
 * pressing home again while already here drops any open overlay back to the HUD.
 */
class JarvisActivity : ComponentActivity() {

    private val viewModel: JarvisViewModel by viewModels()

    private val micPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) viewModel.startListening() else viewModel.onMicrophoneDenied()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            JarvisTheme {
                val state by viewModel.state.collectAsStateWithLifecycle()
                JarvisRoot(
                    state = state,
                    viewModel = viewModel,
                    onMicTapped = ::onMicTapped,
                )
            }
        }
    }

    /** Home pressed while JARVIS is already foreground: reset to the HUD. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        viewModel.showHud()
    }

    override fun onResume() {
        super.onResume()
        // Apps get installed and removed while the launcher sits in the background.
        viewModel.refreshApps()
    }

    private fun onMicTapped() {
        val granted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO,
        ) == PackageManager.PERMISSION_GRANTED

        if (granted) {
            viewModel.toggleListening()
        } else {
            micPermission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }
}

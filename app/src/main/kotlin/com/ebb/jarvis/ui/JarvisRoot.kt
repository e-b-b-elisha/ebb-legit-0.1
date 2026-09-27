package com.ebb.jarvis.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import com.ebb.jarvis.ui.drawer.AppDrawerSheet
import com.ebb.jarvis.ui.hud.BootSequence
import com.ebb.jarvis.ui.hud.HudScreen
import com.ebb.jarvis.ui.hud.ScanlineOverlay
import com.ebb.jarvis.ui.settings.SettingsSheet
import com.ebb.jarvis.ui.theme.Jarvis

/**
 * Assembles the launcher: HUD underneath, drawer and settings as overlays, scan
 * lines over the top. Back never leaves — it only steps an overlay down — because
 * this is the home screen and there is nothing behind it.
 */
@Composable
fun JarvisRoot(
    state: JarvisUiState,
    viewModel: JarvisViewModel,
    onMicTapped: () -> Unit,
) {
    BackHandler(enabled = true) {
        if (state.surface != Surface.HUD) viewModel.showHud()
        // On the HUD, back is deliberately inert.
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Jarvis.Void),
    ) {
        val hudModifier = Modifier
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .imePadding()
            .let { base ->
                if (state.surface == Surface.HUD && state.phase != Phase.BOOT) {
                    base.pointerInput(Unit) {
                        var travelled = 0f
                        detectVerticalDragGestures(
                            onDragStart = { travelled = 0f },
                            onDragEnd = {
                                if (travelled < -SWIPE_THRESHOLD_PX) viewModel.showDrawer()
                            },
                        ) { _, delta -> travelled += delta }
                    }
                } else {
                    base
                }
            }

        if (state.phase == Phase.BOOT) {
            BootSequence(
                operatorName = state.settings.operatorName,
                onFinished = viewModel::onBootFinished,
            )
        } else {
            HudScreen(
                state = state,
                onMic = onMicTapped,
                onSubmit = viewModel::submit,
                onOpenDrawer = viewModel::showDrawer,
                onOpenSettings = viewModel::showSettings,
                onClear = viewModel::clearConversation,
                modifier = hudModifier,
            )
        }

        AnimatedVisibility(
            visible = state.surface == Surface.DRAWER,
            enter = slideInVertically(initialOffsetY = { it / 3 }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it / 3 }) + fadeOut(),
        ) {
            AppDrawerSheet(
                apps = state.apps,
                onLaunch = viewModel::launch,
                onRefresh = viewModel::refreshApps,
                onClose = viewModel::showHud,
                modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing),
            )
        }

        AnimatedVisibility(
            visible = state.surface == Surface.SETTINGS,
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            SettingsSheet(
                settings = state.settings,
                onApiKey = viewModel::setApiKey,
                onModel = viewModel::setModel,
                onEffort = viewModel::setEffort,
                onOperatorName = viewModel::setOperatorName,
                onSpeakReplies = viewModel::setSpeakReplies,
                onBootSequence = viewModel::setBootSequence,
                onWebSearch = viewModel::setWebSearch,
                onClose = viewModel::showHud,
                modifier = Modifier
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .imePadding(),
            )
        }

        // Atmosphere only: no pointer input, so every touch falls through to the UI.
        ScanlineOverlay()
    }
}

private const val SWIPE_THRESHOLD_PX = 120f

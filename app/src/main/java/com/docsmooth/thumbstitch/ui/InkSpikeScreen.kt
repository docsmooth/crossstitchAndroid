package com.docsmooth.thumbstitch.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.docsmooth.thumbstitch.rendering.InkStrokeView

/** Blank screen that shows the in-progress finger stroke. No game logic. */
@Composable
fun InkSpikeScreen(modifier: Modifier = Modifier) {
    AndroidView(
        factory = { context -> InkStrokeView(context) },
        modifier = modifier.fillMaxSize(),
    )
}

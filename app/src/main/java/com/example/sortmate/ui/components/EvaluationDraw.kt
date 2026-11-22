package com.example.sortmate.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import com.example.sortmate.BuildConfig
import com.example.sortmate.util.evaluationMark

/** Log the first frame that actually draws this version, not the preceding network callback. */
@Composable
internal fun Modifier.markFirstDraw(event: String): Modifier {
    if (!BuildConfig.DEBUG) return this
    return then(remember(event) {
        var drawn = false
        Modifier.drawWithContent {
            drawContent()
            if (!drawn) {
                drawn = true
                evaluationMark(event)
            }
        }
    })
}

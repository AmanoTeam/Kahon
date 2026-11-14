package tachiyomi.presentation.core.components.material

import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CardElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.dp

/**
 * Whether costly motion and effects are disabled app-wide: expressive theme motion, overscroll
 * effects, ripples, backdrop blur, card shadows and the animated navigation bar.
 *
 * This is provided by the theme with the value read once at startup; toggling the setting applies
 * after an activity recreation.
 */
val LocalReducedMotion = staticCompositionLocalOf { false }

/**
 * The default [CardElevation] for [androidx.compose.material3.ElevatedCard]s, or one that draws no
 * shadow while [LocalReducedMotion] is enabled.
 */
@Composable
fun reducedMotionCardElevation(): CardElevation =
    if (LocalReducedMotion.current) {
        CardDefaults.elevatedCardElevation(0.dp, 0.dp, 0.dp, 0.dp, 0.dp, 0.dp)
    } else {
        CardDefaults.elevatedCardElevation()
    }

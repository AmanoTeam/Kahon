package tachiyomi.presentation.core.components.material

import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.TopAppBarState
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Whether the top app bar and the bottom navigation bar should hide on scroll. When false, the
 * previous behavior is restored: pinned top app bars and an always visible bottom bar.
 *
 * This is provided with the value read once at startup; toggling the setting requires restarting
 * the app.
 */
val LocalHideBarsOnScroll = staticCompositionLocalOf { true }

/**
 * Creates [TopAppBarDefaults.enterAlwaysScrollBehavior] while [hideBarsOnScroll] is enabled, or
 * [TopAppBarDefaults.pinnedScrollBehavior] restoring the previous behavior otherwise.
 *
 * The height offset limit is capped to a 1px sentinel instead of its default (-Float.MAX_VALUE)
 * because the enterAlways connection consumes the whole scroll delta while the offset is changing;
 * without a TopAppBar attached to the behavior to set the real limit (e.g. Scaffolds with a
 * non-app bar topBar like the banners in MainActivity), it would otherwise swallow all scrolling.
 * A real TopAppBar overwrites this limit on first layout, and the sentinel costs at most 1px of
 * scroll on unconsumed behaviors.
 */
@Composable
fun rememberTopAppBarScrollBehavior(
    hideBarsOnScroll: Boolean,
    state: TopAppBarState = rememberTopAppBarState(initialHeightOffsetLimit = -1f),
): TopAppBarScrollBehavior {
    return if (hideBarsOnScroll) {
        TopAppBarDefaults.enterAlwaysScrollBehavior(state)
    } else {
        TopAppBarDefaults.pinnedScrollBehavior(state)
    }
}

package eu.kanade.tachiyomi.ui.home

import android.view.accessibility.AccessibilityManager
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffoldState
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffoldValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * A [NestedScrollConnection] that hides the navigation suite when the user scrolls down and shows
 * it again when they scroll up, porting the behavior of Material's HideViewOnScrollBehavior used
 * by JustDeleteMe on its bottom navigation view.
 *
 * Like the Material behavior, it only reacts to the scroll delta consumed by the scrolling
 * content, and it refuses to hide the view while touch exploration (TalkBack) is enabled.
 */
class HideOnScrollConnection(
    private val scope: CoroutineScope,
    private val state: NavigationSuiteScaffoldState,
    private val isTouchExplorationEnabled: () -> Boolean = { false },
) : NestedScrollConnection {

    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
        when {
            consumed.y < 0 -> hide()
            consumed.y > 0 -> show()
        }
        return Offset.Zero
    }

    /**
     * The visibility target is derived from [NavigationSuiteScaffoldState.targetValue] instead of
     * a locally tracked flag, since the state can also be shown externally (e.g. when switching
     * tabs or leaving selection mode) and a stale local flag would stop the bar from hiding
     * again until the scroll direction is flipped.
     */
    private fun hide() {
        if (state.targetValue != NavigationSuiteScaffoldValue.Hidden && !isTouchExplorationEnabled()) {
            scope.launch { state.hide() }
        }
    }

    private fun show() {
        if (state.targetValue != NavigationSuiteScaffoldValue.Visible) {
            scope.launch { state.show() }
        }
    }
}

/**
 * Remembers a [HideOnScrollConnection] for [state], tracking the touch exploration state while
 * composed.
 */
@Composable
fun rememberHideOnScrollConnection(state: NavigationSuiteScaffoldState): HideOnScrollConnection {
    val scope = rememberCoroutineScope()

    val context = LocalContext.current
    val accessibilityManager = remember(context) {
        context.getSystemService(AccessibilityManager::class.java)
    }
    var touchExplorationEnabled by remember { mutableStateOf(accessibilityManager?.isTouchExplorationEnabled == true) }
    if (accessibilityManager != null) {
        DisposableEffect(accessibilityManager) {
            val listener = AccessibilityManager.TouchExplorationStateChangeListener {
                touchExplorationEnabled = accessibilityManager.isTouchExplorationEnabled
            }
            accessibilityManager.addTouchExplorationStateChangeListener(listener)
            onDispose { accessibilityManager.removeTouchExplorationStateChangeListener(listener) }
        }
    }

    return remember(scope, state, touchExplorationEnabled) {
        HideOnScrollConnection(scope, state) { touchExplorationEnabled }
    }
}

package tachiyomi.presentation.core.components.material

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material3.FabPosition
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp

@Composable
fun Scaffold(
    modifier: Modifier = Modifier,
    // The height offset limit is capped to a 1px sentinel instead of its default (-Float.MAX_VALUE)
    // because the enterAlways connection consumes the whole scroll delta while the offset is
    // changing; without a TopAppBar attached to the behavior to set the real limit (e.g. Scaffolds
    // with a non-app bar topBar like the banners in MainActivity), it would otherwise swallow all
    // scrolling. A real TopAppBar overwrites this limit on first layout, and the sentinel costs at
    // most 1px of scroll on unconsumed behaviors.
    topBarScrollBehavior: TopAppBarScrollBehavior? = null,
    topBar: @Composable (TopAppBarScrollBehavior) -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    snackbarHost: @Composable () -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
    floatingActionButtonPosition: FabPosition = FabPosition.End,
    containerColor: Color = MaterialTheme.colorScheme.background,
    contentColor: Color = contentColorFor(containerColor),
    contentWindowInsets: WindowInsets = ScaffoldDefaults.contentWindowInsets,
    content: @Composable (PaddingValues) -> Unit,
) {
    var fabHeight by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current

    // The resolved scroll behavior honors LocalHideBarsOnScroll, which is what the pinned fallback
    // from rememberTopAppBarScrollBehavior() is all about.
    val hideBarsOnScroll = LocalHideBarsOnScroll.current
    val resolvedTopBarScrollBehavior =
        topBarScrollBehavior ?: rememberTopAppBarScrollBehavior(hideBarsOnScroll)

    val topBarConnection = remember(resolvedTopBarScrollBehavior) {
        val connection = resolvedTopBarScrollBehavior.nestedScrollConnection
        val topBarState = resolvedTopBarScrollBehavior.state
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset =
                connection.onPreScroll(available, source)

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                val consumedByAppBar = connection.onPostScroll(consumed, available, source)
                // Pull-to-refresh consumes the overscroll while the gesture is held at the start
                // of the list, and the app bar scroll behaviors account for all consumed scroll as
                // content scroll, keeping the bar tinted despite the content being at its start.
                // The content offset is never legitimately positive, so clamp it.
                topBarState.contentOffset = topBarState.contentOffset.coerceAtMost(0f)
                return consumedByAppBar
            }

            override suspend fun onPreFling(available: Velocity): Velocity =
                connection.onPreFling(available)

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity =
                connection.onPostFling(consumed, available)
        }
    }

    androidx.compose.material3.Scaffold(
        modifier = modifier.nestedScroll(topBarConnection),
        topBar = { topBar(resolvedTopBarScrollBehavior) },
        bottomBar = bottomBar,
        snackbarHost = snackbarHost,
        floatingActionButton = {
            Box(Modifier.onSizeChanged { fabHeight = it.height }) {
                floatingActionButton()
            }
        },
        floatingActionButtonPosition = floatingActionButtonPosition,
        containerColor = containerColor,
        contentColor = contentColor,
        contentWindowInsets = contentWindowInsets,
    ) { contentPadding ->
        val paddingValues = remember(contentPadding) {
            FabAwarePaddingValues(contentPadding) {
                if (fabHeight == 0) 0.dp else with(density) { fabHeight.toDp() } + FabSpacing
            }
        }
        content(paddingValues)
    }
}

private class FabAwarePaddingValues(
    private val base: PaddingValues,
    private val extraBottom: () -> Dp,
) : PaddingValues {
    override fun calculateLeftPadding(layoutDirection: LayoutDirection): Dp =
        base.calculateLeftPadding(layoutDirection)

    override fun calculateTopPadding(): Dp = base.calculateTopPadding()

    override fun calculateRightPadding(layoutDirection: LayoutDirection): Dp =
        base.calculateRightPadding(layoutDirection)

    override fun calculateBottomPadding(): Dp = base.calculateBottomPadding() + extraBottom()
}
private val FabSpacing = 16.dp

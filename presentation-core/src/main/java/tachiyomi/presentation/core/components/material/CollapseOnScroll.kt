package tachiyomi.presentation.core.components.material

import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.layout
import kotlin.math.roundToInt

/**
 * Collapses the modified content away together with the app bar attached to [scrollBehavior].
 *
 * Meant for strips laid out right below a collapsing top app bar (e.g. a tab row) so that they
 * hide together with it. The content is clipped from the top and its reported height shrinks
 * proportionally to the scroll behavior's collapsed fraction: when the app bar is fully collapsed
 * the content is fully hidden.
 */
fun Modifier.collapseOnScroll(scrollBehavior: TopAppBarScrollBehavior?): Modifier {
    val state = scrollBehavior?.state ?: return this
    return clipToBounds()
        .layout { measurable, constraints ->
            val placeable = measurable.measure(constraints)
            val collapsedHeight = (placeable.height * state.collapsedFraction).roundToInt()
            layout(placeable.width, placeable.height - collapsedHeight) {
                placeable.place(0, -collapsedHeight)
            }
        }
}

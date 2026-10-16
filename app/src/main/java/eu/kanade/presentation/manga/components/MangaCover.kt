package eu.kanade.presentation.manga.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.paint
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import coil3.size.Size
import eu.kanade.presentation.util.rememberResourceBitmapPainter
import eu.kanade.tachiyomi.R

enum class MangaCover(val ratio: Float) {
    Square(1f / 1f),
    Book(2f / 3f),
    ;

    @Composable
    operator fun invoke(
        data: Any?,
        modifier: Modifier = Modifier,
        contentDescription: String = "",
        shape: Shape = MaterialTheme.shapes.extraSmall,
        onClick: (() -> Unit)? = null,
    ) {
        PinchZoomableMangaCover(
            data = data,
            modifier = modifier,
            ratio = ratio,
            contentDescription = contentDescription,
            shape = shape,
            onClick = onClick,
        )
    }
}

/**
 * Cover with an organic pinch to zoom.
 *
 * The gesture runs on the cover node itself, but while zoomed the node only
 * draws an empty placeholder slot (like the feed row left behind by the lifted
 * view). The zoomed cover is drawn by
 * [PinchZoomOverlayHost] above the rest of the screen, using a full resolution
 * version of the cover (the regular cover is loaded at the size of its view).
 */
@Composable
private fun PinchZoomableMangaCover(
    data: Any?,
    modifier: Modifier,
    ratio: Float,
    contentDescription: String,
    shape: Shape,
    onClick: (() -> Unit)?,
) {
    val state = rememberPinchZoomState()
    val context = LocalContext.current
    val errorPainter = rememberResourceBitmapPainter(id = R.drawable.cover_error)
    state.overlayContent = remember(data, errorPainter) {
        {
            val model = when (data) {
                // The header passes an already-built request (e.g. crossfade);
                // reuse it instead of wrapping the request itself as the model.
                is ImageRequest -> data.newBuilder(context)
                    .size(Size.ORIGINAL)
                    .crossfade(true)
                    .build()
                else -> ImageRequest.Builder(context)
                    .data(data)
                    .size(Size.ORIGINAL)
                    .crossfade(true)
                    .build()
            }
            AsyncImage(
                model = model,
                error = errorPainter,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        }
    }
    Box(
        modifier = modifier
            .aspectRatio(ratio)
            .pinchZoomable(state)
            .clip(shape)
            .then(
                if (onClick != null) {
                    Modifier.clickable(
                        role = Role.Button,
                        onClick = onClick,
                    )
                } else {
                    Modifier
                },
            ),
    ) {
        if (!state.isZooming) {
            AsyncImage(
                model = data,
                placeholder = ColorPainter(CoverPlaceholderColor),
                error = errorPainter,
                contentDescription = contentDescription,
                modifier = Modifier.matchParentSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            Box(
                Modifier
                    .matchParentSize()
                    .paint(ColorPainter(CoverPlaceholderColor)),
            )
        }
    }
}

private val CoverPlaceholderColor = Color(0x1F888888)

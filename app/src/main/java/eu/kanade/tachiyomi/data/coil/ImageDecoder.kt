package eu.kanade.tachiyomi.data.coil

import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.core.graphics.createBitmap
import ca.mpreg.imagedecoder.ImageDecoder
import coil3.Canvas
import coil3.Image
import coil3.ImageLoader
import coil3.asImage
import coil3.decode.DecodeResult
import coil3.decode.DecodeUtils
import coil3.decode.Decoder
import coil3.decode.ImageSource
import coil3.fetch.SourceFetchResult
import coil3.request.Options
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import okio.BufferedSource
import tachiyomi.core.common.util.system.ImageUtil
import kotlin.math.min
import kotlin.math.sqrt

/**
 * A [Decoder] that uses [ImageDecoder] to decode image formats not supported
 * by the Android system decoder (AVIF, JXL, HEIF, etc.).
 */
class ImageDecoder(private val resources: ImageSource, private val options: Options) : Decoder {

    /**
     * Wraps a raw [ImageDecoder.Frame] as a Coil [Image] for callers that want
     * direct access to the RGBA [java.nio.ByteBuffer] (e.g. the new-decoder path).
     */
    class DecodeResultImage(
        val frame: ImageDecoder.Frame,
        val isHdr: Boolean,
        val hdrHeadroom: Float,
        val gainmap: ImageDecoder.Gainmap?,
    ) : Image {
        val image: java.nio.ByteBuffer get() = frame.image

        // Taken now: a caller may close the frame once it has the pixels.
        override val size: Long = frame.image.capacity().toLong()
        override val width: Int get() = frame.width
        override val height: Int get() = frame.height
        override val shareable: Boolean get() = true
        override fun draw(canvas: Canvas) {}
    }

    /**
     * A page cut into immutable strips of about [STRIP_BYTES] each. The renderer uploads an
     * immutable bitmap only when it is drawn, so only the strips on screen reach the GPU instead of
     * the whole page as one texture.
     */
    class StripsImage(val strips: List<Bitmap>) : Image {
        override val size: Long = strips.sumOf { it.allocationByteCount.toLong() }
        override val width: Int = strips.firstOrNull()?.width ?: 0
        override val height: Int = strips.sumOf { it.height }
        override val shareable: Boolean get() = false
        override fun draw(canvas: Canvas) {}
    }

    override suspend fun decode(): DecodeResult {
        if (options.webtoonStrips) return decodeStrips()

        return resources.source().use {
            ImageDecoder.open(it.inputStream()).use { dec ->
                // Raw frame for custom rendering; sampling is the caller's.
                if (options.newDecoder) {
                    return@use DecodeResult(
                        image = DecodeResultImage(
                            dec.decodeNext(),
                            dec.isHdr,
                            dec.hdrHeadroom,
                            if (dec.hdrKind == ImageDecoder.HdrKind.GAINMAP) dec.getGainmap() else null,
                        ),
                        isSampled = false,
                    )
                }

                // Shrunk while decoding. Sizes round up, so may be a pixel over BitmapFactory's.
                val sampleSize = DecodeUtils.calculateInSampleSize(
                    srcWidth = dec.width,
                    srcHeight = dec.height,
                    dstWidth = options.size.widthPx(options.scale) { dec.width },
                    dstHeight = options.size.heightPx(options.scale) { dec.height },
                    scale = options.scale,
                )
                DecodeResult(
                    image = dec.decodeToBitmap(null, sampleSize).asImage(),
                    isSampled = sampleSize > 1,
                )
            }
        }
    }

    /**
     * Copies bands of rows straight from the native frame into strips, so the page never exists as
     * one Java bitmap on top of the frame. The frame is decoded once, at the largest whole fraction
     * still at least the strip width: per-strip regions would re-decode the rows above each.
     *
     * Strips keep the resolution a power-of-two sample size leaves, like the other paths, so zooming
     * in still shows the page's detail, unless that would make the page take more than
     * [MAX_PAGE_BYTES]. They are never narrower than the view, so the page stays sharp unzoomed.
     */
    private suspend fun decodeStrips(): DecodeResult = stripDecodes.withPermit {
        // The content bands flare found for the page: [left, top, width, height, count,
        // bandTop, bandHeight, ...] in image pixels, or null when the whole page is content
        val bandsArray = options.cropRect

        val (frame, isHdr, sizes) = resources.source().use {
            ImageDecoder.open(it.inputStream()).use { dec ->
                val bytesPerPixel = if (dec.isHdr) 8 else 4
                val sourceWidth = (bandsArray?.getOrNull(0) ?: 0) + (bandsArray?.getOrNull(2) ?: dec.width)
                val sourceHeight = (bandsArray?.getOrNull(1) ?: 0) + (bandsArray?.getOrNull(3) ?: dec.height)
                val viewWidth = options.size.widthPx(options.scale) { sourceWidth }
                val sampleSize = DecodeUtils.calculateInSampleSize(
                    srcWidth = sourceWidth,
                    srcHeight = sourceHeight,
                    dstWidth = viewWidth,
                    dstHeight = options.size.heightPx(options.scale) { sourceHeight },
                    scale = options.scale,
                )
                val budgetWidth = sqrt(MAX_PAGE_BYTES.toDouble() / bytesPerPixel * sourceWidth / sourceHeight).toInt()
                val stripWidth = maxOf(viewWidth, min(sourceWidth / sampleSize, budgetWidth))
                    // Older GPUs can't draw a texture wider than this
                    .coerceAtMost(MAX_STRIP_WIDTH)
                    .coerceIn(1, sourceWidth)
                // ceil(width / decodeSample) >= stripWidth: strips only scale down
                val decodeSample = (sourceWidth / stripWidth).coerceAtLeast(1)
                Triple(dec.decode(null, decodeSample), dec.isHdr, Triple(stripWidth, dec.width, dec.height))
            }
        }
        val (stripWidth, fullWidth, fullHeight) = sizes
        frame.use {
            val config = if (isHdr) Bitmap.Config.RGBA_F16 else Bitmap.Config.ARGB_8888
            val bytesPerPixel = if (isHdr) 8 else 4
            val rowBytes = frame.width * bytesPerPixel

            // The bands in the decoded frame's pixels: the frame is the page scaled down by
            // [decodeSample], so it maps image pixels by frame size over image size
            val ratioX = frame.width.toFloat() / fullWidth
            val ratioY = frame.height.toFloat() / fullHeight
            val left = ((bandsArray?.getOrNull(0) ?: 0) * ratioX).toInt().coerceIn(0, frame.width - 1)
            val width = ((bandsArray?.getOrNull(2) ?: fullWidth) * ratioX).toInt().coerceIn(1, frame.width - left)
            val bandList = if (bandsArray != null && bandsArray.size >= 5 && bandsArray[4] > 0 &&
                bandsArray.size == 5 + 2 * bandsArray[4]
            ) {
                (0 until bandsArray[4]).map {
                    val top = (bandsArray[5 + 2 * it] * ratioY).toInt().coerceIn(0, frame.height - 1)
                    val height = (bandsArray[6 + 2 * it] * ratioY).toInt().coerceIn(1, frame.height - top)
                    top to height
                }
            } else {
                listOf(
                    ((bandsArray?.getOrNull(1) ?: 0) * ratioY).toInt().coerceIn(0, frame.height - 1)
                        .let { top ->
                            top to ((bandsArray?.getOrNull(3) ?: fullHeight) * ratioY).toInt()
                                .coerceIn(1, frame.height - top)
                        },
                )
            }
            val scale = stripWidth.toFloat() / width

            // Strips of about the same size in bytes, so each takes about as long to upload
            val stripRows = (STRIP_BYTES / (stripWidth * bytesPerPixel)).coerceAtLeast(1)
            val bandRows = (stripRows / scale).toInt().coerceAtLeast(1)
            val matrix = Matrix().apply { setScale(scale, scale) }
            val band = createBitmap(frame.width, bandRows, config)
            val cuts = mutableListOf<Bitmap>()
            bandList.forEach { (bandTop, bandHeight) ->
                (bandTop..<bandTop + bandHeight step bandRows).forEach { rowsTop ->
                    val rows = min(bandRows, bandTop + bandHeight - rowsTop)
                    if (rows != band.height) band.reconfigure(frame.width, rows, config)
                    band.copyPixelsFromBuffer(
                        frame.image.duplicate().apply {
                            position(rowsTop * rowBytes)
                            limit((rowsTop + rows) * rowBytes)
                        },
                    )
                    val strip = if (left == 0 && width == frame.width && stripWidth >= frame.width) {
                        band
                    } else {
                        Bitmap.createBitmap(band, left, 0, width, rows, matrix, true)
                    }
                    // createBitmap returns a mutable bitmap despite its docs, and copy is the only way to an immutable one
                    cuts += strip.copy(config, false).also { if (strip !== band) strip.recycle() }
                }
            }
            band.recycle()
            DecodeResult(image = StripsImage(cuts), isSampled = stripWidth < width)
        }
    }

    class Factory : Decoder.Factory {
        override fun create(result: SourceFetchResult, options: Options, imageLoader: ImageLoader): Decoder? {
            return if (options.newDecoder || options.webtoonStrips || isApplicable(result.source.source())) {
                ImageDecoder(result.source, options)
            } else {
                null
            }
        }

        private fun isApplicable(source: BufferedSource): Boolean {
            val type = source.peek().inputStream().use {
                ImageUtil.findImageType(it)
            }
            return when (type) {
                ImageUtil.ImageType.AVIF,
                ImageUtil.ImageType.JXL,
                ImageUtil.ImageType.HEIF,
                ImageUtil.ImageType.JP2,
                -> true

                else -> false
            }
        }

        override fun equals(other: Any?) = other is Factory

        override fun hashCode() = javaClass.hashCode()
    }
}

private const val STRIP_BYTES = 4 * 1024 * 1024

private const val MAX_PAGE_BYTES = 128 * 1024 * 1024

private const val MAX_STRIP_WIDTH = 4096

// Each decode holds the whole native frame until its strips are copied, and the reader loads pages ahead in parallel
private val stripDecodes = Semaphore(1)

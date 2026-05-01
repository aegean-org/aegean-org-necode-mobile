package com.litter.android.ui.pets

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.litter.android.state.CachedPetPackage
import com.litter.android.state.PetAvatarState
import com.litter.android.state.PetOverlayController
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

private const val FrameWidth = 192
private const val FrameHeight = 208
private const val Columns = 8
private const val Rows = 9
private const val AtlasWidth = FrameWidth * Columns
private const val AtlasHeight = FrameHeight * Rows

private data class PetSpriteAtlas(
    val image: ImageBitmap,
    val framesByRow: List<List<Int>>,
) {
    fun framesFor(state: PetAvatarState): List<Int> = framesByRow.getOrElse(state.row) { listOf(0) }
}

@Composable
fun PetOverlayView(
    pet: CachedPetPackage,
    state: PetAvatarState,
    reducedMotion: Boolean,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .offset {
                IntOffset(
                    PetOverlayController.dragOffsetX.roundToInt(),
                    PetOverlayController.dragOffsetY.roundToInt(),
                )
            }
            .size(width = 112.dp, height = 122.dp)
            .pointerInput(pet.id) {
                detectDragGestures(
                    onDragStart = { PetOverlayController.startDrag() },
                    onDragCancel = { PetOverlayController.endDrag() },
                    onDragEnd = { PetOverlayController.endDrag() },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        PetOverlayController.dragBy(dragAmount.x, dragAmount.y)
                    },
                )
            },
    ) {
        PetSpriteView(
            spritesheetBytes = pet.spritesheetBytes,
            state = state,
            reducedMotion = reducedMotion,
        )
    }
}

@Composable
fun PetSpriteView(
    spritesheetBytes: ByteArray,
    state: PetAvatarState,
    reducedMotion: Boolean,
    modifier: Modifier = Modifier,
) {
    val atlas = remember(spritesheetBytes) {
        BitmapFactory.decodeByteArray(spritesheetBytes, 0, spritesheetBytes.size)
            ?.takeIf { it.width == AtlasWidth && it.height == AtlasHeight }
            ?.let { bitmap ->
                PetSpriteAtlas(
                    image = bitmap.asImageBitmap(),
                    framesByRow = detectNonTransparentFrames(bitmap),
                )
            }
    }
    val frames = atlas?.framesFor(state) ?: listOf(0)
    var frameIndex by remember(state, reducedMotion, frames) { mutableIntStateOf(0) }

    LaunchedEffect(state, reducedMotion, frames) {
        frameIndex = 0
        if (reducedMotion || frames.size <= 1) return@LaunchedEffect
        while (true) {
            delay(120)
            frameIndex = (frameIndex + 1) % frames.size
        }
    }

    Canvas(
        modifier = modifier.aspectRatio(FrameWidth.toFloat() / FrameHeight.toFloat()),
    ) {
        val bitmap = atlas?.image ?: return@Canvas
        val frame = frames.getOrElse(frameIndex) { frames.firstOrNull() ?: 0 }
        drawImage(
            image = bitmap,
            srcOffset = IntOffset(frame * FrameWidth, state.row * FrameHeight),
            srcSize = IntSize(FrameWidth, FrameHeight),
            dstOffset = IntOffset.Zero,
            dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
            filterQuality = FilterQuality.None,
        )
    }
}

private fun detectNonTransparentFrames(bitmap: Bitmap): List<List<Int>> {
    val framePixels = IntArray(FrameWidth * FrameHeight)
    return List(Rows) { row ->
        (0 until Columns).filter { column ->
            bitmap.getPixels(
                framePixels,
                0,
                FrameWidth,
                column * FrameWidth,
                row * FrameHeight,
                FrameWidth,
                FrameHeight,
            )
            framePixels.any { pixel -> (pixel ushr 24) != 0 }
        }.ifEmpty { listOf(0) }
    }
}

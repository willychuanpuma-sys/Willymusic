package com.metrolist.music.ui.player

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.dp
import com.metrolist.music.playback.audio.BrainAudioLevel
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sin

/**
 * Optional visual inspired by the supplied music-brain clip.
 * Reacts to the decoded PCM peak. Playback continues in the existing player;
 * this composable neither creates an audio player nor captures microphone audio.
 */
@Composable
fun BrainMusicVisualizer(
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
) {
    var level by remember { mutableFloatStateOf(0f) }
    var phase by remember { mutableFloatStateOf(0f) }

    DisposableEffect(Unit) {
        BrainAudioLevel.active = true
        onDispose {
            BrainAudioLevel.active = false
            BrainAudioLevel.peak = 0f
        }
    }

    LaunchedEffect(isPlaying) {
        if (!isPlaying) {
            level = 0f
        } else {
            while (true) {
                val peak = BrainAudioLevel.peak
                level = (level * 0.64f + peak * 0.36f).coerceIn(0f, 1f)
                phase += 0.23f
                delay(50)
            }
        }
    }

    val ink = Color(0xFF07131B)
    val steel = Color(0xFFBBC8D4)
    val cyan = Color(0xFF70D5E1)
    val rose = Color(0xFFE1A6D6)
    val energy = if (isPlaying) level else 0f

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .background(ink),
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val extent = min(size.width, size.height)
            val factor = extent / 460f
            withTransform({
                translate(left = (size.width - extent) / 2f, top = (size.height - extent) / 2f)
                scale(scaleX = factor, scaleY = factor, pivot = Offset.Zero)
            }) {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0xFF203746), ink),
                        center = Offset(230f, 228f),
                        radius = 236f,
                    ),
                    radius = 230f,
                    center = Offset(230f, 228f),
                )

                val glowRadius = 166f + energy * 18f
                drawCircle(
                    color = cyan.copy(alpha = 0.05f + energy * 0.12f),
                    radius = glowRadius,
                    center = Offset(230f, 230f),
                )
                drawCircle(
                    color = rose.copy(alpha = 0.09f + energy * 0.10f),
                    radius = 140f,
                    center = Offset(230f, 228f),
                    style = Stroke(width = 1.8f),
                )

                val brain = Path().apply {
                    moveTo(231f, 104f)
                    cubicTo(197f, 78f, 159f, 93f, 143f, 113f)
                    cubicTo(102f, 105f, 82f, 136f, 91f, 164f)
                    cubicTo(58f, 183f, 75f, 217f, 87f, 231f)
                    cubicTo(76f, 267f, 98f, 292f, 129f, 295f)
                    cubicTo(141f, 338f, 188f, 350f, 224f, 328f)
                    cubicTo(232f, 355f, 248f, 354f, 264f, 326f)
                    cubicTo(305f, 349f, 335f, 327f, 339f, 294f)
                    cubicTo(375f, 289f, 392f, 258f, 373f, 232f)
                    cubicTo(402f, 207f, 392f, 179f, 367f, 162f)
                    cubicTo(381f, 129f, 349f, 105f, 320f, 113f)
                    cubicTo(291f, 87f, 258f, 84f, 231f, 104f)
                    close()
                }
                drawPath(
                    path = brain,
                    brush = Brush.verticalGradient(
                        colors = listOf(Color(0xFFC1CCD5), Color(0xFF77899D), Color(0xFF344354)),
                        startY = 92f,
                        endY = 357f,
                    ),
                )
                drawPath(brain, color = Color(0xFFA9DBE5).copy(alpha = 0.55f), style = Stroke(2.6f))

                // Rounded folds evoke the clip without adding an expensive second video decoder.
                repeat(6) { row ->
                    val y = 126f + row * 34f
                    val leftFold = Path().apply {
                        moveTo(217f, y - 7f)
                        cubicTo(183f, y - 28f, 169f, y + 23f, 148f, y - 3f)
                        cubicTo(126f, y - 22f, 107f, y + 15f, 112f, y + 25f)
                    }
                    val rightFold = Path().apply {
                        moveTo(243f, y - 7f)
                        cubicTo(276f, y - 27f, 293f, y + 23f, 315f, y - 3f)
                        cubicTo(343f, y - 22f, 355f, y + 16f, 350f, y + 25f)
                    }
                    drawPath(leftFold, color = Color(0xFF2F465A).copy(alpha = 0.8f), style = Stroke(3f))
                    drawPath(rightFold, color = Color(0xFF2F465A).copy(alpha = 0.8f), style = Stroke(3f))
                }
                drawLine(
                    color = Color(0xFF3B5265),
                    start = Offset(230f, 122f),
                    end = Offset(230f, 318f),
                    strokeWidth = 3f,
                )

                drawLine(
                    color = cyan.copy(alpha = .23f),
                    start = Offset(79f, 230f),
                    end = Offset(382f, 230f),
                    strokeWidth = 8f,
                    cap = StrokeCap.Round,
                )
                for (i in 0..65) {
                    val x = 83f + i * (294f / 65f)
                    val taper = (1f - abs(i - 32.5f) / 35f).coerceAtLeast(.18f)
                    val beat = abs(sin(i * .48f + phase)) * .55f + .45f
                    val height = (6f + 71f * energy) * taper * beat
                    drawLine(
                        color = if (i % 4 < 2) cyan else rose,
                        start = Offset(x, 229f - height),
                        end = Offset(x, 229f + height),
                        strokeWidth = 2.7f,
                        cap = StrokeCap.Round,
                    )
                }
                drawCircle(
                    color = Color.White.copy(alpha = .06f + energy * .18f),
                    radius = 6f + energy * 16f,
                    center = Offset(230f, 229f),
                )
            }
        }
    }
}

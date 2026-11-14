package io.celox.pegel.ui.sound

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import io.celox.pegel.audio.GAIN_LIMIT_DB
import io.celox.pegel.audio.curveSamples
import io.celox.pegel.ui.theme.Motion
import io.celox.pegel.ui.theme.rememberReducedMotion

private const val SAMPLES = 96

/**
 * The line through the ten sliders. Decorative — the sliders below carry the values for
 * accessibility — and drawn with the same horizontal insets as the slider row, so every bend sits
 * above its slider.
 */
@Composable
fun CurveView(gainsDb: List<Float>, active: Boolean, sideInsetFraction: Float, modifier: Modifier = Modifier) {
    val reduce = rememberReducedMotion()
    // Each band glides to its new value (preset changes morph); snaps when animations are off.
    val animated = gainsDb.map { target ->
        val v by animateFloatAsState(target, if (reduce) androidx.compose.animation.core.snap() else Motion.effects(), label = "gain")
        v
    }
    val line = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
    val zero = MaterialTheme.colorScheme.outlineVariant

    Canvas(modifier.clearAndSetSemantics { }) {
        val inset = size.width * sideInsetFraction
        val w = size.width - 2 * inset
        val mid = size.height / 2f
        val pad = 4.dp.toPx()
        val scale = (mid - pad) / GAIN_LIMIT_DB
        drawLine(zero, Offset(0f, mid), Offset(size.width, mid), strokeWidth = 1.dp.toPx())

        val samples = curveSamples(animated, SAMPLES)
        fun x(i: Int) = inset + w * i / (SAMPLES - 1)
        fun y(db: Float) = mid - db * scale
        val path = Path().apply {
            moveTo(0f, y(samples.first()))
            samples.forEachIndexed { i, db -> lineTo(x(i), y(db)) }
            lineTo(size.width, y(samples.last()))
        }
        val fill = Path().apply {
            addPath(path)
            lineTo(size.width, mid); lineTo(0f, mid); close()
        }
        drawPath(fill, line.copy(alpha = 0.16f))
        drawPath(path, line, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round))
    }
}

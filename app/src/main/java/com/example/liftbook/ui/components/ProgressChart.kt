package com.example.liftbook.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.example.liftbook.ui.theme.LiftBookPreview
import com.example.liftbook.ui.theme.Spacing
import com.example.liftbook.ui.theme.ThemePreviews
import com.example.liftbook.ui.theme.tabularNumbers
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.abs

/**
 * A value on a chart, in the unit the chart shows — pounds, minutes, reps — at [x], a moment in
 * time counted in days, fractions included. Placing points by the moment rather than the day
 * keeps two workouts on the same day apart instead of stacking them into a vertical line.
 */
@Immutable
data class ChartPoint(val x: Double, val value: Double) {
    companion object {
        /** A value for a whole day, such as a weigh-in. */
        fun on(day: LocalDate, value: Double): ChartPoint = ChartPoint(day.toEpochDay().toDouble(), value)

        /** A value from a moment — a workout's start — placed by its day and time in [zone]. */
        fun at(instant: Instant, zone: ZoneId, value: Double): ChartPoint {
            val local = instant.atZone(zone)
            return ChartPoint(local.toLocalDate().toEpochDay() + local.toLocalTime().toSecondOfDay() / SECONDS_PER_DAY, value)
        }
    }
}

/**
 * A value over time, drawn by hand on a Canvas (FR-5.1, FR-5.4): a line in the accent — progress
 * is what the accent is for — across a few dashed gridlines at round values, labelled on the
 * trailing edge, with the first and last day beneath. Points are spaced by time, not by count, so a
 * month off reads as a gap. The latest point stands out; it's the one the user came to see.
 *
 * With a [trend], [points] are drawn as quiet dots — the raw readings — and the line follows the
 * trend instead, as body weight wants.
 *
 * With [onSelect], pressing and dragging picks the nearest point, and letting go gives it back
 * (null) — the screen shows the picked point's value in place of the latest. TalkBack hears
 * [contentDescription]; each value should also be listed on the screen, since a chart can't be
 * read point by point.
 */
@Composable
fun ProgressChart(
    points: List<ChartPoint>,
    axisLabel: (Double) -> String,
    startLabel: String,
    endLabel: String,
    contentDescription: String,
    modifier: Modifier = Modifier,
    trend: List<ChartPoint>? = null,
    selectedIndex: Int? = null,
    onSelect: ((Int?) -> Unit)? = null,
    containerColor: Color = MaterialTheme.colorScheme.background,
) {
    if (points.isEmpty()) return
    val colors = MaterialTheme.colorScheme
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelSmall.tabularNumbers().copy(color = colors.onSurfaceVariant)
    val domain = remember(points, trend) { ChartDomain.of(points, trend) }
    val tickLabels = domain.scale.ticks.map(axisLabel)
    val ticks = remember(tickLabels, labelStyle) { tickLabels.map { measurer.measure(it, labelStyle) } }
    val start = remember(startLabel, labelStyle) { measurer.measure(startLabel, labelStyle) }
    val end = remember(endLabel, labelStyle) { measurer.measure(endLabel, labelStyle) }
    var size by remember { mutableStateOf(IntSize.Zero) }
    val geometry = remember(size, domain, ticks, start, density) { ChartGeometry.of(size, domain, ticks, start, density, withLabels = true) }
    val currentOnSelect by rememberUpdatedState(onSelect)

    val selection = if (onSelect == null) {
        Modifier
    } else {
        // Tap and drag share one flag, so a drag taking over a press doesn't drop the selection.
        val gesture = remember { GestureState() }
        Modifier
            .pointerInput(geometry, points) {
                detectTapGestures(
                    onPress = { offset ->
                        currentOnSelect?.invoke(geometry.nearest(points, offset.x))
                        tryAwaitRelease()
                        if (!gesture.dragging) currentOnSelect?.invoke(null)
                    },
                )
            }
            .pointerInput(geometry, points) {
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        gesture.dragging = true
                        currentOnSelect?.invoke(geometry.nearest(points, offset.x))
                    },
                    onDragEnd = {
                        gesture.dragging = false
                        currentOnSelect?.invoke(null)
                    },
                    onDragCancel = {
                        gesture.dragging = false
                        currentOnSelect?.invoke(null)
                    },
                ) { change, _ ->
                    change.consume()
                    currentOnSelect?.invoke(geometry.nearest(points, change.position.x))
                }
            }
    }

    Canvas(
        modifier
            .fillMaxWidth()
            .height(ChartHeight)
            .onSizeChanged { size = it }
            .semantics { this.contentDescription = contentDescription }
            .then(selection),
    ) {
        val plot = geometry.plot
        val dash = PathEffect.dashPathEffect(floatArrayOf(DashOn.toPx(), DashOff.toPx()))
        domain.scale.ticks.forEachIndexed { index, tick ->
            val y = geometry.y(tick)
            drawLine(colors.outlineVariant, Offset(plot.left, y), Offset(plot.right, y), GridStroke.toPx(), pathEffect = dash)
            val label = ticks[index]
            drawText(label, topLeft = Offset(size.width - label.size.width.toFloat(), y - label.size.height / 2f))
        }
        val labelTop = plot.bottom + LabelGap.toPx()
        // Everything on one day: one label, under the middle.
        if (domain.firstX == domain.lastX || startLabel == endLabel) {
            drawText(start, topLeft = Offset(plot.center.x - start.size.width / 2f, labelTop))
        } else {
            drawText(start, topLeft = Offset(plot.left, labelTop))
            drawText(end, topLeft = Offset(plot.right - end.size.width, labelTop))
        }

        val selected = selectedIndex?.let(points::getOrNull)
        if (selected != null) {
            val x = geometry.x(selected.x)
            drawLine(colors.onSurfaceVariant, Offset(x, plot.top), Offset(x, plot.bottom), GridStroke.toPx())
        }

        if (trend != null) {
            points.forEach { drawCircle(colors.onSurfaceVariant, ReadingRadius.toPx(), geometry.offset(it)) }
            drawSeries(trend, geometry, colors.primary)
            trend.lastOrNull()?.let { drawEmphasis(geometry.offset(it), colors.primary, containerColor) }
        } else {
            drawSeries(points, geometry, colors.primary)
            if (points.size <= DOT_LIMIT) points.forEach { drawCircle(colors.primary, DotRadius.toPx(), geometry.offset(it)) }
            drawEmphasis(geometry.offset(points.last()), colors.primary, containerColor)
        }
        if (selected != null) drawEmphasis(geometry.offset(selected), colors.primary, containerColor, SelectedRadius)
    }
}

/**
 * A chart's line alone, small, for a preview beside the number it leads to. No labels, no
 * gridlines, not interactive: tap through to the full chart for those.
 */
@Composable
fun Sparkline(
    points: List<ChartPoint>,
    modifier: Modifier = Modifier,
    trend: List<ChartPoint>? = null,
    containerColor: Color = MaterialTheme.colorScheme.background,
) {
    if (points.isEmpty()) return
    val colors = MaterialTheme.colorScheme
    val density = LocalDensity.current
    val domain = remember(points, trend) { ChartDomain.of(points, trend) }
    var size by remember { mutableStateOf(IntSize.Zero) }
    val geometry = remember(size, domain, density) { ChartGeometry.of(size, domain, emptyList(), null, density, withLabels = false) }
    Canvas(modifier.onSizeChanged { size = it }) {
        val line = trend ?: points
        drawSeries(line, geometry, colors.primary)
        line.lastOrNull()?.let { drawEmphasis(geometry.offset(it), colors.primary, containerColor) }
    }
}

private fun DrawScope.drawSeries(series: List<ChartPoint>, geometry: ChartGeometry, color: Color) {
    if (series.size < 2) return
    val path = Path()
    series.forEachIndexed { index, point ->
        val offset = geometry.offset(point)
        if (index == 0) path.moveTo(offset.x, offset.y) else path.lineTo(offset.x, offset.y)
    }
    drawPath(path, color, style = Stroke(width = LineStroke.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
}

/** A point that matters: a dot, ringed in the background colour so it lifts off the line. */
private fun DrawScope.drawEmphasis(center: Offset, color: Color, ring: Color, radius: Dp = EmphasisRadius) {
    drawCircle(ring, (radius + RingWidth).toPx(), center)
    drawCircle(color, radius.toPx(), center)
}

/** The data's extent: the time it spans and the value axis that fits it. */
private class ChartDomain(val firstX: Double, val lastX: Double, val scale: ChartScale) {
    companion object {
        fun of(points: List<ChartPoint>, trend: List<ChartPoint>?): ChartDomain {
            val all = points + trend.orEmpty()
            return ChartDomain(
                firstX = all.minOf { it.x },
                lastX = all.maxOf { it.x },
                scale = chartScale(all.minOf { it.value }, all.maxOf { it.value }),
            )
        }
    }
}

/** Where things go on the canvas: the plot area inside the labels, and a point's place in it. */
private class ChartGeometry(val plot: Rect, private val domain: ChartDomain) {

    fun x(at: Double): Float {
        val span = domain.lastX - domain.firstX
        if (span <= 0.0) return plot.center.x
        return plot.left + ((at - domain.firstX) / span).toFloat() * plot.width
    }

    fun y(value: Double): Float = plot.bottom - domain.scale.fraction(value) * plot.height

    fun offset(point: ChartPoint): Offset = Offset(x(point.x), y(point.value))

    /** The index of the point nearest across to [x]. */
    fun nearest(points: List<ChartPoint>, x: Float): Int? =
        points.indices.minByOrNull { abs(x(points[it].x) - x) }

    companion object {
        fun of(
            size: IntSize,
            domain: ChartDomain,
            ticks: List<TextLayoutResult>,
            dayLabel: TextLayoutResult?,
            density: Density,
            withLabels: Boolean,
        ): ChartGeometry = with(density) {
            // Dots sit at the very ends of the line; inset by the largest so none is clipped.
            val inset = (SelectedRadius + RingWidth).toPx()
            if (!withLabels) {
                return ChartGeometry(Rect(inset, inset, size.width - inset, size.height - inset), domain)
            }
            val gutter = (ticks.maxOfOrNull { it.size.width } ?: 0) + LabelGap.toPx()
            val top = maxOf(inset, (ticks.firstOrNull()?.size?.height ?: 0) / 2f)
            val bottom = size.height - (dayLabel?.size?.height ?: 0) - LabelGap.toPx()
            ChartGeometry(Rect(inset, top, size.width - gutter - inset, bottom.coerceAtLeast(top)), domain)
        }
    }
}

private class GestureState {
    var dragging = false
}

private val ChartHeight = 200.dp
private val LabelGap = Spacing.xs
private val LineStroke = 2.dp
private val GridStroke = 1.dp
private val DashOn = 2.dp
private val DashOff = 4.dp
private val DotRadius = 3.dp
private val ReadingRadius = 2.5.dp
private val EmphasisRadius = 4.5.dp
private val SelectedRadius = 6.dp
private val RingWidth = 2.dp

/** More points than this and dots would crowd into a bead chain; the line says enough. */
private const val DOT_LIMIT = 24
private const val SECONDS_PER_DAY = 86_400.0

@ThemePreviews
@Composable
private fun ProgressChartPreview() {
    val start = LocalDate.of(2026, 6, 26)
    val values = listOf(100.0, 102.5, 101.7, 105.0, 106.7, 105.0, 108.3, 110.0, 109.2, 112.5, 113.3, 116.7)
    val points = values.mapIndexed { index, value -> ChartPoint.on(start.plusDays(index * 8L), value) }
    LiftBookPreview {
        Column(Modifier.padding(Spacing.gutter)) {
            ProgressChart(
                points = points,
                axisLabel = { it.toInt().toString() },
                startLabel = "26 Jun",
                endLabel = "24 Sep",
                contentDescription = "",
                selectedIndex = 8,
                onSelect = {},
            )
            Sparkline(points, Modifier.padding(top = Spacing.lg).fillMaxWidth().height(Spacing.xxl))
        }
    }
}

@ThemePreviews
@Composable
private fun BodyWeightChartPreview() {
    val start = LocalDate.of(2026, 8, 1)
    val readings = listOf(83.4, 83.1, 83.6, 82.9, 83.0, 82.6, 82.8, 82.4, 82.7, 82.2, 82.5, 82.0, 82.3, 81.9)
    val points = readings.mapIndexed { index, value -> ChartPoint.on(start.plusDays(index * 4L), value) }
    val trend = points.mapIndexed { index, point ->
        ChartPoint(point.x, points.subList(maxOf(0, index - 1), index + 1).map { it.value }.average())
    }
    LiftBookPreview {
        ProgressChart(
            points = points,
            trend = trend,
            axisLabel = { "%.1f".format(it) },
            startLabel = "1 Aug",
            endLabel = "22 Sep",
            contentDescription = "",
            modifier = Modifier.padding(Spacing.gutter),
        )
    }
}

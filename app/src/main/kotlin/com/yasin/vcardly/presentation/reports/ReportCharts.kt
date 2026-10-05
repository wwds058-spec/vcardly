package com.yasin.vcardly.presentation.reports

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.yasin.vcardly.core.designsystem.theme.StatValueStyle
import com.yasin.vcardly.domain.report.MonthCount
import java.text.NumberFormat
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.ceil

/*
 * Chart rules (dataviz method): identity colours come from the entity (category colour, status), never from rank;
 * one series needs no legend; two or more always get a legend with values in text colours; thin marks with rounded
 * ends and 2dp surface gaps between segments; every chart carries a spoken summary so nothing relies on colour alone.
 */

/** One slice/segment: label, value and the colour that identifies it. */
data class Segment(val label: String, val value: Int, val color: Color)

/** Donut with the total in the centre and a legend (dot, label, percentage) beside it. Animates in once. */
@Composable
fun DonutChart(segments: List<Segment>, centerLabel: String, modifier: Modifier = Modifier, size: Dp = 132.dp) {
    val number = remember { NumberFormat.getInstance() }
    val percent = remember { NumberFormat.getPercentInstance() }
    val total = segments.sumOf { it.value }.coerceAtLeast(1)
    val sweep = remember { Animatable(0f) }
    LaunchedEffect(segments) { sweep.snapTo(0f); sweep.animateTo(1f, tween(700, easing = FastOutSlowInEasing)) }
    val gapColor = MaterialTheme.colorScheme.surfaceContainer
    val summary = segments.joinToString(", ") { "${it.label} ${percent.format(it.value.toDouble() / total)}" }

    Row(modifier.fillMaxWidth().semantics(mergeDescendants = true) { contentDescription = summary }, verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(size).clearAndSetSemantics { }, contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(size)) {
                val stroke = this.size.width * 0.16f
                val inset = stroke / 2
                val arcSize = Size(this.size.width - stroke, this.size.height - stroke)
                var start = -90f
                segments.forEach { s ->
                    val angle = 360f * s.value / total * sweep.value
                    drawArc(s.color, start, angle, useCenter = false, topLeft = Offset(inset, inset), size = arcSize, style = Stroke(stroke))
                    // 2dp surface gap between neighbours.
                    if (segments.size > 1) drawArc(gapColor, start + angle - 0.8f, 1.6f, useCenter = false, topLeft = Offset(inset, inset), size = arcSize, style = Stroke(stroke + 2f))
                    start += angle
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(number.format(segments.sumOf { it.value }), style = StatValueStyle, color = MaterialTheme.colorScheme.onSurface)
                Text(centerLabel, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Column(Modifier.padding(start = 20.dp).weight(1f).clearAndSetSemantics { }, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            segments.forEach { s ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(10.dp).clip(CircleShape).background(s.color))
                    Text(s.label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, modifier = Modifier.padding(start = 8.dp).weight(1f))
                    Text(percent.format(s.value.toDouble() / total), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
                }
            }
        }
    }
}

/** Horizontal stacked bar (e.g. follow-up health) with a legend underneath. */
@Composable
fun StackedBar(segments: List<Segment>, modifier: Modifier = Modifier) {
    val number = remember { NumberFormat.getInstance() }
    val percent = remember { NumberFormat.getPercentInstance() }
    val total = segments.sumOf { it.value }
    val summary = segments.joinToString(", ") { "${it.label} ${number.format(it.value)}" }
    Column(modifier.fillMaxWidth().semantics(mergeDescendants = true) { contentDescription = summary }) {
        Row(Modifier.fillMaxWidth().height(14.dp).clip(RoundedCornerShape(7.dp)), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            if (total == 0) {
                Box(Modifier.fillMaxWidth().fillMaxHeight().background(MaterialTheme.colorScheme.surfaceContainerHighest))
            } else {
                segments.filter { it.value > 0 }.forEach { s ->
                    Box(Modifier.weight(s.value.toFloat()).fillMaxHeight().background(s.color))
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 12.dp).clearAndSetSemantics { }, horizontalArrangement = Arrangement.SpaceBetween) {
            segments.forEach { s ->
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(8.dp).clip(CircleShape).background(s.color))
                        Text(s.label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 6.dp))
                    }
                    Text(
                        if (total == 0) number.format(0) else "${number.format(s.value)} · ${percent.format(s.value.toDouble() / total)}",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(start = 14.dp),
                    )
                }
            }
        }
    }
}

/**
 * Contact growth: one series of rounded bars per month in a single brand gradient (no legend needed), months along a
 * recessive baseline; each bar announces its month and value to TalkBack.
 */
@Composable
fun GrowthChart(items: List<MonthCount>, color: Color, modifier: Modifier = Modifier, chartHeight: Dp = 140.dp) {
    val number = remember { NumberFormat.getInstance() }
    val short = remember { DateTimeFormatter.ofPattern("MMM", Locale.getDefault()) }
    val long = remember { DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault()) }
    val max = items.maxOfOrNull { it.count }?.coerceAtLeast(1) ?: 1
    val labelEvery = ceil(items.size / 6f).toInt().coerceAtLeast(1)
    val grow = remember { Animatable(0f) }
    LaunchedEffect(items) { grow.snapTo(0f); grow.animateTo(1f, tween(650, easing = FastOutSlowInEasing)) }
    val track = MaterialTheme.colorScheme.surfaceContainerHighest

    Column(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().height(chartHeight), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            items.forEach { m ->
                val description = "${long.format(m.month)}: ${number.format(m.count)}"
                Column(
                    Modifier.weight(1f).fillMaxHeight().semantics(mergeDescendants = true) { contentDescription = description },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Bottom,
                ) {
                    // Every column has the same plot height (the headline total sits above the chart), so bars stay comparable.
                    Canvas(Modifier.fillMaxWidth(0.62f).weight(1f, fill = true)) {
                        val h = size.height * (m.count.toFloat() / max) * grow.value
                        val w = size.width
                        drawLine(track, Offset(w / 2, 0f), Offset(w / 2, size.height), strokeWidth = w, cap = StrokeCap.Round)
                        if (m.count > 0) {
                            val top = (size.height - h).coerceAtMost(size.height - w / 2)
                            drawLine(
                                Brush.verticalGradient(listOf(color, color.copy(alpha = 0.7f))),
                                Offset(w / 2, top + w / 2),
                                Offset(w / 2, size.height - w / 2),
                                strokeWidth = w,
                                cap = StrokeCap.Round,
                            )
                        }
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            items.forEachIndexed { i, m ->
                Text(
                    text = if (i % labelEvery == 0) short.format(m.month) else "",
                    modifier = Modifier.weight(1f).clearAndSetSemantics { },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                )
            }
        }
    }
}

data class BarItem(val label: String, val value: Int)

/** Ranked horizontal bars: label and value in text colours, one hue on a quiet track. */
@Composable
fun HorizontalBars(items: List<BarItem>, color: Color, modifier: Modifier = Modifier) {
    val number = remember { NumberFormat.getInstance() }
    val max = items.maxOfOrNull { it.value }?.coerceAtLeast(1) ?: 1
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        items.forEach { item ->
            Column(Modifier.fillMaxWidth().semantics(mergeDescendants = true) { contentDescription = "${item.label}: ${number.format(item.value)}" }) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(item.label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                    Text(number.format(item.value), style = MaterialTheme.typography.titleSmall)
                }
                Box(Modifier.padding(top = 6.dp).fillMaxWidth().height(8.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHighest)) {
                    if (item.value > 0) {
                        Box(Modifier.fillMaxHeight().fillMaxWidth((item.value.toFloat() / max).coerceIn(0.03f, 1f)).clip(CircleShape).background(color))
                    }
                }
            }
        }
    }
}

package com.yasin.vcardly.presentation.reports

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.yasin.vcardly.core.designsystem.theme.spacing
import com.yasin.vcardly.domain.report.MonthCount
import java.text.NumberFormat
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.ceil

/*
 * Chart rules used here (one hue for magnitude, thin rounded marks anchored to the baseline, direct value labels,
 * recessive track, text in text colours, a spoken description per bar so nothing relies on colour or shape alone).
 */

private val BarShape = RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp)
private val TrackShape = RoundedCornerShape(4.dp)

/** One column per month. Counts are printed above the bars (all of them up to 12 months, otherwise only the tallest). */
@Composable
fun MonthlyBarChart(items: List<MonthCount>, modifier: Modifier = Modifier, chartHeight: Dp = 150.dp) {
    val number = remember { NumberFormat.getInstance() }
    val short = remember { DateTimeFormatter.ofPattern("MMM", Locale.getDefault()) }
    val long = remember { DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault()) }
    val max = items.maxOfOrNull { it.count }?.coerceAtLeast(1) ?: 1
    val labelEvery = ceil(items.size / 12f).toInt().coerceAtLeast(1)
    val showAllCounts = items.size <= 12
    val textHeight = 16.dp

    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().height(chartHeight),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            items.forEachIndexed { i, m ->
                val barArea = chartHeight - textHeight
                val barHeight = if (m.count == 0) 0.dp else (barArea * (m.count.toFloat() / max)).coerceAtLeast(3.dp)
                val description = "${long.format(m.month)}: ${number.format(m.count)}"
                Column(
                    Modifier.weight(1f).semantics(mergeDescendants = true) { contentDescription = description },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Bottom,
                ) {
                    if (m.count > 0 && (showAllCounts || m.count == max)) {
                        Text(number.format(m.count), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
                    }
                    Box(Modifier.fillMaxWidth(0.7f).height(barHeight).clip(BarShape).background(MaterialTheme.colorScheme.primary))
                }
            }
        }
        // Baseline.
        Box(Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
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

/** Ranked horizontal bars: label and value in text colours, a single-hue fill on a quiet track. */
@Composable
fun HorizontalBars(items: List<BarItem>, modifier: Modifier = Modifier) {
    val number = remember { NumberFormat.getInstance() }
    val max = items.maxOfOrNull { it.value }?.coerceAtLeast(1) ?: 1
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm)) {
        items.forEach { item ->
            Column(Modifier.fillMaxWidth().semantics(mergeDescendants = true) { contentDescription = "${item.label}: ${number.format(item.value)}" }) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(item.label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                    Text(number.format(item.value), style = MaterialTheme.typography.bodyMedium)
                }
                Box(Modifier.padding(top = 4.dp).fillMaxWidth().height(8.dp).clip(TrackShape).background(MaterialTheme.colorScheme.surfaceVariant)) {
                    if (item.value > 0) {
                        Box(
                            Modifier.fillMaxHeight().fillMaxWidth((item.value.toFloat() / max).coerceIn(0.02f, 1f))
                                .clip(TrackShape).background(MaterialTheme.colorScheme.primary),
                        )
                    }
                }
            }
        }
    }
}

/** A single proportion (e.g. follow-ups completed) as a bar with its percentage as text. */
@Composable
fun ProportionBar(fraction: Float, label: String, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().semantics(mergeDescendants = true) { contentDescription = label }) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Box(Modifier.padding(top = 4.dp).fillMaxWidth().height(8.dp).clip(TrackShape).background(MaterialTheme.colorScheme.surfaceVariant)) {
            Box(Modifier.fillMaxHeight().fillMaxWidth(fraction.coerceIn(0f, 1f)).clip(TrackShape).background(MaterialTheme.colorScheme.primary))
        }
    }
}

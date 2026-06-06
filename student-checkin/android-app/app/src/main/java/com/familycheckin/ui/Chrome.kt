package com.familycheckin.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun AppHeader(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    trailing: @Composable (RowScope.() -> Unit)? = null
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(title, style = MaterialTheme.typography.headlineMedium, color = FamilyPalette.Ink)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = FamilyPalette.InkSoft)
        }
        if (trailing != null) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                content = trailing
            )
        }
    }
}

@Composable
fun BackHeader(
    title: String,
    subtitle: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        GhostPill(text = "返回", onClick = onBack)
        Text(title, style = MaterialTheme.typography.headlineMedium, color = FamilyPalette.Ink)
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = FamilyPalette.InkSoft)
    }
}

@Composable
fun SectionSurface(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = FamilyPalette.Surface,
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(1.dp, FamilyPalette.Line)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            content = content
        )
    }
}

@Composable
fun StatusBanner(message: String, modifier: Modifier = Modifier) {
    if (message.isBlank()) return

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = FamilyPalette.SurfaceMuted,
        shape = MaterialTheme.shapes.medium
    ) {
        Text(
            text = message,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = FamilyPalette.Ink
        )
    }
}

@Composable
fun MetricTile(
    title: String,
    value: String,
    note: String,
    modifier: Modifier = Modifier,
    emphasized: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    val tone = if (emphasized) FamilyPalette.SurfaceAccent else Color.White
    val displayValue = value.normalizeMetricValueForTitle(title)
    Surface(
        modifier = if (onClick == null) modifier else modifier.clickable(onClick = onClick),
        color = tone,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, FamilyPalette.Line)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(title, style = MaterialTheme.typography.labelSmall, color = FamilyPalette.InkSoft)
            Text(displayValue, style = MaterialTheme.typography.headlineSmall, color = FamilyPalette.Ink)
            Text(note, style = MaterialTheme.typography.bodySmall, color = FamilyPalette.InkSoft)
        }
    }
}

private fun String.normalizeMetricValueForTitle(title: String): String {
    val normalizedTitle = title.trim()
    return when (normalizedTitle) {
        "昨天" -> trim()
            .removePrefix("昨天 ")
            .removePrefix("Yesterday ")
            .trim()

        "最近7天" -> trim()
            .removePrefix("最近7天 ")
            .removePrefix("Last 7 days ")
            .trim()

        "最近30天" -> trim()
            .removePrefix("最近30天 ")
            .removePrefix("Last 30 days ")
            .trim()

        else -> this
    }
}

@Composable
fun FilterPill(
    text: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val background = if (selected) FamilyPalette.AccentStrong else Color.White
    val contentColor = if (selected) Color.White else FamilyPalette.Ink
    Surface(
        modifier = Modifier.clickable(onClick = onClick),
        color = background,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, if (selected) FamilyPalette.AccentStrong else FamilyPalette.Line)
    ) {
        Text(
            text = text,
            color = contentColor,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
        )
    }
}

@Composable
fun PrimaryPill(text: String, onClick: () -> Unit, enabled: Boolean = true) {
    Button(
        onClick = onClick,
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(
            containerColor = FamilyPalette.Accent,
            contentColor = Color.White,
            disabledContainerColor = FamilyPalette.Line,
            disabledContentColor = FamilyPalette.InkSoft
        )
    ) {
        Text(text)
    }
}

@Composable
fun GhostPill(text: String, onClick: () -> Unit, enabled: Boolean = true) {
    Surface(
        modifier = Modifier.clickable(enabled = enabled, onClick = onClick),
        color = Color.White,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, FamilyPalette.Line)
    ) {
        Box(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                color = if (enabled) FamilyPalette.Ink else FamilyPalette.InkSoft
            )
        }
    }
}

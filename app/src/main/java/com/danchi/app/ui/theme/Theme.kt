package com.danchi.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

private val DanChiColors = lightColorScheme(
    primary = Color(0xFF0F766E),
    onPrimary = Color.White,
    secondary = Color(0xFF7C3AED),
    onSecondary = Color.White,
    tertiary = Color(0xFFB45309),
    surface = Color(0xFFFAFAF9),
    surfaceVariant = Color(0xFFE7E5E4),
    background = Color(0xFFF8FAFC),
    error = Color(0xFFB91C1C)
)

private val DanChiShapes = Shapes(
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(8.dp),
    large = RoundedCornerShape(8.dp)
)

@Composable
fun DanChiTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DanChiColors,
        shapes = DanChiShapes,
        typography = MaterialTheme.typography,
        content = content
    )
}

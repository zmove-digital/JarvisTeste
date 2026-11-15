package com.example.jarvisteste.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object JarvisColors {
    val BackgroundTop = Color(0xFF101C2B)
    val BackgroundMid = Color(0xFF0A111C)
    val BackgroundBottom = Color(0xFF080D15)
    val Surface = Color(0xFF172536)
    val SurfaceAlt = Color(0xFF16202E)
    val SurfaceSelected = Color(0xFF1B3A54)
    val Border = Color(0xFF263D53)
    val BorderSelected = Color(0xFF66B9E8)
    val Accent = Color(0xFF70BDEB)
    val AccentSoft = Color(0xFFB8E7FF)
    val TextPrimary = Color.White
    val TextSecondary = Color(0xFFA8B9CC)
    val TextMuted = Color(0xFF8194A9)
    val Success = Color(0xFF75E6AE)
    val SuccessSurface = Color(0xFF153B32)
    val Danger = Color(0xFFFF8A8A)
}

@Composable
fun JarvisBackground(content: @Composable BoxScope.() -> Unit) {
    val gradient = Brush.verticalGradient(
        colors = listOf(
            JarvisColors.BackgroundTop,
            JarvisColors.BackgroundMid,
            JarvisColors.BackgroundBottom
        )
    )
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(gradient),
        content = content
    )
}

@Composable
fun JarvisLabel(text: String) {
    Text(
        text = text.uppercase(),
        color = JarvisColors.Accent,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 2.sp
    )
}

@Composable
fun JarvisPrimaryButton(
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp),
        enabled = enabled,
        shape = RoundedCornerShape(50),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color(0xFF17699A),
            contentColor = Color.White,
            disabledContainerColor = Color(0xFF1B3047),
            disabledContentColor = Color(0xFF7A93AA)
        )
    ) {
        Text(
            text = text.uppercase(),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.5.sp
        )
    }
}

@Composable
fun JarvisGhostButton(
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    TextButton(onClick = onClick, modifier = modifier, enabled = enabled) {
        Text(
            text = text.uppercase(),
            color = Color(0xFF8BBFE0),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.5.sp
        )
    }
}

@Composable
fun SelectableCard(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        color = if (selected) JarvisColors.SurfaceSelected else JarvisColors.SurfaceAlt,
        border = BorderStroke(
            1.dp,
            if (selected) JarvisColors.BorderSelected else JarvisColors.Border
        )
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 13.dp),
            content = content
        )
    }
}

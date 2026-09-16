package com.example.shiptracker.ui

import android.R
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

@Composable
fun TacticalTelemetryPanel(
    targetMmsi: String?,
    vesselName: String?,
    vesselType: String?,
    speed: Double,
    heading: Double,
    isFleetMember: Boolean,
    isVisible: Boolean,
    onToggleFleet: (String) -> Unit,
    onOpenIntel: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    // This makes the panel slide in from the right edge
    AnimatedVisibility(
        visible = isVisible,
        enter = slideInHorizontally(initialOffsetX = { fullWidth -> fullWidth }) + fadeIn(),
        exit = slideOutHorizontally(targetOffsetX = { fullWidth -> fullWidth }) + fadeOut(),
        modifier = modifier.fillMaxSize()
    ) {
        Box(contentAlignment = Alignment.CenterEnd) {
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(0.45f) // Slightly wider to fit longer vessel names
                    .background(Color(0xCC00140A)) // Semi-transparent tactical green/black
                    .border(2.dp, Color(0xFF00FF41))
                    .padding(16.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { /* Consumes taps inside the HUD panel so they don't dismiss it */ },
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // TOP SECTION: Telemetry Data
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "TARGET LOCKED",
                            color = Color.Red,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 16.sp
                        )
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Dismiss HUD",
                                tint = Color(0xFF00FF41)
                            )
                        }
                    }

                    HudDataRow(label = "CALLSIGN / NAME", value = vesselName?.ifBlank { "UNKNOWN" } ?: "UNKNOWN")
                    HudDataRow(label = "CLASS", value = vesselType ?: "CARGO / GENERAL")
                    HudDataRow(label = "MMSI", value = targetMmsi ?: "---")
                    HudDataRow(label = "VELOCITY", value = String.format(Locale.US, "%.1f KTS", speed))
                    HudDataRow(label = "BEARING", value = String.format(Locale.US, "%.0f°", heading))
                    HudDataRow(label = "DATALINK", value = "SECURE")
                }

                // BOTTOM SECTION: Action Controls
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // NEW BUTTON: The bridge to the full AIS screen
                    OutlinedButton(
                        onClick = { targetMmsi?.let { onOpenIntel(it) } },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF00FF41)),
                        border = BorderStroke(1.dp, Color(0xFF00FF41))
                    ) {
                        Text(
                            text = "DEEP SCAN (AIS)",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp
                        )
                    }

                    // Tactical Fleet Toggle Button
                    OutlinedButton(
                        onClick = { targetMmsi?.let { onToggleFleet(it) } },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = if (isFleetMember) Color(0xFFFFD700) else Color(0xFF00FF41)
                        ),
                        border = BorderStroke(
                            1.dp,
                            if (isFleetMember) Color(0xFFFFD700) else Color(0xFF00FF41)
                        )
                    ) {
                        Text(
                            text = if (isFleetMember) "★ IN FLEET" else "☆ ADD TO FLEET",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp
                        )
                    }

                    // Unlock / Dismiss Button
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF00FF41)),
                        border = BorderStroke(1.dp, Color(0xFF00FF41))
                    ) {
                        Text(
                            text = "DISMISS / UNLOCK",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun HudDataRow(label: String, value: String) {
    Column(modifier = Modifier.padding(bottom = 12.dp)) {
        Text(
            text = label,
            color = Color(0xFF00FF41).copy(alpha = 0.6f),
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace
        )
        Text(
            text = value,
            color = Color(0xFF00FF41),
            fontSize = 16.sp,
            fontFamily = FontFamily.Monospace
        )
    }
}

@Composable
fun LockOnReticle(
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "ReticleTransition")

    // Creates a pulsing breathing effect
    val scale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ReticleScale"
    )

    // Slowly rotates the outer bracket
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ReticleRotation"
    )

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(id = R.drawable.ic_menu_mylocation),
            contentDescription = "Lock",
            modifier = Modifier
                .scale(scale)
                .rotate(rotation)
        )

        Canvas(
            modifier = Modifier
                .size(48.dp)
                .scale(scale)
                .rotate(rotation)
        ) {
            val strokeWidth = 2.dp.toPx()
            val bracketLen = 10.dp.toPx()
            val color = Color(0xFF00FF41)

            // Top-Left corner
            drawLine(color, Offset(0f, 0f), Offset(bracketLen, 0f), strokeWidth)
            drawLine(color, Offset(0f, 0f), Offset(0f, bracketLen), strokeWidth)

            // Top-Right corner
            drawLine(color, Offset(size.width, 0f), Offset(size.width - bracketLen, 0f), strokeWidth)
            drawLine(color, Offset(size.width, 0f), Offset(size.width, bracketLen), strokeWidth)

            // Bottom-Left corner
            drawLine(color, Offset(0f, size.height), Offset(bracketLen, size.height), strokeWidth)
            drawLine(color, Offset(0f, size.height), Offset(0f, size.height - bracketLen), strokeWidth)

            // Bottom-Right corner
            drawLine(color, Offset(size.width, size.height), Offset(size.width - bracketLen, size.height), strokeWidth)
            drawLine(color, Offset(size.width, size.height), Offset(size.width, size.height - bracketLen), strokeWidth)
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
fun TacticalTelemetryPanelPreview() {
    TacticalTelemetryPanel(
        targetMmsi = "235009270",
        vesselName = "STENA FORERUNNER",
        vesselType = "CARGO / GENERAL",
        speed = 14.2,
        heading = 185.0,
        isFleetMember = true,
        isVisible = true,
        onToggleFleet = {},
        onOpenIntel = {},
        onDismiss = {}
    )
}

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
fun LockOnReticlePreview() {
    Box(
        modifier = Modifier
            .size(120.dp)
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        LockOnReticle()
    }
}

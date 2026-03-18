package com.example.batterymonitor.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.res.painterResource
import com.example.batterymonitor.R

@Composable
fun DynamicBatteryLogo(level: Int, modifier: Modifier = Modifier) {
    // 5% Stepped Logic: Round down to nearest 5%
    val steppedLevel = (level / 5) * 5
    val fillFraction = steppedLevel / 100f

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        // Layer 1: Background (White)
        Image(
            painter = painterResource(id = R.drawable.ic_logo_layer_bg),
            contentDescription = null,
            modifier = Modifier.fillMaxSize()
        )

        // Layer 2: Orange Fill (Clipping)
        Image(
            painter = painterResource(id = R.drawable.ic_logo_layer_fill),
            contentDescription = null,
            modifier = Modifier
                .fillMaxSize()
                .drawWithContent {
                    val fillHeight = size.height * fillFraction
                    clipRect(
                        top = size.height - fillHeight,
                        bottom = size.height
                    ) {
                        this@drawWithContent.drawContent()
                    }
                }
        )

        // Layer 3: Bolt Cutout (Card Background)
        Image(
            painter = painterResource(id = R.drawable.ic_logo_layer_bolt),
            contentDescription = null,
            modifier = Modifier.fillMaxSize()
        )

        // Layer 4: Outline (Grey)
        Image(
            painter = painterResource(id = R.drawable.ic_logo_layer_outline),
            contentDescription = null,
            modifier = Modifier.fillMaxSize()
        )
    }
}

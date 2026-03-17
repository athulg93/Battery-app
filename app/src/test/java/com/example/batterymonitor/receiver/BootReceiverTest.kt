package com.example.batterymonitor.receiver

import android.content.Context
import android.content.Intent
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class BootReceiverTest {

    private val bootReceiver = BootReceiver()

    @Test
    fun `onReceive with ACTION_BOOT_COMPLETED should start BatteryMonitorService`() {
        // Arrange
        val context = mockk<Context>(relaxed = true)
        val intent = Intent(Intent.ACTION_BOOT_COMPLETED)

        // Act
        bootReceiver.onReceive(context, intent)

        // Assert
        verify(exactly = 1) {
            context.startForegroundService(any())
        }
    }

    @Test
    fun `onReceive with random intent action should NOT start BatteryMonitorService`() {
        // Arrange
        val context = mockk<Context>(relaxed = true)
        val intent = Intent("SOME_RANDOM_ACTION")

        // Act
        bootReceiver.onReceive(context, intent)

        // Assert
        verify(exactly = 0) {
            context.startForegroundService(any())
        }
    }
}

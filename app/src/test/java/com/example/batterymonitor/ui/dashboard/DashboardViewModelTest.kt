package com.example.batterymonitor.ui.dashboard

import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.BatteryManager
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.example.batterymonitor.data.local.AppDatabase
import com.example.batterymonitor.data.local.AppUsageDao
import com.example.batterymonitor.data.local.AppUsageLog
import com.example.batterymonitor.data.local.ChargeSession
import com.example.batterymonitor.data.local.ChargeSessionDao
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class DashboardViewModelTest {

    @get:Rule
    val instantExecutorRule = InstantTaskExecutorRule()

    private val testDispatcher = StandardTestDispatcher()
    
    private lateinit var viewModel: DashboardViewModel
    private lateinit var application: Application
    private lateinit var mockDatabase: AppDatabase
    private lateinit var mockAppUsageDao: AppUsageDao
    private lateinit var mockChargeSessionDao: ChargeSessionDao

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)

        application = mockk(relaxed = true)
        mockDatabase = mockk(relaxed = true)
        mockAppUsageDao = mockk(relaxed = true)
        mockChargeSessionDao = mockk(relaxed = true)

        mockkStatic(AppDatabase::class)
        every { AppDatabase.getDatabase(any()) } returns mockDatabase
        every { mockDatabase.appUsageDao() } returns mockAppUsageDao
        every { mockDatabase.chargeSessionDao() } returns mockChargeSessionDao
        
        // Mock default database returns
        every { mockAppUsageDao.getUsageSince(any()) } returns flowOf(emptyList())
        every { mockChargeSessionDao.getAllSessions() } returns flowOf(emptyList())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `appUsage flows empty list when no database usage logs exist`() = runTest {
        viewModel = DashboardViewModel(application)
        advanceUntilIdle() // let coroutines execute
        
        val currentUsage = viewModel.appUsage.value
        assertTrue("Expected empty app usage", currentUsage.isEmpty())
    }

    @Test
    fun `calculateUsagePercentages creates correct AppUsageSummary list`() = runTest {
        // Arrange: Emulate logs representing 3 packages
        val tempLogs = listOf(
            AppUsageLog(packageName = "com.google.chrome", foregroundTimeMs = 5000, timestamp = 1000L), // Max for chrome will be 5000
            AppUsageLog(packageName = "com.google.chrome", foregroundTimeMs = 2000, timestamp = 1200L), 
            AppUsageLog(packageName = "com.android.settings", foregroundTimeMs = 15000, timestamp = 1300L), // Max for settings is 15000
            AppUsageLog(packageName = "com.example.app", foregroundTimeMs = 0, timestamp = 1400L) // Max is 0
        )
        // Set the mock to return our test flow
        every { mockAppUsageDao.getUsageSince(any()) } returns flowOf(tempLogs)

        // Act
        viewModel = DashboardViewModel(application)
        advanceUntilIdle() 
        
        // Total max foreground time = 5000 + 15000 + 0 = 20000 ms
        // Permissions for chrome: 5000 / 20000 = 25%
        // Permissions for settings: 15000 / 20000 = 75%
        // Permissions for example: 0%

        val usage = viewModel.appUsage.value

        // Assert
        assertEquals(3, usage.size)
        
        // Results should be ordered by descending percentage
        assertEquals("com.android.settings", usage[0].packageName)
        assertEquals(75.0f, usage[0].estimatedDrainPct, 0.01f)
        assertEquals(15000L, usage[0].foregroundTimeMs)

        assertEquals("com.google.chrome", usage[1].packageName)
        assertEquals(25.0f, usage[1].estimatedDrainPct, 0.01f)
        assertEquals(5000L, usage[1].foregroundTimeMs)
        
        assertEquals("com.example.app", usage[2].packageName)
        assertEquals(0.0f, usage[2].estimatedDrainPct, 0.01f)
    }

    @Test
    fun `calculateBatteryHealth penalizes deep discharges returning less than 100`() = runTest {
        val badCycleSession = ChargeSession(
            startLevel = 10,   // deep discharge penalty 0.1
            endLevel = 100,    // high charge penalty 0.05
            startTemperature = 300,
            endTemperature = 450, // high heat penalty 0.2
            startTime = 0,
            endTime = 1000,
            isComplete = true,
            startVoltage = 3800,
            endVoltage = 4200
        )
        
        every { mockChargeSessionDao.getAllSessions() } returns flowOf(listOf(badCycleSession))

        viewModel = DashboardViewModel(application)
        advanceUntilIdle()

        val health = viewModel.uiState.value.healthEstimate

        // Expect 100 - 0.1 - 0.05 - 0.2 = 99.65
        assertEquals(99.65f, health, 0.01f)
    }
}

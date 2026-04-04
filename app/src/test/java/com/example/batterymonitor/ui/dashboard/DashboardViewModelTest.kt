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
import com.example.batterymonitor.data.local.OverchargeDao
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
import androidx.test.core.app.ApplicationProvider
import io.mockk.spyk

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
    private lateinit var mockOverchargeDao: OverchargeDao

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)

        application = ApplicationProvider.getApplicationContext<Application>()
        
        mockDatabase = mockk(relaxed = true)
        mockAppUsageDao = mockk(relaxed = true)
        mockChargeSessionDao = mockk(relaxed = true)
        mockOverchargeDao = mockk(relaxed = true)

        mockkStatic(AppDatabase::class)
        every { AppDatabase.getDatabase(any()) } returns mockDatabase
        every { mockDatabase.appUsageDao() } returns mockAppUsageDao
        every { mockDatabase.chargeSessionDao() } returns mockChargeSessionDao
        every { mockDatabase.overchargeDao() } returns mockOverchargeDao
        
        // Mock default database returns
        every { mockAppUsageDao.getUsageSince(any()) } returns flowOf(emptyList())
        every { mockChargeSessionDao.getAllSessions() } returns flowOf(emptyList())
        every { mockOverchargeDao.getAllEventsFlow() } returns flowOf(emptyList())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }



    @Test
    fun `calculateBatteryHealth penalizes deep discharges based on cycles`() = runTest {
        val badCycleSession = ChargeSession(
            startLevel = 10,   // deep discharge penalty 0.02 added to factor
            endLevel = 100,    // 90% charge = 0.9 cycles
            startTemperature = 300,
            endTemperature = 460, // high heat penalty 0.05 added to factor (>45C)
            startTime = 0,
            endTime = 1000,
            isComplete = true,
            startVoltage = 3800,
            endVoltage = 4200
        )
        // Cycles = 0.9
        // PenaltyFactor = 1.0 (base) + 0.02 (deep) + 0.05 (heat) = 1.07
        // Degradation = 0.9 (cycles) * 0.05f (loss/cycle) * 1.07 (penalty) = 0.04815
        val expectedHealth = 100f - (0.9f * 0.05f * 1.07f)
        
        every { mockChargeSessionDao.getAllSessions() } returns flowOf(listOf(badCycleSession))

        viewModel = DashboardViewModel(application)
        advanceUntilIdle()

        val health = viewModel.uiState.value.healthEstimate

        assertEquals(expectedHealth, health, 0.001f)
    }
}

/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.components.metrics

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mozilla.fenix.utils.Settings
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class DefaultMetricsStorageTest {

    private val formatter = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private val calendarStart = Calendar.getInstance(Locale.US)
    private val dayMillis: Long = 1000 * 60 * 60 * 24

    private var checkDefaultBrowser = false
    private val doCheckDefaultBrowser = { checkDefaultBrowser }
    private var shouldSendGenerally = true
    private val doShouldSendGenerally = { shouldSendGenerally }
    private var installTime = 0L
    private val doGetInstallTime = { installTime }

    private val settings = mockk<Settings>()

    private val dispatcher = StandardTestDispatcher()

    private lateinit var storage: DefaultMetricsStorage

    @Before
    fun setup() {
        checkDefaultBrowser = false
        shouldSendGenerally = true
        installTime = System.currentTimeMillis()

        every { settings.firstWeekDaysOfUseGrowthData } returns setOf()
        every { settings.firstWeekDaysOfUseGrowthData = any() } returns Unit

        storage = DefaultMetricsStorage(mockk(), settings, doCheckDefaultBrowser, doShouldSendGenerally, doGetInstallTime, dispatcher)
    }

    @Test
    fun `GIVEN that events should not be generally sent WHEN event would be tracked THEN it is not`() = runTest(dispatcher) {
        shouldSendGenerally = false
        checkDefaultBrowser = true
        every { settings.setAsDefaultGrowthSent } returns false

        val result = storage.shouldTrack(Event.GrowthData.SetAsDefault)

        assertFalse(result)
    }

    @Test
    fun `GIVEN set as default has not been sent and app is not default WHEN checked for sending THEN will not be sent`() = runTest(dispatcher) {
        every { settings.setAsDefaultGrowthSent } returns false
        checkDefaultBrowser = false

        val result = storage.shouldTrack(Event.GrowthData.SetAsDefault)

        assertFalse(result)
    }

    @Test
    fun `GIVEN set as default has not been sent and app is default WHEN checked for sending THEN will be sent`() = runTest(dispatcher) {
        every { settings.setAsDefaultGrowthSent } returns false
        checkDefaultBrowser = true

        val result = storage.shouldTrack(Event.GrowthData.SetAsDefault)

        assertTrue(result)
    }

    @Test
    fun `GIVEN set as default has been sent and app is default WHEN checked for sending THEN will be not sent`() = runTest(dispatcher) {
        every { settings.setAsDefaultGrowthSent } returns true
        checkDefaultBrowser = true

        val result = storage.shouldTrack(Event.GrowthData.SetAsDefault)

        assertFalse(result)
    }

    @Test
    fun `WHEN set as default updated THEN settings will be updated accordingly`() = runTest(dispatcher) {
        val updateSlot = slot<Boolean>()
        every { settings.setAsDefaultGrowthSent = capture(updateSlot) } returns Unit

        storage.updateSentState(Event.GrowthData.SetAsDefault)

        assertTrue(updateSlot.captured)
    }

    @Test
    fun `GIVEN that it has been less than 24 hours since last resumed sent WHEN checked for sending THEN will not be sent`() = runTest(dispatcher) {
        val currentTime = System.currentTimeMillis()
        every { settings.resumeGrowthLastSent } returns currentTime

        val result = storage.shouldTrack(Event.GrowthData.FirstAppOpenForDay)

        assertFalse(result)
    }

    @Test
    fun `GIVEN that it has been more than 24 hours since last resumed sent WHEN checked for sending THEN will be sent`() = runTest(dispatcher) {
        val currentTime = System.currentTimeMillis()
        every { settings.resumeGrowthLastSent } returns currentTime - 1000 * 60 * 60 * 24 * 2

        val result = storage.shouldTrack(Event.GrowthData.FirstAppOpenForDay)

        assertTrue(result)
    }

    @Test
    fun `WHEN last resumed state updated THEN settings updated accordingly`() = runTest(dispatcher) {
        val updateSlot = slot<Long>()
        every { settings.resumeGrowthLastSent } returns 0
        every { settings.resumeGrowthLastSent = capture(updateSlot) } returns Unit

        storage.updateSentState(Event.GrowthData.FirstAppOpenForDay)

        assertTrue(updateSlot.captured > 0)
    }

    @Test
    fun `GIVEN that it has been less than 24 hours since uri load sent WHEN checked for sending THEN will not be sent`() = runTest(dispatcher) {
        val currentTime = System.currentTimeMillis()
        every { settings.uriLoadGrowthLastSent } returns currentTime

        val result = storage.shouldTrack(Event.GrowthData.FirstUriLoadForDay)

        assertFalse(result)
    }

    @Test
    fun `GIVEN that it has been more than 24 hours since uri load  sent WHEN checked for sending THEN will be sent`() = runTest(dispatcher) {
        val currentTime = System.currentTimeMillis()
        every { settings.uriLoadGrowthLastSent } returns currentTime - 1000 * 60 * 60 * 24 * 2

        val result = storage.shouldTrack(Event.GrowthData.FirstUriLoadForDay)

        assertTrue(result)
    }

    @Test
    fun `WHEN uri load updated THEN settings updated accordingly`() = runTest(dispatcher) {
        val updateSlot = slot<Long>()
        every { settings.uriLoadGrowthLastSent } returns 0
        every { settings.uriLoadGrowthLastSent = capture(updateSlot) } returns Unit

        storage.updateSentState(Event.GrowthData.FirstUriLoadForDay)

        assertTrue(updateSlot.captured > 0)
    }

    @Test
    fun `GIVEN that app has been used for less than 3 days in a row WHEN checked for first week activity THEN event will not be sent`() = runTest(dispatcher) {
        val tomorrow = calendarStart.createNextDay()
        every { settings.firstWeekDaysOfUseGrowthData = any() } returns Unit
        every { settings.firstWeekDaysOfUseGrowthData } returns setOf(calendarStart, tomorrow).toStrings()
        every { settings.firstWeekSeriesGrowthSent } returns false

        val result = storage.shouldTrack(Event.GrowthData.FirstWeekSeriesActivity)

        assertFalse(result)
    }

    @Test
    fun `GIVEN that app has only been used for 3 days in a row WHEN checked for first week activity THEN event will be sent`() = runTest(dispatcher) {
        val tomorrow = calendarStart.createNextDay()
        val thirdDay = tomorrow.createNextDay()
        every { settings.firstWeekDaysOfUseGrowthData } returns setOf(calendarStart, tomorrow, thirdDay).toStrings()
        every { settings.firstWeekSeriesGrowthSent } returns false

        val result = storage.shouldTrack(Event.GrowthData.FirstWeekSeriesActivity)

        assertTrue(result)
    }

    @Test
    fun `GIVEN that app has been used for 3 days but not consecutively WHEN checked for first week activity THEN event will be not sent`() = runTest(dispatcher) {
        val tomorrow = calendarStart.createNextDay()
        val fourDaysFromNow = tomorrow.createNextDay().createNextDay()
        every { settings.firstWeekDaysOfUseGrowthData = any() } returns Unit
        every { settings.firstWeekDaysOfUseGrowthData } returns setOf(calendarStart, tomorrow, fourDaysFromNow).toStrings()
        every { settings.firstWeekSeriesGrowthSent } returns false

        val result = storage.shouldTrack(Event.GrowthData.FirstWeekSeriesActivity)

        assertFalse(result)
    }

    @Test
    fun `GIVEN that app has been used for 3 days consecutively but not within first week WHEN checked for first week activity THEN event will be not sent`() = runTest(dispatcher) {
        val tomorrow = calendarStart.createNextDay()
        val thirdDay = tomorrow.createNextDay()
        val installTime9DaysEarlier = calendarStart.timeInMillis - (dayMillis * 9)
        every { settings.firstWeekDaysOfUseGrowthData } returns setOf(calendarStart, tomorrow, thirdDay).toStrings()
        every { settings.firstWeekSeriesGrowthSent } returns false
        installTime = installTime9DaysEarlier

        val result = storage.shouldTrack(Event.GrowthData.FirstWeekSeriesActivity)

        assertFalse(result)
    }

    @Test
    fun `GIVEN that first week activity has already been sent WHEN checked for first week activity THEN event will be not sent`() = runTest(dispatcher) {
        val tomorrow = calendarStart.createNextDay()
        val thirdDay = tomorrow.createNextDay()
        every { settings.firstWeekDaysOfUseGrowthData } returns setOf(calendarStart, tomorrow, thirdDay).toStrings()
        every { settings.firstWeekSeriesGrowthSent } returns true

        val result = storage.shouldTrack(Event.GrowthData.FirstWeekSeriesActivity)

        assertFalse(result)
    }

    @Test
    fun `GIVEN that first week activity is not sent WHEN checked to send THEN current day is added to rolling days`() = runTest(dispatcher) {
        val captureRolling = slot<Set<String>>()
        val previousDay = calendarStart.createPreviousDay()
        every { settings.firstWeekDaysOfUseGrowthData } returns setOf(previousDay).toStrings()
        every { settings.firstWeekDaysOfUseGrowthData = capture(captureRolling) } returns Unit
        every { settings.firstWeekSeriesGrowthSent } returns false

        storage.shouldTrack(Event.GrowthData.FirstWeekSeriesActivity)

        assertTrue(captureRolling.captured.contains(formatter.format(calendarStart.time)))
    }

    @Test
    fun `WHEN first week activity state updated THEN settings updated accordingly`() = runTest(dispatcher) {
        val captureSent = slot<Boolean>()
        every { settings.firstWeekSeriesGrowthSent } returns false
        every { settings.firstWeekSeriesGrowthSent = capture(captureSent) } returns Unit

        storage.updateSentState(Event.GrowthData.FirstWeekSeriesActivity)

        assertTrue(captureSent.captured)
    }

    @Test
    fun `GIVEN not yet in recording window WHEN checking to track THEN days of use still updated`() = runTest(dispatcher) {
        shouldSendGenerally = false
        val captureSlot = slot<Set<String>>()
        every { settings.firstWeekDaysOfUseGrowthData } returns setOf()
        every { settings.firstWeekDaysOfUseGrowthData = capture(captureSlot) } returns Unit

        storage.shouldTrack(Event.GrowthData.FirstWeekSeriesActivity)

        assertTrue(captureSlot.captured.isNotEmpty())
    }

    @Test
    fun `GIVEN outside first week after install WHEN checking to track THEN days of use is not updated`() = runTest(dispatcher) {
        val captureSlot = slot<Set<String>>()
        every { settings.firstWeekDaysOfUseGrowthData } returns setOf()
        every { settings.firstWeekDaysOfUseGrowthData = capture(captureSlot) } returns Unit
        installTime = calendarStart.timeInMillis - (dayMillis * 9)

        storage.shouldTrack(Event.GrowthData.FirstWeekSeriesActivity)

        assertFalse(captureSlot.isCaptured)
    }

    private fun Calendar.copy() = clone() as Calendar
    private fun Calendar.createNextDay() = copy().apply {
        add(Calendar.DAY_OF_MONTH, 1)
    }
    private fun Calendar.createPreviousDay() = copy().apply {
        add(Calendar.DAY_OF_MONTH, -1)
    }
    private fun Set<Calendar>.toStrings() = map {
        formatter.format(it.time)
    }.toSet()
}

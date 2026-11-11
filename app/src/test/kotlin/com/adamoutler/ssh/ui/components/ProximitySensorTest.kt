package com.adamoutler.ssh.ui.components

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorManager
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSensor

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class ProximitySensorTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `rememberProximitySensorState skips sensor registration when enabled is false`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val shadowSensorManager = shadowOf(sensorManager)
        val proximitySensor = ShadowSensor.newInstance(Sensor.TYPE_PROXIMITY)
        shadowSensorManager.addSensor(proximitySensor)

        var currentState = true
        composeTestRule.setContent {
            val isNear = rememberProximitySensorState(enabled = false)
            currentState = isNear.value
        }
        composeTestRule.waitForIdle()

        assertFalse("isNear must be false when pocket detection is disabled", currentState)
        assertTrue(
            "SensorManager must have no registered listeners when enabled is false",
            shadowSensorManager.listeners.isEmpty(),
        )
    }

    @Test
    fun `rememberProximitySensorState registers proximity listener when enabled is true`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val shadowSensorManager = shadowOf(sensorManager)
        val proximitySensor = ShadowSensor.newInstance(Sensor.TYPE_PROXIMITY)
        shadowSensorManager.addSensor(proximitySensor)

        composeTestRule.setContent {
            rememberProximitySensorState(enabled = true)
        }
        composeTestRule.waitForIdle()

        assertFalse(
            "SensorManager must register a listener when enabled is true",
            shadowSensorManager.listeners.isEmpty(),
        )
    }
}

package com.adamoutler.ssh.vm

import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AvfCapabilityDetectorTest {

    private lateinit var baseContext: Context

    @Before
    fun setUp() {
        baseContext = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun testSdkLessThan34ReturnsApiLevelUnsupported() {
        val status33 = AvfCapabilityDetector.getAvfStatus(baseContext, sdkInt = 33)
        assertEquals(AvfStatus.API_LEVEL_UNSUPPORTED, status33)

        val statusTopLevel = getAvfStatus(baseContext, sdkInt = 30)
        assertEquals(AvfStatus.API_LEVEL_UNSUPPORTED, statusTopLevel)
    }

    @Test
    fun testServiceNullReturnsHypervisorUnsupported() {
        val context = object : ContextWrapper(baseContext) {
            override fun getSystemService(name: String): Any? = null
        }
        val status = AvfCapabilityDetector.getAvfStatus(context, sdkInt = 34)
        assertEquals(AvfStatus.HYPERVISOR_UNSUPPORTED, status)
    }

    @Test
    fun testServiceThrowsReturnsHypervisorUnsupported() {
        val context = object : ContextWrapper(baseContext) {
            override fun getSystemService(name: String): Any? {
                throw SecurityException("Access denied to virtual_machine")
            }
        }
        val status = AvfCapabilityDetector.getAvfStatus(context, sdkInt = 34)
        assertEquals(AvfStatus.HYPERVISOR_UNSUPPORTED, status)
    }

    @Test
    fun testServicePresentPermissionDeniedReturnsPermissionRequired() {
        val fakeVmService = Any()
        val context = object : ContextWrapper(baseContext) {
            override fun getSystemService(name: String): Any? =
                if (name == AvfCapabilityDetector.VIRTUAL_MACHINE_SERVICE) fakeVmService else super.getSystemService(name)

            override fun checkPermission(permission: String, pid: Int, uid: Int): Int =
                PackageManager.PERMISSION_DENIED

            override fun checkSelfPermission(permission: String): Int =
                PackageManager.PERMISSION_DENIED
        }

        val status = AvfCapabilityDetector.getAvfStatus(context, sdkInt = 34)
        assertEquals(AvfStatus.PERMISSION_REQUIRED, status)
    }

    @Test
    fun testPermissionCheckThrowsReturnsPermissionRequired() {
        val fakeVmService = Any()
        val context = object : ContextWrapper(baseContext) {
            override fun getSystemService(name: String): Any? =
                if (name == AvfCapabilityDetector.VIRTUAL_MACHINE_SERVICE) fakeVmService else super.getSystemService(name)

            override fun checkSelfPermission(permission: String): Int {
                throw SecurityException("Security check failed")
            }
        }

        val status = AvfCapabilityDetector.getAvfStatus(context, sdkInt = 34)
        assertEquals(AvfStatus.PERMISSION_REQUIRED, status)
    }

    @Test
    fun testServicePresentPermissionGrantedReturnsAvailableAndPermitted() {
        val fakeVmService = Any()
        val context = object : ContextWrapper(baseContext) {
            override fun getSystemService(name: String): Any? =
                if (name == AvfCapabilityDetector.VIRTUAL_MACHINE_SERVICE) fakeVmService else super.getSystemService(name)

            override fun checkSelfPermission(permission: String): Int =
                if (permission == AvfCapabilityDetector.MANAGE_VM_PERMISSION) {
                    PackageManager.PERMISSION_GRANTED
                } else {
                    PackageManager.PERMISSION_DENIED
                }
        }

        val status = AvfCapabilityDetector.getAvfStatus(context, sdkInt = 34)
        assertEquals(AvfStatus.AVAILABLE_AND_PERMITTED, status)

        val statusTopLevel = getAvfStatus(context, sdkInt = 35)
        assertEquals(AvfStatus.AVAILABLE_AND_PERMITTED, statusTopLevel)
    }
}

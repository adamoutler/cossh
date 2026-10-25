package com.adamoutler.ssh.vm

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build

enum class AvfStatus {
    AVAILABLE_AND_PERMITTED,
    PERMISSION_REQUIRED,
    HYPERVISOR_UNSUPPORTED,
    API_LEVEL_UNSUPPORTED,
}

object AvfCapabilityDetector {
    const val VIRTUAL_MACHINE_SERVICE = "virtual_machine"
    const val MANAGE_VM_PERMISSION = "android.permission.MANAGE_VIRTUAL_MACHINE"

    fun getAvfStatus(context: Context, sdkInt: Int = Build.VERSION.SDK_INT): AvfStatus {
        if (sdkInt < 34) {
            return AvfStatus.API_LEVEL_UNSUPPORTED
        }

        val vmService = try {
            context.getSystemService(VIRTUAL_MACHINE_SERVICE)
        } catch (_: Throwable) {
            null
        }

        if (vmService == null) {
            return AvfStatus.HYPERVISOR_UNSUPPORTED
        }

        val permissionGranted = try {
            context.checkSelfPermission(MANAGE_VM_PERMISSION) == PackageManager.PERMISSION_GRANTED
        } catch (_: Throwable) {
            false
        }

        return if (permissionGranted) {
            AvfStatus.AVAILABLE_AND_PERMITTED
        } else {
            AvfStatus.PERMISSION_REQUIRED
        }
    }
}

fun getAvfStatus(context: Context, sdkInt: Int = Build.VERSION.SDK_INT): AvfStatus =
    AvfCapabilityDetector.getAvfStatus(context, sdkInt)

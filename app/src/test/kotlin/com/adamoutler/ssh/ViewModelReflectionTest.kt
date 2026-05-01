package com.adamoutler.ssh

import android.app.Application
import com.adamoutler.ssh.ui.screens.ConnectionListViewModel
import org.junit.Test

class ViewModelReflectionTest {
    @Test
    fun testReflection() {
        val clazz = ConnectionListViewModel::class.java
        clazz.getConstructor(Application::class.java)
    }
}

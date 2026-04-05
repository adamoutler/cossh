package com.adamoutler.ssh

import android.app.Application
import org.junit.Test
import com.adamoutler.ssh.ui.screens.ConnectionListViewModel

class ViewModelReflectionTest {
    @Test
    fun testReflection() {
        val clazz = ConnectionListViewModel::class.java
        clazz.getConstructor(Application::class.java)
    }
}

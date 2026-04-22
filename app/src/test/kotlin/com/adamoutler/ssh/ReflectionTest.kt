package com.adamoutler.ssh

import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalEmulator
import org.junit.Test

class ReflectionTest {
    @Test
    fun dumpMethods() {
        val f = java.io.File("app/reflect-methods.txt")
        f.printWriter().use { out ->
            out.println("TerminalSession:")
            TerminalSession::class.java.methods.forEach { out.println(it.name) }
            out.println("TerminalEmulator:")
            TerminalEmulator::class.java.methods.forEach { out.println(it.name) }
        }
    }
}

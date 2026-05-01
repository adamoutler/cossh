package com.adamoutler.ssh

import com.termux.terminal.TerminalEmulator
import com.termux.terminal.TerminalSession

class ReflectionTest {
    fun printMethods() {
        println("TerminalSession methods:")
        TerminalSession::class.java.methods.forEach { println(it.name) }
        println("TerminalEmulator methods:")
        TerminalEmulator::class.java.methods.forEach { println(it.name) }
    }
}

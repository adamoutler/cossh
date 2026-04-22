package com.adamoutler.ssh

import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalEmulator

class ReflectionTest {
    fun printMethods() {
        println("TerminalSession methods:")
        TerminalSession::class.java.methods.forEach { println(it.name) }
        println("TerminalEmulator methods:")
        TerminalEmulator::class.java.methods.forEach { println(it.name) }
    }
}

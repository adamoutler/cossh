package com.adamoutler.ssh

import org.junit.Test
import com.termux.view.TerminalViewClient
import java.io.File

class ReflectionTest {
    @Test
    fun testMethods() {
        val sb = java.lang.StringBuilder()
        sb.append("TerminalViewClient Methods:\n")
        TerminalViewClient::class.java.methods.forEach { 
            sb.append(it.name).append(" | ").append(it.toGenericString()).append("\n")
        }
        File("reflect-methods.txt").writeText(sb.toString())
    }
}

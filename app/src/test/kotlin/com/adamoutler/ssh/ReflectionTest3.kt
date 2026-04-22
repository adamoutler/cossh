package com.adamoutler.ssh

import com.termux.terminal.TerminalSessionClient
import org.junit.Test

class ReflectionTest3 {
    @Test
    fun dumpMethods() {
        val f = java.io.File("../session_client_methods.txt")
        f.printWriter().use { out ->
            out.println("TerminalSessionClient:")
            TerminalSessionClient::class.java.methods.forEach { out.println(it.name) }
        }
    }
}

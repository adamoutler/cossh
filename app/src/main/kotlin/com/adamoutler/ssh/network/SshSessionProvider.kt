package com.adamoutler.ssh.network

import java.io.OutputStream

object SshSessionProvider {
    var ptyOutputStream: OutputStream? = null
    var onOutputReceived: ((ByteArray, Int) -> Unit)? = null
}

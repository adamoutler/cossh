package com.adamoutler.ssh.network

import java.io.ByteArrayOutputStream

/**
 * Implements RFC 1073 (Telnet Window Size Option - NAWS) and RFC 854 (IAC Escaping).
 *
 * Subnegotiation Structure:
 * IAC SB NAWS <16-bit width high> <16-bit width low> <16-bit height high> <16-bit height low> IAC SE
 *
 * Security Invariant:
 * Any occurrence of byte 255 (0xFF) within the payload must be doubled (0xFF 0xFF)
 * per RFC 854 to prevent terminal option injection and state machine desynchronization.
 */
object TelnetNawsProtocol {
    const val OPTION_NAWS: Int = 31
    const val IAC: Int = 255
    const val DONT: Int = 254
    const val DO: Int = 253
    const val WONT: Int = 252
    const val WILL: Int = 251
    const val SB: Int = 250
    const val SE: Int = 240

    const val DEFAULT_COLS = 80
    const val DEFAULT_ROWS = 24
    const val MIN_DIMENSION = 1
    const val MAX_DIMENSION = 65535

    /**
     * Clamps terminal dimensions to RFC 1073 16-bit bounds [1..65535].
     */
    fun sanitizeDimensions(cols: Int, rows: Int): Pair<Int, Int> {
        val sanitizedCols = if (cols <= 0) DEFAULT_COLS else cols.coerceIn(MIN_DIMENSION, MAX_DIMENSION)
        val sanitizedRows = if (rows <= 0) DEFAULT_ROWS else rows.coerceIn(MIN_DIMENSION, MAX_DIMENSION)
        return Pair(sanitizedCols, sanitizedRows)
    }

    /**
     * Builds the unescaped subnegotiation integer payload passed to Commons Net [TelnetClient.sendSubnegotiation].
     * Commons Net automatically handles RFC 854 IAC escaping when writing the subnegotiation stream.
     */
    fun buildSubnegotiationPayload(cols: Int, rows: Int): IntArray {
        val (c, r) = sanitizeDimensions(cols, rows)
        return intArrayOf(
            OPTION_NAWS,
            (c ushr 8) and 0xFF,
            c and 0xFF,
            (r ushr 8) and 0xFF,
            r and 0xFF,
        )
    }

    /**
     * Builds the raw RFC 854 / RFC 1073 wire frame, explicitly doubling any 0xFF byte
     * within the subnegotiation data:
     * `IAC SB NAWS <escaped width> <escaped height> IAC SE`
     */
    fun buildWireFrame(cols: Int, rows: Int): ByteArray {
        val (c, r) = sanitizeDimensions(cols, rows)
        val out = ByteArrayOutputStream()

        out.write(IAC)
        out.write(SB)
        out.write(OPTION_NAWS)

        fun writeEscapedByte(b: Int) {
            val ubyte = b and 0xFF
            out.write(ubyte)
            if (ubyte == IAC) {
                // RFC 854: Double the IAC byte
                out.write(IAC)
            }
        }

        writeEscapedByte((c ushr 8) and 0xFF)
        writeEscapedByte(c and 0xFF)
        writeEscapedByte((r ushr 8) and 0xFF)
        writeEscapedByte(r and 0xFF)

        out.write(IAC)
        out.write(SE)

        return out.toByteArray()
    }

    /**
     * RFC 1143 Q-Method state tracker for local option negotiation (WILL NAWS).
     * Prevents infinite negotiation loops when receiving duplicate `DO NAWS` or `DONT NAWS`.
     */
    class NawsNegotiationStateMachine {
        enum class State {
            NO,
            YES,
            WANT_NO,
            WANT_YES,
        }

        var state: State = State.NO
            private set

        /**
         * Invoked when remote peer sends `DO NAWS`.
         * Returns true if a `WILL NAWS` response must be sent; false if suppressed as a duplicate.
         */
        @Synchronized
        fun onReceiveDo(): Boolean = when (state) {
            State.NO -> {
                state = State.YES
                true
            }
            State.YES -> false // Already negotiated; ignore duplicate to prevent loop
            State.WANT_NO -> {
                state = State.NO
                false
            }
            State.WANT_YES -> {
                state = State.YES
                false
            }
        }

        /**
         * Invoked when remote peer sends `DONT NAWS`.
         * Returns true if a `WONT NAWS` response must be sent; false if suppressed.
         */
        @Synchronized
        fun onReceiveDont(): Boolean = when (state) {
            State.YES -> {
                state = State.NO
                true
            }
            State.NO -> false // Already disabled; ignore duplicate
            State.WANT_YES -> {
                state = State.NO
                false
            }
            State.WANT_NO -> {
                state = State.NO
                false
            }
        }

        val isNegotiated: Boolean
            @Synchronized get() = state == State.YES

        @Synchronized
        fun reset() {
            state = State.NO
        }
    }
}

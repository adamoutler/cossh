package com.adamoutler.ssh.data

import kotlinx.serialization.Serializable

@Serializable
data class CommandSnippet(
    val id: String,
    val name: String,
    val command: String,
    val autoSend: Boolean,
    val requireAuth: Boolean,
)

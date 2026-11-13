package com.omp.chat.data

import java.util.UUID

data class TerminalTab(
    val id: String = UUID.randomUUID().toString(),
    val title: String = "Terminal",
    val pcId: String = "",
    val sessionId: String? = null,
    val wsUrl: String? = null
)
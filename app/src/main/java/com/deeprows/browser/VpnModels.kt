package com.deeprows.browser

data class VpnCountry(
    val code: String,
    val name: String,
    val available: Boolean
)

data class VpnServer(
    val id: String,
    val countryCode: String,
    val city: String,
    val hostname: String,
    val available: Boolean = true
)

data class VpnSession(
    val serverId: String,
    val expiresAtEpochSeconds: Long,
    val wireGuardConfig: String
)

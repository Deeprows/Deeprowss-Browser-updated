package com.deeprows.browser

/**
 * Contract for the production Deeprows VPN control plane.
 * Implement this against your HTTPS backend; never ship server private keys in the APK.
 */
interface DeeprowsVpnApi {
    suspend fun listServers(): List<VpnServer>
    suspend fun createSession(serverId: String): VpnSession
    suspend fun revokeSession(serverId: String)
}

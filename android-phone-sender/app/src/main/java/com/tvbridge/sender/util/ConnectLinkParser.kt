package com.tvbridge.sender.util

import android.net.Uri

/**
 * Parsea enlaces del QR de la TV o URLs http del servidor.
 */
object ConnectLinkParser {

    fun parseServerIp(raw: String): String? {
        val trimmed = raw.trim()
        if (trimmed.isBlank()) return null

        if (trimmed.startsWith("tvbridge://", ignoreCase = true)) {
            val uri = Uri.parse(trimmed)
            val server = uri.getQueryParameter("server")?.trim()
            if (!server.isNullOrBlank()) {
                return stripSchemeAndPort(server)
            }
            val host = uri.host?.trim()
            if (!host.isNullOrBlank() && host != "connect") {
                return stripSchemeAndPort(host)
            }
        }

        if (trimmed.startsWith("http://", ignoreCase = true) ||
            trimmed.startsWith("https://", ignoreCase = true)
        ) {
            val uri = Uri.parse(trimmed)
            val host = uri.host?.trim()
            if (!host.isNullOrBlank()) {
                return host
            }
        }

        return stripSchemeAndPort(trimmed)
    }

    fun buildDeepLink(serverIp: String): String {
        val host = stripSchemeAndPort(serverIp)
        return "tvbridge://connect?server=$host"
    }

    private fun stripSchemeAndPort(value: String): String {
        return value
            .removePrefix("http://")
            .removePrefix("https://")
            .substringBefore("/")
            .substringBefore(":")
            .trim()
    }
}

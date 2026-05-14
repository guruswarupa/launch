package com.guruswarupa.launch.managers

import android.net.Uri
import android.webkit.WebResourceResponse
import java.io.ByteArrayInputStream
import java.util.Locale

object WebAppAdBlocker {
    private val blockedHostSuffixes = listOf(
        "doubleclick.net",
        "googlesyndication.com",
        "googleadservices.com",
        "pagead2.googlesyndication.com",
        "googletagmanager.com",
        "googletagservices.com",
        "adservice.google.com",
        "adservice.google.co",
        "adserver.google.com",
        "adnxs.com",
        "pubmatic.com",
        "rubiconproject.com",
        "openx.net",
        "serving-sys.com",
        "adsafeprotected.com",
        "moatads.com",
        "media.net",
        "adroll.com",
        "adform.net",
        "yieldmo.com",
        "taboola.com",
        "outbrain.com",
        "popads.net",
        "propellerads.com",
        "adcash.com",
        "clickadu.com"
    )

    private val blockedHostSegments = setOf(
        "ads",
        "adservice",
        "adservices",
        "adserver",
        "appnexus",
        "adnxs",
        "adform",
        "adroll",
        "adlog",
        "doubleclick",
        "googlesyndication",
        "googleadservices",
        "googletagmanager",
        "googletagservices",
        "moatads",
        "banner",
        "advertising",
        "publisher",
        "telemetry",
        "analytics",
        "tracking"
    )

    fun shouldBlock(uri: Uri?): Boolean {
        val host = uri?.host?.lowercase(Locale.ROOT).orEmpty()
        if (host.isBlank()) return false

        if (blockedHostSuffixes.any { host.endsWith(it) }) return true

        val segments = host.split('.')
        if (segments.any { it in blockedHostSegments }) return true

        val path = uri?.path?.lowercase(Locale.ROOT).orEmpty()
        if (path.contains("/ads/") || path.contains("/adserver/") || path.contains("/adstream/")) return true

        return false
    }

    fun createEmptyResponse(): WebResourceResponse {
        return WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(ByteArray(0)))
    }
}

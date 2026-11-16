package com.metrolist.music.providers

import java.net.URI
import java.util.Base64

object DirectVideoSource {
    private const val PREFIX = "video:"

    fun isVideo(mediaId: String?) = mediaId?.startsWith(PREFIX) == true

    fun mediaId(uri: String): String {
        require(uri == uri.trim() && isSupported(uri)) { "Use a direct HTTPS video link or a selected local video" }
        return PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(uri.toByteArray(Charsets.UTF_8))
    }

    fun uri(mediaId: String): String {
        require(isVideo(mediaId))
        val uri = String(Base64.getUrlDecoder().decode(mediaId.removePrefix(PREFIX)), Charsets.UTF_8)
        require(isSupported(uri))
        return uri
    }

    fun isSupported(value: String): Boolean = runCatching {
        val uri = URI(value.trim())
        when (uri.scheme?.lowercase()) {
            "https" -> !uri.host.isNullOrBlank() && uri.userInfo == null &&
                uri.host.lowercase() !in setOf("youtube.com", "www.youtube.com", "m.youtube.com", "youtu.be", "music.youtube.com") &&
                !uri.path.orEmpty().endsWith(".m3u8", ignoreCase = true) &&
                !uri.path.orEmpty().endsWith(".mpd", ignoreCase = true)
            "content" -> !uri.authority.isNullOrBlank()
            else -> false
        }
    }.getOrDefault(false)
}

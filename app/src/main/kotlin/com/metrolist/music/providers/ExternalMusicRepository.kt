package com.metrolist.music.providers

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import java.io.IOException
import java.net.URI

data class ExternalTrack(
    val id: String,
    val title: String,
    val artist: String,
    val duration: Int,
    val artwork: String?,
)

class JamendoClientIdRequired : IOException()

class ExternalMusicRepository(
    private val client: HttpClient = HttpClient(OkHttp) {
        expectSuccess = true
        install(HttpTimeout) {
            requestTimeoutMillis = 15_000
            connectTimeoutMillis = 10_000
        }
    },
) : AutoCloseable {
    suspend fun search(source: String, query: String, jamendoClientId: String): List<ExternalTrack> {
        if (query.isBlank()) return emptyList()
        return when (source) {
            HEARTHIS -> {
                val response = client.get("https://api-v2.hearthis.at/search/") {
                    parameter("t", query)
                    parameter("count", 30)
                    parameter("page", 1)
                }
                Json.parseToJsonElement(response.bodyAsText()).let { it as JsonArray }
                    .mapNotNull { parseHearThis(it.jsonObject) }
            }
            JAMENDO -> {
                val results = jamendo(jamendoClientId, query = query)
                val tracks = results.mapNotNull { parseJamendo(it.jsonObject) }
                if (results.isNotEmpty() && tracks.isEmpty()) {
                    throw IOException("Jamendo returned tracks without compatible audio URLs")
                }
                tracks
            }
            else -> error("Unknown music source")
        }.distinctBy { it.id }
    }

    suspend fun resolveStream(mediaId: String, jamendoClientId: String): String {
        if (DirectVideoSource.isVideo(mediaId)) return DirectVideoSource.uri(mediaId)
        val source = mediaId.substringBefore(':')
        val trackId = mediaId.substringAfter(':', "")
        val url = when (source) {
            HEARTHIS -> {
                require(trackId.matches(Regex("[A-Za-z0-9_-]+/[A-Za-z0-9_-]+")))
                val track = Json.parseToJsonElement(
                    client.get("https://api-v2.hearthis.at/$trackId/").bodyAsText(),
                ).jsonObject
                if (track.isRestricted()) {
                    throw IOException("Track is not publicly playable")
                }
                track.text("stream_url")
            }
            JAMENDO -> {
                require(trackId.matches(Regex("[0-9]+")))
                jamendo(jamendoClientId, id = trackId).firstOrNull()?.jsonObject?.text("audio")
            }
            else -> error("Unknown music source")
        }
        return url?.takeIf { isProviderUrl(it, source) }
            ?: throw IOException("No public audio stream available")
    }

    private suspend fun jamendo(clientId: String, query: String? = null, id: String? = null): JsonArray {
        val results = fetchJamendo(clientId, query, id)
        if (results.isNotEmpty()) return results
        // The public API intermittently returns an empty success for available tracks.
        delay(350)
        return fetchJamendo(clientId, query, id)
    }

    private suspend fun fetchJamendo(clientId: String, query: String?, id: String?): JsonArray {
        if (clientId.isBlank()) throw JamendoClientIdRequired()
        val response = client.get("https://api.jamendo.com/v3.0/tracks/") {
            parameter("client_id", clientId.trim())
            parameter("format", "json")
            parameter("audioformat", "mp32")
            parameter("limit", 30)
            query?.let { parameter("search", it) }
            id?.let { parameter("id", it) }
        }
        val result = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        if (result["headers"]?.jsonObject?.text("status") != "success") {
            throw IOException("Jamendo request failed")
        }
        return result["results"] as? JsonArray ?: throw IOException("Missing Jamendo results")
    }

    override fun close() = client.close()

    companion object {
        const val HEARTHIS = "hearthis"
        const val JAMENDO = "jamendo"
        const val DEFAULT_JAMENDO_CLIENT_ID = "4e997d93"

        fun isExternal(mediaId: String) = DirectVideoSource.isVideo(mediaId) || mediaId.startsWith("$HEARTHIS:") || mediaId.startsWith("$JAMENDO:")

        fun shareUrl(mediaId: String): String = when {
            DirectVideoSource.isVideo(mediaId) -> DirectVideoSource.uri(mediaId)
            mediaId.startsWith("$HEARTHIS:") -> "https://hearthis.at/${mediaId.substringAfter(':')}/"
            mediaId.startsWith("$JAMENDO:") -> "https://www.jamendo.com/track/${mediaId.substringAfter(':')}"
            else -> "https://music.youtube.com/watch?v=$mediaId"
        }

        internal fun isProviderUrl(value: String, source: String): Boolean = runCatching {
            val uri = URI(value)
            val host = uri.host?.lowercase().orEmpty()
            val domains = when (source) {
                HEARTHIS -> listOf("hearthis.at", "hearthis.app")
                JAMENDO -> listOf("jamendo.com")
                else -> emptyList()
            }
            uri.scheme == "https" && uri.userInfo == null && (uri.port == -1 || uri.port == 443) &&
                domains.any { host == it || host.endsWith(".$it") }
        }.getOrDefault(false)

        internal fun parseHearThis(track: JsonObject): ExternalTrack? {
            if (track.isRestricted()) return null
            if (!isProviderUrl(track.text("stream_url").orEmpty(), HEARTHIS)) return null
            val uri = runCatching { URI(track.text("uri").orEmpty()) }.getOrNull() ?: return null
            val path = uri.path.orEmpty().trim('/')
            if (uri.host != "api-v2.hearthis.at" || !path.matches(Regex("[A-Za-z0-9_-]+/[A-Za-z0-9_-]+"))) return null
            return ExternalTrack(
                id = "$HEARTHIS:$path",
                title = track.text("title")?.takeIf { it.isNotBlank() } ?: return null,
                artist = (track["user"] as? JsonObject)?.text("username").orEmpty(),
                duration = track.text("duration")?.toIntOrNull() ?: -1,
                artwork = track.text("artwork_url"),
            )
        }

        internal fun parseJamendo(track: JsonObject): ExternalTrack? {
            val id = track.text("id")?.takeIf { it.matches(Regex("[0-9]+")) } ?: return null
            if (!isProviderUrl(track.text("audio").orEmpty(), JAMENDO)) return null
            return ExternalTrack(
                id = "$JAMENDO:$id",
                title = track.text("name")?.takeIf { it.isNotBlank() } ?: return null,
                artist = track.text("artist_name").orEmpty(),
                duration = track.text("duration")?.toIntOrNull() ?: -1,
                artwork = track.text("image"),
            )
        }

        private fun JsonObject.isRestricted() = listOf("private", "fan_exclusive_play")
            .any { text(it) in listOf("1", "true") }

        private fun JsonObject.text(key: String): String? = (this[key] as? JsonPrimitive)?.content
            ?.takeUnless { it == "null" }
    }
}

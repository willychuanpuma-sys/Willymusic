package com.metrolist.music.providers

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.net.URI
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpSend
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.plugin
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonArray

class ExternalMusicLiveTest {
    @Test fun `Jamendo piano search and fresh stream resolve with the configured app ID`() = runBlocking {
        val clientId = System.getenv("JAMENDO_CLIENT_ID").orEmpty()
        assumeTrue("Live API check only runs when explicitly configured in CI", clientId.isNotBlank())
        val client = HttpClient(OkHttp) {
            expectSuccess = true
            install(HttpTimeout) { requestTimeoutMillis = 15_000 }
        }
        client.plugin(HttpSend).intercept { request ->
            println("Jamendo request parameters: " + request.url.parameters.entries().filter { it.key != "client_id" })
            execute(request)
        }
        ExternalMusicRepository(client).use { repository ->
            val tracks = repository.search("jamendo", "piano", clientId)
            println("Parsed Jamendo tracks: " + tracks.size)
            if (tracks.isEmpty()) {
                for (limit in listOf(2, 30)) {
                    val url = "https://api.jamendo.com/v3.0/tracks/?client_id=" + clientId +
                        "&format=json&audioformat=mp32&limit=" + limit + "&search=piano"
                    val result = Json.parseToJsonElement(URI(url).toURL().readText()).jsonObject
                    println("Direct Java API results for limit " + limit + ": " + result["results"]?.jsonArray?.size)
                }
            }
            assertTrue("The real response must yield playable tracks rather than empty parsed results", tracks.isNotEmpty())
            val stream = repository.resolveStream(tracks.first().id, clientId)
            assertEquals("https", URI(stream).scheme)
            assertTrue(ExternalMusicRepository.isProviderUrl(stream, "jamendo"))
        }
    }
}

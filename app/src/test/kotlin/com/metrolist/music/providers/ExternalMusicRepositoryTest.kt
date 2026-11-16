package com.metrolist.music.providers

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class ExternalMusicRepositoryTest {
    private val hearThis = """{
        "id":"42", "uri":"https://api-v2.hearthis.at/artist/my-song/",
        "title":"My song", "duration":"185", "user":{"username":"Artist"},
        "stream_url":"https://hearthis.app/artist/my-song/listen/?s=fresh",
        "private":"0", "fan_exclusive_play":0
    }"""

    @Test fun `HearThis uses a stable provider ID and parses numeric strings`() {
        val track = ExternalMusicRepository.parseHearThis(Json.parseToJsonElement(hearThis).jsonObject)!!
        assertEquals("hearthis:artist/my-song", track.id)
        assertEquals(185, track.duration)
        assertEquals("Artist", track.artist)
    }

    @Test fun `private and fan only tracks are excluded`() {
        for (restricted in listOf(
            hearThis.replace("\"private\":\"0\"", "\"private\":\"1\""),
            hearThis.replace("\"fan_exclusive_play\":0", "\"fan_exclusive_play\":true"),
        )) {
            assertNull(ExternalMusicRepository.parseHearThis(Json.parseToJsonElement(restricted).jsonObject))
        }
    }

    @Test fun `stream URLs reject unrelated hosts credentials and cleartext`() {
        assertTrue(ExternalMusicRepository.isProviderUrl("https://mp3l.jamendo.com/audio", "jamendo"))
        for (url in listOf("http://hearthis.at/audio", "https://hearthis.at.evil.test/audio", "https://user@hearthis.at/audio")) {
            assertFalse(ExternalMusicRepository.isProviderUrl(url, "hearthis"))
        }
    }

    @Test fun `search escapes query parameters and playback fetches a fresh stream`() = runBlocking {
        var requests = 0
        val engine = MockEngine { request ->
            requests++
            if (requests == 1) {
                assertEquals("AC/DC & soul", request.url.parameters["t"])
                respond("[$hearThis]", headers = headersOf("Content-Type", "application/json"))
            } else {
                assertEquals("/artist/my-song/", request.url.encodedPath)
                respond(hearThis, headers = headersOf("Content-Type", "application/json"))
            }
        }
        ExternalMusicRepository(HttpClient(engine)).use { repository ->
            val track = repository.search("hearthis", "AC/DC & soul", "").single()
            assertEquals("https://hearthis.app/artist/my-song/listen/?s=fresh", repository.resolveStream(track.id, ""))
        }
        assertEquals(2, requests)
    }

    @Test fun `Jamendo refuses a missing client ID before making a request`() = runBlocking {
        ExternalMusicRepository(HttpClient(MockEngine { error("Must not request without client ID") })).use { repository ->
            try {
                repository.search("jamendo", "song", "")
                fail("Expected Client ID requirement")
            } catch (_: JamendoClientIdRequired) { }
        }
    }

    @Test fun `Jamendo selects audio rather than a download URL`() = runBlocking {
        val engine = MockEngine { request ->
            assertEquals("own-app-id", request.url.parameters["client_id"])
            respond("""{"headers":{"status":"success"},"results":[{
                "id":"72","name":"Song","artist_name":"Artist","duration":120,
                "audio":"https://prod.jamendo.com/stream.mp3",
                "audiodownload":"https://prod.jamendo.com/download.mp3","audiodownload_allowed":false
            }]}""", headers = headersOf("Content-Type", "application/json"))
        }
        ExternalMusicRepository(HttpClient(engine)).use { repository ->
            val track = repository.search("jamendo", "Song", "own-app-id").single()
            assertEquals("jamendo:72", track.id)
            assertEquals("https://prod.jamendo.com/stream.mp3", repository.resolveStream(track.id, "own-app-id"))
        }
    }

    @Test fun `Jamendo API failure is not presented as empty search results`() = runBlocking {
        val engine = MockEngine {
            respond("""{"headers":{"status":"failed","code":5},"results":[]}""",
                headers = headersOf("Content-Type", "application/json"))
        }
        ExternalMusicRepository(HttpClient(engine)).use { repository ->
            try {
                repository.search("jamendo", "Song", "invalid")
                fail("Expected an API error")
            } catch (_: IOException) { }
        }
    }

    @Test fun `incompatible Jamendo audio is reported as an error rather than no results`() = runBlocking {
        val engine = MockEngine {
            respond("""{"headers":{"status":"success"},"results":[{"id":"72","name":"Song","audio":"https://unrelated.example/audio"}]}""",
                headers = headersOf("Content-Type", "application/json"))
        }
        ExternalMusicRepository(HttpClient(engine)).use { repository ->
            try {
                repository.search("jamendo", "Song", "own-app-id")
                fail("Expected unsupported audio error")
            } catch (_: IOException) { }
        }
    }

    @Test fun `one empty Jamendo success is retried before returning results`() = runBlocking {
        var requests = 0
        val engine = MockEngine {
            requests++
            val results = if (requests == 1) "[]" else """[{"id":"72","name":"Song","audio":"https://prod.jamendo.com/stream.mp3"}]"""
            respond("""{"headers":{"status":"success"},"results":$results}""",
                headers = headersOf("Content-Type", "application/json"))
        }
        ExternalMusicRepository(HttpClient(engine)).use { repository ->
            assertEquals("jamendo:72", repository.search("jamendo", "Song", "own-app-id").single().id)
        }
        assertEquals(2, requests)
    }

    @Test fun `a genuinely empty Jamendo search stops after one retry`() = runBlocking {
        var requests = 0
        val engine = MockEngine {
            requests++
            respond("""{"headers":{"status":"success"},"results":[]}""",
                headers = headersOf("Content-Type", "application/json"))
        }
        ExternalMusicRepository(HttpClient(engine)).use { repository ->
            assertTrue(repository.search("jamendo", "unknown query", "own-app-id").isEmpty())
        }
        assertEquals(2, requests)
    }

    @Test fun `malformed HearThis paths cannot change the API request target`() = runBlocking {
        ExternalMusicRepository(HttpClient(MockEngine { error("Must not request invalid path") })).use { repository ->
            try {
                repository.resolveStream("hearthis:../search", "")
                fail("Expected invalid path rejection")
            } catch (_: IllegalArgumentException) { }
        }
    }
}

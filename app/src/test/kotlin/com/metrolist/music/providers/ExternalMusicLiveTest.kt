package com.metrolist.music.providers

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.net.URI

class ExternalMusicLiveTest {
    @Test fun `Jamendo piano search and fresh stream resolve with the configured app ID`() = runBlocking {
        val clientId = System.getenv("JAMENDO_CLIENT_ID").orEmpty()
        assumeTrue("Live API check only runs when explicitly configured in CI", clientId.isNotBlank())
        ExternalMusicRepository().use { repository ->
            val tracks = repository.search("jamendo", "piano", clientId)
            assertTrue("The real response must yield playable tracks rather than empty parsed results", tracks.isNotEmpty())
            val stream = repository.resolveStream(tracks.first().id, clientId)
            assertEquals("https", URI(stream).scheme)
            assertTrue(ExternalMusicRepository.isProviderUrl(stream, "jamendo"))
        }
    }
}

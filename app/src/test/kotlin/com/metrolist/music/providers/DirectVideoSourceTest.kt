package com.metrolist.music.providers

import org.junit.Assert.*
import org.junit.Test

class DirectVideoSourceTest {
    @Test fun `video identifiers preserve URLs and provider identity`() {
        val uri = "https://media.example.org/clip.mp4?token=abc%2Fxyz"
        val id = DirectVideoSource.mediaId(uri)
        assertTrue(DirectVideoSource.isVideo(id))
        assertTrue(ExternalMusicRepository.isExternal(id))
        assertEquals(uri, DirectVideoSource.uri(id))
    }
    @Test fun `selected document URI survives queue persistence`() {
        val uri = "content://com.android.providers.media.documents/document/video%3A42"
        assertEquals(uri, DirectVideoSource.uri(DirectVideoSource.mediaId(uri)))
    }
    @Test fun `unsafe or unsupported video sources are rejected`() {
        for (uri in listOf("http://example.org/video.mp4", "file:///sdcard/private.mp4",
            "https://user:password@example.org/video.mp4", "https://youtu.be/123",
            "https://www.youtube.com/watch?v=123", "https://example.org/list.m3u8", "https://example.org/list.mpd")) {
            assertFalse(uri, DirectVideoSource.isSupported(uri))
        }
    }
    @Test fun `video resolver never sends document URIs to a music provider`() = kotlinx.coroutines.runBlocking {
        val engine = io.ktor.client.engine.mock.MockEngine { error("Must not make a provider request") }
        ExternalMusicRepository(io.ktor.client.HttpClient(engine)).use { repository ->
            val uri = "content://media/external/video/media/42"
            assertEquals(uri, repository.resolveStream(DirectVideoSource.mediaId(uri), ""))
        }
    }
    @Test(expected = IllegalArgumentException::class)
    fun `damaged persisted video identifier is rejected`() {
        DirectVideoSource.uri("video:!!!")
    }
}

package com.metrolist.music.ui.screens.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.metrolist.innertube.models.Artist
import com.metrolist.innertube.models.SongItem
import com.metrolist.music.LocalPlayerConnection
import com.metrolist.music.R
import com.metrolist.music.constants.JamendoClientIdKey
import com.metrolist.music.constants.MiniPlayerBottomSpacing
import com.metrolist.music.constants.MiniPlayerHeight
import com.metrolist.music.constants.NavigationBarHeight
import com.metrolist.music.extensions.toMediaItem
import com.metrolist.music.playback.queues.ListQueue
import com.metrolist.music.providers.ExternalMusicRepository
import com.metrolist.music.providers.ExternalTrack
import com.metrolist.music.providers.JamendoClientIdRequired
import com.metrolist.music.ui.component.EmptyPlaceholder
import com.metrolist.music.ui.component.YouTubeListItem
import com.metrolist.music.utils.rememberPreference
import kotlinx.coroutines.CancellationException

@Composable
fun ExternalMusicSearch(query: String, source: String, activeId: String?, isPlaying: Boolean) {
    val playerConnection = LocalPlayerConnection.current ?: return
    var clientId by rememberPreference(JamendoClientIdKey, ExternalMusicRepository.DEFAULT_JAMENDO_CLIENT_ID)
    val effectiveClientId = clientId.ifBlank { ExternalMusicRepository.DEFAULT_JAMENDO_CLIENT_ID }
    var queryInput by remember(query, source) { mutableStateOf(query) }
    var submittedQuery by remember(query, source) { mutableStateOf(query) }
    var clientIdInput by remember(effectiveClientId) { mutableStateOf(effectiveClientId) }
    var tracks by remember(query, source) { mutableStateOf<List<ExternalTrack>>(emptyList()) }
    var loading by remember(query, source) { mutableStateOf(false) }
    var failed by remember(query, source) { mutableStateOf(false) }
    var attempt by remember { mutableIntStateOf(0) }
    val needsClientId = source == ExternalMusicRepository.JAMENDO && effectiveClientId.isBlank()

    LaunchedEffect(submittedQuery, source, effectiveClientId, attempt) {
        tracks = emptyList()
        failed = false
        if (needsClientId) return@LaunchedEffect
        loading = true
        val repository = ExternalMusicRepository()
        try {
            tracks = repository.search(source, submittedQuery, effectiveClientId)
        } catch (e: CancellationException) {
            throw e
        } catch (_: JamendoClientIdRequired) {
            failed = true
        } catch (_: Exception) {
            failed = true
        } finally {
            repository.close()
            loading = false
        }
    }

    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = queryInput,
            onValueChange = { queryInput = it },
            label = { Text(stringResource(R.string.external_music_query)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        )
        Button(
            onClick = { submittedQuery = queryInput.trim(); attempt++ },
            enabled = queryInput.isNotBlank() && !loading,
            modifier = Modifier.padding(horizontal = 16.dp),
        ) { Text(stringResource(R.string.external_music_search)) }
        if (source == ExternalMusicRepository.JAMENDO) {
            OutlinedTextField(
                value = clientIdInput,
                onValueChange = { clientIdInput = it },
                label = { Text(stringResource(R.string.jamendo_client_id)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            )
            Text(
                text = stringResource(R.string.jamendo_client_id_hint),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
            Button(
                onClick = { clientId = clientIdInput.trim() },
                enabled = clientIdInput.trim() != clientId,
                modifier = Modifier.padding(horizontal = 16.dp),
            ) { Text(stringResource(R.string.save)) }
        }
        LazyColumn(Modifier.fillMaxSize()) {
            if (loading) item { CircularProgressIndicator(Modifier.padding(24.dp)) }
            if (failed) item {
                Text(stringResource(R.string.external_music_error), Modifier.padding(16.dp))
                Button(onClick = { attempt++ }, modifier = Modifier.padding(horizontal = 16.dp)) {
                    Text(stringResource(R.string.retry))
                }
            }
            if (!loading && !failed && !needsClientId && tracks.isEmpty()) item {
                EmptyPlaceholder(icon = R.drawable.search, text = stringResource(R.string.external_music_empty, submittedQuery))
            }
            if (!loading && !failed && tracks.isNotEmpty()) item {
                Text(stringResource(R.string.external_music_count, tracks.size, submittedQuery), Modifier.padding(16.dp))
            }
            items(tracks, key = { it.id }) { track ->
                val song = track.asSongItem()
                YouTubeListItem(
                    item = song,
                    isActive = activeId == track.id,
                    isPlaying = isPlaying,
                    isSwipeable = false,
                    modifier = Modifier.clickable {
                        if (activeId == track.id) {
                            playerConnection.togglePlayPause()
                        } else {
                            playerConnection.playQueue(
                                ListQueue(
                                    title = if (source == ExternalMusicRepository.JAMENDO) "Jamendo" else "HearThis",
                                    items = tracks.map { it.asSongItem().toMediaItem() },
                                    startIndex = tracks.indexOf(track),
                                ),
                            )
                        }
                    },
                )
            }
            item {
                Spacer(Modifier.height(MiniPlayerHeight + MiniPlayerBottomSpacing + NavigationBarHeight))
            }
        }
    }
}

private fun ExternalTrack.asSongItem() = SongItem(
    id = id,
    title = title,
    artists = listOf(Artist(name = artist, id = null)),
    duration = duration,
    thumbnail = artwork.orEmpty(),
)

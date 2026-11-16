package com.metrolist.music.ui.screens

import android.content.Intent
import android.provider.OpenableColumns
import android.view.SurfaceView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.metrolist.music.LocalNavController
import com.metrolist.music.LocalPlayerConnection
import com.metrolist.music.R
import com.metrolist.music.constants.MiniPlayerBottomSpacing
import com.metrolist.music.constants.MiniPlayerHeight
import com.metrolist.music.constants.NavigationBarHeight
import com.metrolist.music.extensions.toMediaItem
import com.metrolist.music.models.MediaMetadata
import com.metrolist.music.playback.queues.ListQueue
import com.metrolist.music.providers.DirectVideoSource
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoScreen() {
    val context = LocalContext.current
    val navController = LocalNavController.current
    val connection = LocalPlayerConnection.current ?: return
    val player by connection.service.playerFlow.collectAsStateWithLifecycle()
    val metadata by connection.mediaMetadata.collectAsStateWithLifecycle()
    val isPlaying by connection.isEffectivelyPlaying.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    var input by rememberSaveable { mutableStateOf("") }
    var audioOnly by rememberSaveable { mutableStateOf(false) }
    var fullscreen by rememberSaveable { mutableStateOf(false) }
    var invalidInput by remember { mutableStateOf(false) }
    var playbackError by remember(player) { mutableStateOf(false) }
    var position by remember(player) { mutableLongStateOf(0L) }
    var duration by remember(player) { mutableLongStateOf(0L) }
    var aspectRatio by remember(player) { mutableFloatStateOf(16f / 9f) }
    val isVideo = DirectVideoSource.isVideo(metadata?.id)

    fun openVideo(uri: String, title: String) {
        if (!DirectVideoSource.isSupported(uri)) {
            invalidInput = true
            return
        }
        invalidInput = false
        playbackError = false
        val item = MediaMetadata(
            id = DirectVideoSource.mediaId(uri),
            title = title,
            artists = emptyList(),
            duration = -1,
        ).toMediaItem()
        connection.playQueue(ListQueue(title = title, items = listOf(item)))
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                var name = context.getString(R.string.video_local_title)
                context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) name = cursor.getString(0) ?: name
                }
                openVideo(uri.toString(), name)
            } catch (_: Exception) {
                invalidInput = true
            }
        }
    }

    DisposableEffect(player, lifecycleOwner, audioOnly, isVideo) {
        val currentPlayer = player
        fun setVideoEnabled(enabled: Boolean) {
            currentPlayer?.let {
                it.trackSelectionParameters = it.trackSelectionParameters
                    .buildUpon().setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, !enabled).build()
            }
        }
        setVideoEnabled(isVideo && !audioOnly && lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> setVideoEnabled(isVideo && !audioOnly)
                Lifecycle.Event.ON_STOP -> setVideoEnabled(false)
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            setVideoEnabled(false)
        }
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onVideoSizeChanged(size: androidx.media3.common.VideoSize) {
                if (size.width > 0 && size.height > 0) aspectRatio = size.width * size.pixelWidthHeightRatio / size.height
            }
            override fun onPlayerError(error: PlaybackException) { playbackError = true }
            override fun onMediaItemTransition(mediaItem: androidx.media3.common.MediaItem?, reason: Int) {
                playbackError = false
            }
        }
        player?.addListener(listener)
        onDispose { player?.removeListener(listener) }
    }

    LaunchedEffect(player) {
        while (isActive) {
            position = player?.currentPosition?.coerceAtLeast(0L) ?: 0L
            duration = player?.duration?.takeIf { it != C.TIME_UNSET && it > 0L } ?: 0L
            delay(500)
        }
    }

    val controls: @Composable () -> Unit = {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            TextButton(onClick = { player?.seekBack() }) { Text(stringResource(R.string.video_back)) }
            Button(onClick = { connection.togglePlayPause() }) {
                Text(stringResource(if (isPlaying) R.string.video_pause else R.string.video_play))
            }
            TextButton(onClick = { player?.seekForward() }) { Text(stringResource(R.string.video_forward)) }
        }
        if (duration > 0) {
            var seekPosition by remember { mutableStateOf<Float?>(null) }
            Slider(
                value = seekPosition ?: position.coerceAtMost(duration).toFloat(),
                onValueChange = { seekPosition = it },
                onValueChangeFinished = {
                    seekPosition?.let { player?.seekTo(it.toLong()) }
                    seekPosition = null
                },
                valueRange = 0f..duration.toFloat(),
            )
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.video_title)) },
                navigationIcon = {
                    TextButton(onClick = { navController.navigateUp() }) {
                        Text(stringResource(R.string.video_close))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = MiniPlayerHeight + MiniPlayerBottomSpacing + NavigationBarHeight),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.video_source_hint))
            OutlinedTextField(
                value = input,
                onValueChange = { input = it; invalidInput = false },
                label = { Text(stringResource(R.string.video_link)) },
                singleLine = true,
                isError = invalidInput,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = { openVideo(input.trim(), context.getString(R.string.video_link_title)) }) {
                    Text(stringResource(R.string.video_open_link))
                }
                OutlinedButton(onClick = { picker.launch(arrayOf("video/*")) }) {
                    Text(stringResource(R.string.video_open_local))
                }
            }
            if (invalidInput) Text(stringResource(R.string.video_invalid_source), color = MaterialTheme.colorScheme.error)
            if (isVideo) {
                Text(metadata?.title.orEmpty(), style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    FilterChip(selected = !audioOnly, onClick = { audioOnly = false },
                        label = { Text(stringResource(R.string.video_title)) })
                    FilterChip(selected = audioOnly, onClick = { audioOnly = true },
                        label = { Text(stringResource(R.string.video_audio_only)) })
                    if (!audioOnly) TextButton(onClick = { fullscreen = true }) {
                        Text(stringResource(R.string.video_fullscreen))
                    }
                }
                if (!audioOnly && !fullscreen) VideoSurface(player, Modifier.fillMaxWidth().aspectRatio(aspectRatio))
                if (playbackError) Text(stringResource(R.string.video_playback_error), color = MaterialTheme.colorScheme.error)
                controls()
            }
        }
    }

    if (fullscreen && isVideo && !audioOnly) {
        Dialog(
            onDismissRequest = { fullscreen = false },
            properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
        ) {
            Column(Modifier.fillMaxSize().background(Color.Black).systemBarsPadding(), horizontalAlignment = Alignment.CenterHorizontally) {
                TextButton(onClick = { fullscreen = false }) { Text(stringResource(R.string.video_close)) }
                Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    VideoSurface(player, Modifier.fillMaxWidth().aspectRatio(aspectRatio))
                }
                controls()
            }
        }
    }
}

@Composable
private fun VideoSurface(player: ExoPlayer?, modifier: Modifier) {
    AndroidView(
        factory = { context -> SurfaceView(context).apply { keepScreenOn = true } },
        modifier = modifier.background(Color.Black),
        update = { view -> player?.setVideoSurfaceView(view) },
        onRelease = { view -> player?.clearVideoSurfaceView(view) },
    )
}

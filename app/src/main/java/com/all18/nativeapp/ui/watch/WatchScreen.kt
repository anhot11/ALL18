@file:kotlin.OptIn(androidx.media3.common.util.UnstableApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.all18.nativeapp.ui.watch

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import coil.request.ImageRequest
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.all18.nativeapp.core.model.StreamQuality
import com.all18.nativeapp.core.model.VideoItem
import com.all18.nativeapp.core.repository.MultiSourceRepository
import com.all18.nativeapp.core.repository.UserLibraryRepository
import com.all18.nativeapp.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(UnstableApi::class, ExperimentalMaterial3Api::class)
@Composable
fun WatchScreen(
    video: VideoItem,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val scope = rememberCoroutineScope()

    var streamUrl by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(true) }
    var isBuffering by remember { mutableStateOf(false) }
    var isPlaying by remember { mutableStateOf(false) }
    var isFirstFrameRendered by remember { mutableStateOf(false) }

    var currentPosition by remember { mutableLongStateOf(0L) }
    var totalDuration by remember { mutableLongStateOf(0L) }
    var bufferedPosition by remember { mutableLongStateOf(0L) }
    var isSeeking by remember { mutableStateOf(false) }
    var seekPosition by remember { mutableFloatStateOf(0f) }

    var areControlsVisible by remember { mutableStateOf(true) }
    var lastInteractionTime by remember { mutableLongStateOf(System.currentTimeMillis()) }

    var isFullscreen by remember { mutableStateOf(false) }
    var resizeMode by remember { mutableIntStateOf(AspectRatioFrameLayout.RESIZE_MODE_FIT) }

    var availableQualities by remember { mutableStateOf<List<StreamQuality>>(listOf(StreamQuality(label = "Auto"))) }
    var selectedQuality by remember { mutableStateOf(StreamQuality(label = "Auto")) }
    var showSettingsSheet by remember { mutableStateOf(false) }
    var playbackSpeed by remember { mutableFloatStateOf(1.0f) }

    var isLoopEnabled by remember { mutableStateOf(false) }
    var playbackErrorMessage by remember { mutableStateOf<String?>(null) }
    var failedQualities by remember { mutableStateOf<Set<String>>(emptySet()) }

    var showRewindIndicator by remember { mutableStateOf(false) }
    var showForwardIndicator by remember { mutableStateOf(false) }

    // Media3 Native ExoPlayer - ALWAYS MUTED FOR NIGHT SAFETY (0 DECIBELS)
    val exoPlayer = remember {
        com.all18.nativeapp.core.player.SilentPlayerManager.createSilentPlayer(context).apply {
            playWhenReady = true
        }
    }

    // Alternar modo repetición en bucle
    fun toggleLoopMode() {
        isLoopEnabled = !isLoopEnabled
        exoPlayer.repeatMode = if (isLoopEnabled) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
    }

    // Aplicar selección de calidad con soporte para HLS y streams discretos MP4
    fun onQualitySelected(q: StreamQuality) {
        selectedQuality = q
        showSettingsSheet = false
        if (q.isHlsTrack) {
            val builder = exoPlayer.trackSelectionParameters.buildUpon()
            if (q.label == "Auto") {
                builder.clearVideoSizeConstraints()
                builder.clearOverridesOfType(C.TRACK_TYPE_VIDEO)
            } else {
                builder.setMaxVideoSize(q.width, q.height)
                builder.setMinVideoSize(q.width, q.height)
            }
            exoPlayer.trackSelectionParameters = builder.build()
        } else if (q.url.isNotEmpty() && q.url != streamUrl) {
            val curPos = exoPlayer.currentPosition
            val wasPlaying = exoPlayer.isPlaying
            streamUrl = q.url
            exoPlayer.setMediaItem(MediaItem.fromUri(q.url), curPos)
            exoPlayer.prepare()
            if (wasPlaying) exoPlayer.play()
        }
    }

    // Reintento manual o automático de extracción de stream ante fallo de servidor o token expirado
    fun retryStream() {
        playbackErrorMessage = null
        failedQualities = emptySet()
        isLoading = true
        isFirstFrameRendered = false
        scope.launch {
            val freshUrl = MultiSourceRepository.extractStreamUrl(video)
            streamUrl = freshUrl
            if (freshUrl.isNotEmpty()) {
                exoPlayer.setMediaItem(MediaItem.fromUri(freshUrl), currentPosition)
                exoPlayer.prepare()
                exoPlayer.play()
            } else {
                playbackErrorMessage = "No se pudo obtener un stream válido del proveedor."
            }
            isLoading = false
        }
    }

    // Toggle system bars for immersive fullscreen
    fun applyFullscreen(fullscreen: Boolean) {
        activity?.let { act ->
            val window = act.window
            val insetsController = WindowCompat.getInsetsController(window, window.decorView)
            if (fullscreen) {
                insetsController.hide(WindowInsetsCompat.Type.systemBars())
                insetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            } else {
                insetsController.show(WindowInsetsCompat.Type.systemBars())
                act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            }
        }
    }

    // Player listener for state, tracks and playback changes
    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
                if (playing) lastInteractionTime = System.currentTimeMillis()
            }

            override fun onPlaybackStateChanged(state: Int) {
                isBuffering = state == Player.STATE_BUFFERING
                if (state == Player.STATE_READY) {
                    totalDuration = exoPlayer.duration.coerceAtLeast(0L)
                    playbackErrorMessage = null
                }
            }

            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                // Registrar calidad actual como no disponible
                failedQualities = failedQualities + selectedQuality.label

                // 1. Intentar fallback automático a otra resolución discreta no fallida
                val fallbackQuality = availableQualities.firstOrNull { q ->
                    q != selectedQuality && q.label != "Auto" && q.url.isNotEmpty() && !failedQualities.contains(q.label)
                }

                if (fallbackQuality != null) {
                    onQualitySelected(fallbackQuality)
                    return
                }

                // 2. Si no hay más calidades alternativas o fallaron todas, mostrar overlay de reintento
                playbackErrorMessage = when (error.errorCode) {
                    androidx.media3.common.PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS -> "El servidor de video devolvió un error (403/404)."
                    androidx.media3.common.PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED -> "Error de conexión con el proveedor."
                    else -> "No se pudo reproducir este video (${error.errorCodeName})."
                }
            }

            override fun onRenderedFirstFrame() {
                isFirstFrameRendered = true
                playbackErrorMessage = null
            }

            override fun onTracksChanged(tracks: Tracks) {
                val hlsQualities = mutableListOf<StreamQuality>()
                for (group in tracks.groups) {
                    if (group.type == C.TRACK_TYPE_VIDEO) {
                        for (i in 0 until group.length) {
                            val format = group.getTrackFormat(i)
                            if (format.height > 0) {
                                val label = "${format.height}p"
                                if (hlsQualities.none { it.label == label }) {
                                    hlsQualities.add(
                                        StreamQuality(
                                            label = label,
                                            width = format.width,
                                            height = format.height,
                                            isHlsTrack = true
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
                if (hlsQualities.isNotEmpty()) {
                    hlsQualities.sortByDescending { it.height }
                    val list = mutableListOf<StreamQuality>()
                    list.add(StreamQuality(label = "Auto", isHlsTrack = true))
                    list.addAll(hlsQualities)
                    availableQualities = list
                }
            }
        }
        exoPlayer.addListener(listener)

        onDispose {
            exoPlayer.removeListener(listener)
        }
    }

    // Load initial stream and qualities
    DisposableEffect(Unit) {
        UserLibraryRepository.addToHistory(video)
        scope.launch {
            isLoading = true
            val url = MultiSourceRepository.extractStreamUrl(video)
            streamUrl = url
            if (url.isNotEmpty()) {
                exoPlayer.setMediaItem(MediaItem.fromUri(url))
                exoPlayer.prepare()
            }

            // Extract qualities list if available
            val discreteQualities = MultiSourceRepository.extractStreamQualities(video)
            if (discreteQualities.isNotEmpty()) {
                val list = mutableListOf<StreamQuality>()
                list.add(StreamQuality(label = "Auto", url = url))
                list.addAll(discreteQualities)
                availableQualities = list
            }
            isLoading = false
        }

        onDispose {
            applyFullscreen(false)
            exoPlayer.stop()
            exoPlayer.release()
        }
    }

    // Periodic time and auto-hide updater
    LaunchedEffect(isPlaying, areControlsVisible, isSeeking) {
        while (true) {
            if (!isSeeking) {
                currentPosition = exoPlayer.currentPosition.coerceAtLeast(0L)
                totalDuration = exoPlayer.duration.coerceAtLeast(0L)
                bufferedPosition = exoPlayer.bufferedPosition.coerceAtLeast(0L)
            }
            if (areControlsVisible && isPlaying && System.currentTimeMillis() - lastInteractionTime > 3500) {
                areControlsVisible = false
            }
            delay(250)
        }
    }


    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    val effectivelyFullscreen = isFullscreen || isLandscape

    // Handle Back action
    val handleBack = {
        if (isFullscreen) {
            isFullscreen = false
            applyFullscreen(false)
        } else {
            onBack()
        }
    }

    Scaffold(
        containerColor = AmoledBlack,
        topBar = {
            if (!effectivelyFullscreen) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = handleBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Volver",
                            tint = Color.White
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = video.source,
                        color = PornhubOrange,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(if (effectivelyFullscreen) PaddingValues(0.dp) else padding)
                .background(AmoledBlack)
        ) {
            // Player Box: Fullscreen or 16:9
            Box(
                modifier = if (effectivelyFullscreen) {
                    Modifier
                        .fillMaxSize()
                        .background(Color.Black)
                } else {
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .background(Color.Black)
                },
                contentAlignment = Alignment.Center
            ) {
                if (streamUrl.isNotEmpty()) {
                    AndroidView(
                        factory = { ctx ->
                            PlayerView(ctx).apply {
                                player = exoPlayer
                                useController = false
                                this.resizeMode = resizeMode
                                layoutParams = FrameLayout.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT
                                )
                            }
                        },
                        update = { playerView ->
                            playerView.resizeMode = resizeMode
                        },
                        modifier = Modifier.fillMaxSize()
                    )

                    // Touch Gesture Detection (Tap for controls, Double tap for seek)
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(Unit) {
                                detectTapGestures(
                                    onTap = {
                                        areControlsVisible = !areControlsVisible
                                        if (areControlsVisible) lastInteractionTime = System.currentTimeMillis()
                                    },
                                    onDoubleTap = { offset ->
                                        val isLeft = offset.x < size.width / 2
                                        if (isLeft) {
                                            val target = (exoPlayer.currentPosition - 10000L).coerceAtLeast(0L)
                                            exoPlayer.seekTo(target)
                                            showRewindIndicator = true
                                            scope.launch { delay(650); showRewindIndicator = false }
                                        } else {
                                            val maxDuration = exoPlayer.duration.coerceAtLeast(0L)
                                            val target = (exoPlayer.currentPosition + 10000L).coerceAtMost(maxDuration)
                                            exoPlayer.seekTo(target)
                                            showForwardIndicator = true
                                            scope.launch { delay(650); showForwardIndicator = false }
                                        }
                                        lastInteractionTime = System.currentTimeMillis()
                                    }
                                )
                            }
                    )

                    // Double-tap visual indicators
                    if (showRewindIndicator) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(0.5f)
                                .align(Alignment.CenterStart)
                                .background(Color.White.copy(alpha = 0.12f), RoundedCornerShape(topEnd = 80.dp, bottomEnd = 80.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.Replay10, contentDescription = null, tint = Color.White, modifier = Modifier.size(36.dp))
                                Text("-10s", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                        }
                    }

                    if (showForwardIndicator) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(0.5f)
                                .align(Alignment.CenterEnd)
                                .background(Color.White.copy(alpha = 0.12f), RoundedCornerShape(topStart = 80.dp, bottomStart = 80.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.Forward10, contentDescription = null, tint = Color.White, modifier = Modifier.size(36.dp))
                                Text("+10s", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                        }
                    }

                    // Modern Animated Compose Player Overlay
                    androidx.compose.animation.AnimatedVisibility(
                        visible = areControlsVisible,
                        enter = fadeIn(animationSpec = tween(180)),
                        exit = fadeOut(animationSpec = tween(180))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.35f))
                        ) {
                            // Top Bar Scrim
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .align(Alignment.TopCenter)
                                    .background(
                                        Brush.verticalGradient(
                                            listOf(Color.Black.copy(alpha = 0.85f), Color.Transparent)
                                        )
                                    )
                                    .padding(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (isFullscreen) {
                                        IconButton(onClick = handleBack) {
                                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Salir pantalla completa", tint = Color.White)
                                        }
                                    }

                                    Column(modifier = Modifier.weight(1f).padding(horizontal = 8.dp)) {
                                        Text(
                                            text = video.title,
                                            color = Color.White,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = video.source,
                                            color = PornhubOrange,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }

                                    // Quality Badge Pill (Clickable)
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = Color(0xFF222222),
                                        modifier = Modifier.clickable {
                                            lastInteractionTime = System.currentTimeMillis()
                                            showSettingsSheet = true
                                        }
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(6.dp)
                                                    .background(PornhubOrange, CircleShape)
                                            )
                                            Spacer(modifier = Modifier.width(5.dp))
                                            Text(
                                                text = selectedQuality.label,
                                                color = Color.White,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.width(4.dp))

                                    // Botón de Modo Bucle (Repetición continua)
                                    IconButton(
                                        onClick = {
                                            lastInteractionTime = System.currentTimeMillis()
                                            toggleLoopMode()
                                        },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (isLoopEnabled) Icons.Default.RepeatOne else Icons.Default.Repeat,
                                            contentDescription = if (isLoopEnabled) "Bucle activado" else "Bucle desactivado",
                                            tint = if (isLoopEnabled) PornhubOrange else Color.White,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(4.dp))

                                    // Aspect Ratio Toggle
                                    IconButton(
                                        onClick = {
                                            lastInteractionTime = System.currentTimeMillis()
                                            resizeMode = if (resizeMode == AspectRatioFrameLayout.RESIZE_MODE_FIT) {
                                                AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                                            } else {
                                                AspectRatioFrameLayout.RESIZE_MODE_FIT
                                            }
                                        }
                                    ) {
                                        Icon(
                                            imageVector = if (resizeMode == AspectRatioFrameLayout.RESIZE_MODE_ZOOM) Icons.Default.FitScreen else Icons.Default.AspectRatio,
                                            contentDescription = "Aspect Ratio",
                                            tint = Color.White,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }

                                    // Settings Cog Button
                                    IconButton(
                                        onClick = {
                                            lastInteractionTime = System.currentTimeMillis()
                                            showSettingsSheet = true
                                        }
                                    ) {
                                        Icon(
                                            Icons.Default.Settings,
                                            contentDescription = "Ajustes de reproducción",
                                            tint = Color.White,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }

                            // Center Controls: Rewind, Play/Pause, Fast Forward
                            Row(
                                modifier = Modifier.align(Alignment.Center),
                                horizontalArrangement = Arrangement.spacedBy(28.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // -10s Jump
                                Surface(
                                    shape = CircleShape,
                                    color = Color.Black.copy(alpha = 0.50f),
                                    modifier = Modifier.size(46.dp).clickable {
                                        lastInteractionTime = System.currentTimeMillis()
                                        val target = (exoPlayer.currentPosition - 10000L).coerceAtLeast(0L)
                                        exoPlayer.seekTo(target)
                                    }
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(Icons.Default.Replay10, contentDescription = "Retroceder 10s", tint = Color.White, modifier = Modifier.size(26.dp))
                                    }
                                }

                                // Play / Pause / Buffering
                                Surface(
                                    shape = CircleShape,
                                    color = PornhubOrange,
                                    modifier = Modifier.size(60.dp).clickable {
                                        lastInteractionTime = System.currentTimeMillis()
                                        if (exoPlayer.isPlaying) {
                                            exoPlayer.pause()
                                        } else {
                                            exoPlayer.play()
                                        }
                                    }
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        if (isBuffering) {
                                            CircularProgressIndicator(color = Color.Black, modifier = Modifier.size(28.dp), strokeWidth = 3.dp)
                                        } else {
                                            Icon(
                                                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                                contentDescription = if (isPlaying) "Pausar" else "Reproducir",
                                                tint = Color.Black,
                                                modifier = Modifier.size(34.dp)
                                            )
                                        }
                                    }
                                }

                                // +10s Jump
                                Surface(
                                    shape = CircleShape,
                                    color = Color.Black.copy(alpha = 0.50f),
                                    modifier = Modifier.size(46.dp).clickable {
                                        lastInteractionTime = System.currentTimeMillis()
                                        val maxDuration = exoPlayer.duration.coerceAtLeast(0L)
                                        val target = (exoPlayer.currentPosition + 10000L).coerceAtMost(maxDuration)
                                        exoPlayer.seekTo(target)
                                    }
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(Icons.Default.Forward10, contentDescription = "Adelantar 10s", tint = Color.White, modifier = Modifier.size(26.dp))
                                    }
                                }
                            }

                            // Bottom Bar Scrim: Seekbar, Timestamps & Fullscreen
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .align(Alignment.BottomCenter)
                                    .background(
                                        Brush.verticalGradient(
                                            listOf(Color.Transparent, Color.Black.copy(alpha = 0.90f))
                                        )
                                    )
                                    .padding(horizontal = 14.dp, vertical = 8.dp)
                            ) {
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    // Custom Seekbar Slider
                                    Slider(
                                        value = if (isSeeking) seekPosition else if (totalDuration > 0) currentPosition.toFloat() / totalDuration else 0f,
                                        onValueChange = { frac ->
                                            isSeeking = true
                                            seekPosition = frac
                                            lastInteractionTime = System.currentTimeMillis()
                                        },
                                        onValueChangeFinished = {
                                            isSeeking = false
                                            val targetMs = (seekPosition * totalDuration).toLong().coerceIn(0L, totalDuration)
                                            exoPlayer.seekTo(targetMs)
                                            lastInteractionTime = System.currentTimeMillis()
                                        },
                                        colors = SliderDefaults.colors(
                                            thumbColor = PornhubOrange,
                                            activeTrackColor = PornhubOrange,
                                            inactiveTrackColor = Color.White.copy(alpha = 0.28f)
                                        ),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(26.dp)
                                    )

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        // Time label
                                        val curDisplay = if (isSeeking) (seekPosition * totalDuration).toLong() else currentPosition
                                        Text(
                                            text = "${formatDuration(curDisplay)} / ${formatDuration(totalDuration)}",
                                            color = Color.White,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Medium
                                        )

                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            // Speed Indicator
                                            if (playbackSpeed != 1.0f) {
                                                Text(
                                                    text = "${playbackSpeed}x",
                                                    color = PornhubOrange,
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.padding(end = 8.dp)
                                                )
                                            }

                                            // Fullscreen Button
                                            IconButton(
                                                onClick = {
                                                    lastInteractionTime = System.currentTimeMillis()
                                                    isFullscreen = !isFullscreen
                                                    applyFullscreen(isFullscreen)
                                                },
                                                modifier = Modifier.size(32.dp)
                                            ) {
                                                Icon(
                                                    imageVector = if (isFullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                                                    contentDescription = if (isFullscreen) "Salir de pantalla completa" else "Pantalla completa",
                                                    tint = Color.White,
                                                    modifier = Modifier.size(24.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                }
            }

                // 2. Loading State: Thumbnail backdrop + Functional Loader (NEVER BLACK SCREEN!)
                androidx.compose.animation.AnimatedVisibility(
                    visible = !isFirstFrameRendered && (isLoading || streamUrl.isEmpty() || isBuffering),
                    enter = fadeIn(animationSpec = tween(150)),
                    exit = fadeOut(animationSpec = tween(350))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black),
                        contentAlignment = Alignment.Center
                    ) {
                        // Video thumbnail as backdrop
                        if (video.thumbUrl.isNotEmpty()) {
                            AsyncImage(
                                model = ImageRequest.Builder(LocalContext.current)
                                    .data(video.thumbUrl)
                                    .crossfade(true)
                                    .build(),
                                contentDescription = video.title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                            // Dimming layer
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.Black.copy(alpha = 0.65f))
                            )
                        }

                        // Functional animated loader
                        FunctionalPlayerLoader(
                            label = if (isLoading) "Obteniendo stream..." else "Cargando video..."
                        )
                    }
                }

                // 3. Mid-stream buffering overlay (when first frame has already rendered)
                if (isFirstFrameRendered && isBuffering) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.35f)),
                        contentAlignment = Alignment.Center
                    ) {
                        FunctionalPlayerLoader(label = "")
                    }
                }

                // 4. Extraction or Playback failure state with direct Retry
                if (!isLoading && (streamUrl.isEmpty() || playbackErrorMessage != null)) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.88f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier.padding(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = null,
                                tint = PornhubOrange,
                                modifier = Modifier.size(44.dp)
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = playbackErrorMessage ?: "No se pudo extraer el stream directo.",
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(14.dp))
                            Button(
                                onClick = { retryStream() },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = PornhubOrange,
                                    contentColor = Color.Black
                                ),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text("Reintentar reproducción", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // Video Details (visible only in standard portrait mode)
            if (!effectivelyFullscreen) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text(
                        text = video.title,
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        lineHeight = 22.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(text = video.author, color = PornhubOrange, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Text(text = " • ", color = TextSecondary)
                        Text(text = video.durationText, color = TextSecondary, fontSize = 13.sp)
                        if (video.views.isNotEmpty()) {
                            Text(text = " • ", color = TextSecondary)
                            Text(text = video.views, color = TextSecondary, fontSize = 13.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    val isLiked = UserLibraryRepository.isVideoLiked(video.id)
                    val isSaved = UserLibraryRepository.isVideoSaved(video.id)
                    var likedState by remember { mutableStateOf(isLiked) }
                    var savedState by remember { mutableStateOf(isSaved) }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = {
                                likedState = !likedState
                                UserLibraryRepository.toggleLikeVideo(video)
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (likedState) Color(0xFFFE2C55) else Color(0xFF1E1E1E),
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(20.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = if (likedState) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                contentDescription = "Me gusta",
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (likedState) "Te gusta" else "Me gusta", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = {
                                savedState = !savedState
                                UserLibraryRepository.toggleSaveVideo(video)
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (savedState) PornhubOrange else Color(0xFF1E1E1E),
                                contentColor = if (savedState) Color.Black else Color.White
                            ),
                            shape = RoundedCornerShape(20.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = if (savedState) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                                contentDescription = "Guardar",
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (savedState) "Guardado" else "Guardar", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    HorizontalDivider(color = BorderSubtle)
                }
            }
        }
    }

    // Modal Bottom Sheet: Quality and Playback Speed Selector
    if (showSettingsSheet) {
        ModalBottomSheet(
            onDismissRequest = { showSettingsSheet = false },
            containerColor = Color(0xFF161616),
            dragHandle = { BottomSheetDefaults.DragHandle(color = Color.Gray) }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp)
                    .navigationBarsPadding()
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.HighQuality, contentDescription = null, tint = PornhubOrange, modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("Calidad del Video", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Available Qualities List
                availableQualities.forEach { quality ->
                    val isSelected = quality.label == selectedQuality.label
                    Surface(
                        color = if (isSelected) Color(0xFF242424) else Color.Transparent,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onQualitySelected(quality) }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = when (quality.label) {
                                        "Auto" -> "Auto (Recomendada)"
                                        "1080p" -> "1080p Full HD"
                                        "720p" -> "720p HD"
                                        "480p" -> "480p SD"
                                        else -> quality.label
                                    },
                                    color = if (isSelected) PornhubOrange else Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                            if (isSelected) {
                                Icon(Icons.Default.Check, contentDescription = "Seleccionada", tint = PornhubOrange, modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                }

                Spacer(modifier = Modifier.height(18.dp))
                HorizontalDivider(color = Color(0xFF2A2A2A))
                Spacer(modifier = Modifier.height(14.dp))

                // Playback Speed Section
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Speed, contentDescription = null, tint = PornhubOrange, modifier = Modifier.size(22.dp))
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("Velocidad de reproducción", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.height(10.dp))

                val speedOptions = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    speedOptions.forEach { spd ->
                        val isSpdSelected = playbackSpeed == spd
                        FilterChip(
                            selected = isSpdSelected,
                            onClick = {
                                playbackSpeed = spd
                                exoPlayer.setPlaybackSpeed(spd)
                                showSettingsSheet = false
                            },
                            label = {
                                Text(if (spd == 1.0f) "1.0x" else "${spd}x", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = PornhubOrange,
                                selectedLabelColor = Color.Black,
                                containerColor = Color(0xFF222222),
                                labelColor = Color.White
                            ),
                            border = null
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                HorizontalDivider(color = Color(0xFF2A2A2A))
                Spacer(modifier = Modifier.height(14.dp))

                // Modo Repetición en Bucle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Repeat,
                            contentDescription = null,
                            tint = if (isLoopEnabled) PornhubOrange else Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text("Repetición en bucle", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            Text("Reiniciar el video automáticamente al finalizar", color = Color(0xFF888888), fontSize = 11.5.sp)
                        }
                    }
                    Switch(
                        checked = isLoopEnabled,
                        onCheckedChange = { toggleLoopMode() },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.Black,
                            checkedTrackColor = PornhubOrange,
                            uncheckedThumbColor = Color(0xFF888888),
                            uncheckedTrackColor = Color(0xFF2A2A2A)
                        )
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

private fun formatDuration(ms: Long): String {
    if (ms <= 0) return "00:00"
    val totalSec = ms / 1000
    val hours = totalSec / 3600
    val minutes = (totalSec % 3600) / 60
    val seconds = totalSec % 60
    return if (hours > 0) {
        String.format("%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%02d:%02d", minutes, seconds)
    }
}

private fun Context.findActivity(): Activity? {
    var ctx = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

@Composable
fun FunctionalPlayerLoader(
    modifier: Modifier = Modifier,
    label: String = "Cargando..."
) {
    val infiniteTransition = rememberInfiniteTransition(label = "player_loader")

    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing)
        ),
        label = "rotation"
    )

    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(850, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.25f,
        targetValue = 0.65f,
        animationSpec = infiniteRepeatable(
            animation = tween(850, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowAlpha"
    )

    val waveOffset by infiniteTransition.animateFloat(
        initialValue = -8f,
        targetValue = 8f,
        animationSpec = infiniteRepeatable(
            animation = tween(700, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "waveOffset"
    )

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(contentAlignment = Alignment.Center) {
            // Outer glowing halo with breathing expansion
            Box(
                modifier = Modifier
                    .size(68.dp)
                    .graphicsLayer {
                        scaleX = pulseScale
                        scaleY = pulseScale
                        alpha = glowAlpha
                    }
                    .background(PornhubOrange, CircleShape)
            )

            // Inner dark frosted disc
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .background(Color.Black.copy(alpha = 0.65f), CircleShape)
            )

            // Dual rotating ring with PornhubOrange accent
            CircularProgressIndicator(
                color = PornhubOrange,
                trackColor = Color.White.copy(alpha = 0.12f),
                strokeWidth = 3.5.dp,
                modifier = Modifier
                    .size(48.dp)
                    .graphicsLayer {
                        rotationZ = rotation
                    }
            )

            // Center pulsing dot with slight horizontal floating wave
            Box(
                modifier = Modifier
                    .size(11.dp)
                    .graphicsLayer {
                        scaleX = pulseScale
                        scaleY = pulseScale
                        translationX = waveOffset
                    }
                    .background(PornhubOrange, CircleShape)
            )
        }

        if (label.isNotEmpty()) {
            Spacer(modifier = Modifier.height(14.dp))
            Surface(
                color = Color.Black.copy(alpha = 0.55f),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = label,
                    color = Color.White.copy(alpha = 0.95f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                )
            }
        }
    }
}

package com.all18.nativeapp.ui.tiktok

import android.content.Context
import android.content.Intent
import android.widget.Toast
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil.request.CachePolicy
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.compose.ui.draw.alpha
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.size.Size
import com.all18.nativeapp.core.model.TikTokItem
import com.all18.nativeapp.core.player.SilentPlayerManager
import com.all18.nativeapp.ui.theme.*
import kotlinx.coroutines.delay
import java.util.Locale
import kotlin.math.roundToInt

// Colores oficiales de la interfaz TikTok
private val TikTokPink = Color(0xFFFE2C55)
private val TikTokBookmarkYellow = Color(0xFFFAC61B)
private val TikTokIconWhite = Color.White
private val TikTokTextDim = Color.White.copy(alpha = 0.65f)

private data class HeartTapParticle(
    val id: Long,
    val offset: Offset,
    val rotation: Float
)

@kotlin.OptIn(
    androidx.compose.foundation.ExperimentalFoundationApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class
)
@Composable
fun TikTokScreen(
    viewModel: TikTokViewModel
) {
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        TikTokUiPreferences.load(context)
    }
    val uiMode by TikTokUiPreferences.uiMode.collectAsState()

    val state by viewModel.uiState.collectAsState()
    val selectedTab by viewModel.selectedTab.collectAsState()
    val isLiveMode by viewModel.isLiveMode.collectAsState()

    if (isLiveMode) {
        TikTokLiveScreen(
            viewModel = viewModel,
            onExitLive = { viewModel.toggleLiveMode(false) }
        )
        return
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AmoledBlack)
    ) {
        when (val s = state) {
            is TikTokUiState.Loading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = TikTokPink)
                }
            }
            is TikTokUiState.Error -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                        Text(text = s.message, color = TextSecondary, fontSize = 14.sp)
                        Spacer(modifier = Modifier.height(14.dp))
                        Button(
                            onClick = { viewModel.loadFeed(1) },
                            colors = ButtonDefaults.buttonColors(containerColor = TikTokPink, contentColor = Color.White),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text("Reintentar", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
            is TikTokUiState.Success -> {
                val initialPage = viewModel.currentVideoIndex.coerceIn(0, (s.items.size - 1).coerceAtLeast(0))
                val pagerState = rememberPagerState(
                    initialPage = initialPage,
                    pageCount = { s.items.size }
                )

                // Paginacion continua al aproximarse al final
                LaunchedEffect(pagerState.currentPage) {
                    viewModel.onPageSelected(pagerState.currentPage)
                    if (pagerState.currentPage >= s.items.size - 2) {
                        viewModel.loadNextPage()
                    }
                }

                VerticalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize()
                ) { page ->
                    val item = s.items[page]
                    val isPageActive = pagerState.currentPage == page
                    val streamUrl = viewModel.streamUrls[item.id] ?: item.videoUrl.takeIf { it.isNotEmpty() }

                    TikTokPageItem(
                        item = item,
                        isActive = isPageActive,
                        streamUrl = streamUrl,
                        uiMode = uiMode,
                        onToggleLike = { viewModel.toggleLike(item.id) },
                        onToggleFollow = { viewModel.toggleFollow(item.id) },
                        onToggleBookmark = { viewModel.toggleBookmark(item.id) },
                        onShare = { viewModel.incrementShare(item.id) },
                        onRetry = { viewModel.retryStream(item) }
                    )
                }

                // Cabecera Superior: Insignia LIVE | Explorar | Siguiendo | Para ti | Lupa (🔍)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(
                            horizontal = if (uiMode == TikTokUiMode.DEFAULT_CLEAN) 14.dp else 16.dp,
                            vertical = if (uiMode == TikTokUiMode.DEFAULT_CLEAN) 6.dp else 10.dp
                        ),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Botón interactivo LIVE oficial TikTok con animación pulsante fluida
                    val liveTransition = rememberInfiniteTransition(label = "liveDotTransition")
                    val liveDotAlpha by liveTransition.animateFloat(
                        initialValue = 0.35f,
                        targetValue = 1.0f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(850, easing = FastOutSlowInEasing),
                            repeatMode = RepeatMode.Reverse
                        ),
                        label = "liveDotAlpha"
                    )

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.Black.copy(alpha = 0.30f))
                            .border(0.8.dp, TikTokPink.copy(alpha = 0.75f), RoundedCornerShape(12.dp))
                            .clickable { viewModel.toggleLiveMode(true) }
                            .padding(horizontal = 9.dp, vertical = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .graphicsLayer { alpha = liveDotAlpha }
                                    .background(TikTokPink)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = "LIVE",
                                color = Color.White,
                                fontSize = if (uiMode == TikTokUiMode.DEFAULT_CLEAN) 12.sp else 13.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp
                            )
                        }
                    }

                    // Pestañas Centrales: Explorar | Siguiendo | Para ti con transiciones fluidas de color y línea
                    val tabs = listOf("Explorar", "Siguiendo", "Para ti")
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(if (uiMode == TikTokUiMode.DEFAULT_CLEAN) 14.dp else 18.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        tabs.forEach { tabTitle ->
                            val isSelected = selectedTab == tabTitle
                            val tabTextColor by animateColorAsState(
                                targetValue = if (isSelected) Color.White else TikTokTextDim,
                                animationSpec = tween(180),
                                label = "tabTextColor"
                            )
                            val indicatorAlpha by animateFloatAsState(
                                targetValue = if (isSelected) 1f else 0f,
                                animationSpec = tween(180),
                                label = "indicatorAlpha"
                            )

                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) {
                                    viewModel.selectTab(tabTitle)
                                }
                            ) {
                                Text(
                                    text = tabTitle,
                                    color = tabTextColor,
                                    fontSize = if (uiMode == TikTokUiMode.DEFAULT_CLEAN) {
                                        if (isSelected) 15.sp else 14.sp
                                    } else {
                                        if (isSelected) 17.sp else 16.sp
                                    },
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold
                                )
                                Spacer(modifier = Modifier.height(3.dp))
                                Box(
                                    modifier = Modifier
                                        .width(if (uiMode == TikTokUiMode.DEFAULT_CLEAN) 22.dp else 28.dp)
                                        .height(2.dp)
                                        .graphicsLayer { alpha = indicatorAlpha }
                                        .clip(RoundedCornerShape(1.dp))
                                        .background(Color.White)
                                )
                            }
                        }
                    }

                    // Icono de Búsqueda Superior Derecho (Lupa TikTok)
                    IconButton(
                        onClick = { /* Búsqueda */ },
                        modifier = Modifier
                            .size(if (uiMode == TikTokUiMode.DEFAULT_CLEAN) 32.dp else 36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Buscar",
                            tint = Color.White.copy(alpha = 0.90f),
                            modifier = Modifier.size(if (uiMode == TikTokUiMode.DEFAULT_CLEAN) 22.dp else 26.dp)
                        )
                    }
                }
            }
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
fun TikTokPageItem(
    item: TikTokItem,
    isActive: Boolean,
    streamUrl: String?,
    uiMode: TikTokUiMode = TikTokUiMode.DEFAULT_CLEAN,
    onToggleLike: () -> Unit,
    onToggleFollow: () -> Unit,
    onToggleBookmark: () -> Unit,
    onShare: () -> Unit = {},
    onRetry: () -> Unit = {}
) {
    val context = LocalContext.current
    var isPlaying by remember { mutableStateOf(false) }
    var showPauseIcon by remember { mutableStateOf(false) }
    var isFirstFrameRendered by remember { mutableStateOf(false) }

    var currentPositionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(0L) }

    // Particulas de corazon en doble toque
    var heartParticles by remember { mutableStateOf<List<HeartTapParticle>>(emptyList()) }

    // Dimensiones y orientacion del video decodificado
    var videoWidth by remember(item.id) { mutableIntStateOf(item.width) }
    var videoHeight by remember(item.id) { mutableIntStateOf(item.height) }

    val isVideoHorizontal = remember(videoWidth, videoHeight, item.width, item.height) {
        if (videoWidth > 0 && videoHeight > 0) {
            videoWidth > videoHeight
        } else if (item.width > 0 && item.height > 0) {
            item.width > item.height
        } else {
            false
        }
    }

    // El 60% de los videos horizontales se muestran en horizontal completo sin zoom (RESIZE_MODE_FIT)
    // El 40% restante con zoom variado (RESIZE_MODE_ZOOM)
    val defaultIsFit = remember(item.id) {
        (kotlin.math.abs(item.id.hashCode()) % 10) < 6
    }
    var isFitMode by remember(item.id, defaultIsFit) { mutableStateOf(defaultIsFit) }

    val targetResizeMode = if (isVideoHorizontal) {
        if (isFitMode) AspectRatioFrameLayout.RESIZE_MODE_FIT else AspectRatioFrameLayout.RESIZE_MODE_ZOOM
    } else {
        AspectRatioFrameLayout.RESIZE_MODE_ZOOM
    }

    // Timeout de seguridad de 4 segundos para evitar spinners congelados indefinidamente
    var hasLoadTimeout by remember(item.id, isActive) { mutableStateOf(false) }
    LaunchedEffect(isActive, isFirstFrameRendered, streamUrl) {
        if (isActive && !isFirstFrameRendered) {
            delay(4000)
            if (!isFirstFrameRendered) {
                hasLoadTimeout = true
            }
        } else {
            hasLoadTimeout = false
        }
    }

    // Reproductor ExoPlayer estrictamente silenciado (volumen = 0f)
    var player by remember { mutableStateOf<ExoPlayer?>(null) }

    DisposableEffect(isActive, streamUrl, item.isPhotoPost) {
        isFirstFrameRendered = false
        hasLoadTimeout = false
        currentPositionMs = 0L
        durationMs = 0L
        isPlaying = false
        showPauseIcon = false

        var p: ExoPlayer? = null
        var listener: Player.Listener? = null

        if (isActive && !item.isPhotoPost && !streamUrl.isNullOrEmpty()) {
            p = SilentPlayerManager.createSilentPlayer(context).apply {
                repeatMode = Player.REPEAT_MODE_ONE
                setMediaItem(MediaItem.fromUri(streamUrl))
                prepare()
                playWhenReady = true
            }

            listener = object : Player.Listener {
                override fun onRenderedFirstFrame() {
                    isFirstFrameRendered = true
                    hasLoadTimeout = false
                }

                override fun onIsPlayingChanged(playing: Boolean) {
                    isPlaying = playing
                    if (playing) {
                        showPauseIcon = false
                    }
                }

                override fun onPlaybackStateChanged(state: Int) {
                    if (state == Player.STATE_READY) {
                        val d = p.duration
                        if (d > 0) durationMs = d
                    }
                }

                override fun onVideoSizeChanged(videoSize: androidx.media3.common.VideoSize) {
                    val rot = videoSize.unappliedRotationDegrees
                    val w = if (rot == 90 || rot == 270) videoSize.height else videoSize.width
                    val h = if (rot == 90 || rot == 270) videoSize.width else videoSize.height
                    if (w > 0 && h > 0) {
                        videoWidth = w
                        videoHeight = h
                    }
                }

                override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                    android.util.Log.e("TikTokScreen", "Error playing ${item.id}: ${error.message}")
                    hasLoadTimeout = true
                    onRetry()
                }
            }
            p.addListener(listener)
            player = p
        } else {
            player?.release()
            player = null
        }

        onDispose {
            listener?.let { p?.removeListener(it) }
            p?.release()
            player?.release()
            player = null
        }
    }

    // Monitoreo continuo de posicion de reproduccion real
    LaunchedEffect(player, isActive, isFirstFrameRendered) {
        if (isActive && player != null && isFirstFrameRendered) {
            while (true) {
                player?.let { p ->
                    currentPositionMs = p.currentPosition.coerceAtLeast(0L)
                    val d = p.duration
                    if (d > 0) durationMs = d
                }
                delay(35)
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = { tapOffset ->
                        onToggleLike()
                        val randomRot = (-18..18).random().toFloat()
                        val particle = HeartTapParticle(
                            id = System.currentTimeMillis(),
                            offset = tapOffset,
                            rotation = randomRot
                        )
                        heartParticles = heartParticles + particle
                    },
                    onTap = {
                        player?.let { p ->
                            if (p.isPlaying) {
                                p.pause()
                                isPlaying = false
                                showPauseIcon = true
                            } else {
                                p.play()
                                isPlaying = true
                                showPauseIcon = false
                            }
                        }
                    }
                )
            }
    ) {
        // 1. Reproduccion de Video o Carrusel de Fotos
        if (item.isPhotoPost) {
            val photoList = if (item.photoUrls.isNotEmpty()) item.photoUrls else listOf(item.thumbUrl)
            if (photoList.size > 1) {
                val horizontalPagerState = rememberPagerState(pageCount = { photoList.size })
                HorizontalPager(
                    state = horizontalPagerState,
                    modifier = Modifier.fillMaxSize()
                ) { pageIndex ->
                    TikTokPhotoImage(
                        photoUrl = photoList[pageIndex],
                        title = item.title
                    )
                }

                // Indicador de Puntos del Carrusel (Estilo TikTok)
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 120.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    repeat(photoList.size) { dotIndex ->
                        val isSelected = horizontalPagerState.currentPage == dotIndex
                        Box(
                            modifier = Modifier
                                .size(if (isSelected) 7.dp else 5.dp)
                                .clip(CircleShape)
                                .background(if (isSelected) Color.White else Color.White.copy(alpha = 0.35f))
                        )
                    }
                }

                // Insignia contador superior (ej. 1/5)
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .statusBarsPadding()
                        .padding(top = 54.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.Black.copy(alpha = 0.5f))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "${horizontalPagerState.currentPage + 1}/${photoList.size}",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            } else {
                // Foto individual
                TikTokPhotoImage(
                    photoUrl = item.thumbUrl,
                    title = item.title
                )
            }
        } else {
            // Video / Post de video
            Box(modifier = Modifier.fillMaxSize()) {
                // Backdrop para videos horizontales con 100% de imagen visible (efecto cine elegante)
                if (isVideoHorizontal && isFitMode) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(item.thumbUrl)
                            .crossfade(true)
                            .build(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .alpha(0.22f)
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.65f))
                    )
                } else {
                    // Miniatura de fondo estatico permanente con cache Coil optimizado
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(item.thumbUrl)
                            .size(Size.ORIGINAL)
                            .crossfade(true)
                            .memoryCachePolicy(CachePolicy.ENABLED)
                            .diskCachePolicy(CachePolicy.ENABLED)
                            .build(),
                        contentDescription = item.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                // Superficie de reproduccion de ExoPlayer
                if (isActive && player != null) {
                    AndroidView(
                        factory = { ctx ->
                            PlayerView(ctx).apply {
                                this.player = player
                                useController = false
                                setShutterBackgroundColor(android.graphics.Color.TRANSPARENT)
                                resizeMode = targetResizeMode
                                layoutParams = FrameLayout.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT
                                )
                            }
                        },
                        update = { view ->
                            view.player = player
                            view.resizeMode = targetResizeMode
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }

                // Caratula y spinner TikTok mientras no se haya renderizado el primer frame o se este resolviendo la URL
                androidx.compose.animation.AnimatedVisibility(
                    visible = isActive && (!isFirstFrameRendered || streamUrl.isNullOrEmpty()),
                    enter = fadeIn(tween(150)),
                    exit = fadeOut(tween(250)),
                    modifier = Modifier.fillMaxSize()
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(item.thumbUrl)
                                .crossfade(true)
                                .memoryCachePolicy(CachePolicy.ENABLED)
                                .diskCachePolicy(CachePolicy.ENABLED)
                                .build(),
                            contentDescription = item.title,
                            contentScale = if (isVideoHorizontal && isFitMode) ContentScale.Fit else ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.35f)),
                            contentAlignment = Alignment.Center
                        ) {
                            if (hasLoadTimeout) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.padding(16.dp)
                                ) {
                                    IconButton(
                                        onClick = {
                                            hasLoadTimeout = false
                                            onRetry()
                                            player?.prepare()
                                            player?.play()
                                        },
                                        modifier = Modifier
                                            .size(50.dp)
                                            .clip(CircleShape)
                                            .background(Color.Black.copy(alpha = 0.7f))
                                            .border(1.dp, Color.White.copy(alpha = 0.4f), CircleShape)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Refresh,
                                            contentDescription = "Reintentar",
                                            tint = Color.White,
                                            modifier = Modifier.size(26.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "Toca para reintentar",
                                        color = Color.White,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            } else {
                                CircularProgressIndicator(
                                    color = TikTokPink,
                                    modifier = Modifier.size(42.dp),
                                    strokeWidth = 3.dp
                                )
                            }
                        }
                    }
                }
            }
        }

        // Botón/Insignia interactiva para alternar 100% Horizontal (sin zoom) <-> Zoom en videos horizontales
        if (isVideoHorizontal && isFirstFrameRendered) {
            Surface(
                onClick = { isFitMode = !isFitMode },
                shape = RoundedCornerShape(18.dp),
                color = Color.Black.copy(alpha = 0.50f),
                border = androidx.compose.foundation.BorderStroke(0.6.dp, Color.White.copy(alpha = 0.35f)),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(top = 64.dp, end = 12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp)
                ) {
                    Icon(
                        imageVector = if (isFitMode) Icons.Default.AspectRatio else Icons.Default.FitScreen,
                        contentDescription = if (isFitMode) "100% Horizontal" else "Zoom",
                        tint = if (isFitMode) Color.White else TikTokPink,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (isFitMode) "100% Horizontal (HD)" else "Zoom (Ajustar)",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        // 2. Animación de Pausa/Play Fluida y Transparente (GPU graphicsLayer, sin sombras pesadas)
        val playPauseAlpha by animateFloatAsState(
            targetValue = if (showPauseIcon && !isPlaying) 1f else 0f,
            animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),
            label = "playPauseAlpha"
        )
        val playPauseScale by animateFloatAsState(
            targetValue = if (showPauseIcon && !isPlaying) 1f else 0.7f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMedium
            ),
            label = "playPauseScale"
        )

        if (playPauseAlpha > 0.01f) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(72.dp)
                    .graphicsLayer {
                        alpha = playPauseAlpha
                        scaleX = playPauseScale
                        scaleY = playPauseScale
                    }
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.35f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Pausado",
                    tint = Color.White.copy(alpha = 0.90f),
                    modifier = Modifier.size(46.dp)
                )
            }
        }

        // 3. Degradado Oscuro Inferior Sutil y Transparente (Cero sobrecarga de overdraw, textos perfectamente legibles)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.60f))
                    )
                )
        )

        // 4. Riel Lateral Derecho de Acciones Calcado a TikTok (Avatar +, Like, Comentario, Guardar, Compartir, Disco)
        val isDefaultClean = uiMode == TikTokUiMode.DEFAULT_CLEAN
        val avatarSize = if (isDefaultClean) 38.dp else 48.dp
        val followSize = if (isDefaultClean) 18.dp else 22.dp
        val actionButtonSize = if (isDefaultClean) 32.dp else 40.dp
        val rightRailAlpha = if (isDefaultClean) 0.78f else 1.0f

        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(
                    end = if (isDefaultClean) 8.dp else 10.dp,
                    bottom = if (isDefaultClean) 14.dp else 22.dp
                ),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(if (isDefaultClean) 11.dp else 16.dp)
        ) {
            // A. Avatar con Botón de Seguir (+)
            val followScale by animateFloatAsState(
                targetValue = if (item.isFollowing) 0.92f else 1f,
                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                label = "followScale"
            )
            val followBgColor by animateColorAsState(
                targetValue = if (item.isFollowing) Color.White.copy(alpha = 0.35f) else TikTokPink,
                animationSpec = tween(200),
                label = "followBg"
            )

            Box(
                contentAlignment = Alignment.BottomCenter,
                modifier = Modifier
                    .padding(bottom = if (isDefaultClean) 4.dp else 8.dp)
                    .alpha(rightRailAlpha)
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(item.authorAvatarUrl)
                        .crossfade(150)
                        .memoryCachePolicy(CachePolicy.ENABLED)
                        .diskCachePolicy(CachePolicy.ENABLED)
                        .build(),
                    contentDescription = item.authorName,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(avatarSize)
                        .clip(CircleShape)
                        .border(1.dp, Color.White.copy(alpha = 0.85f), CircleShape)
                        .background(Color(0xFF1E1E1E))
                )

                // Botón '+' de Follow TikTok con micro-animación elástica
                Box(
                    modifier = Modifier
                        .offset(y = if (isDefaultClean) 7.dp else 10.dp)
                        .size(followSize)
                        .graphicsLayer {
                            scaleX = followScale
                            scaleY = followScale
                        }
                        .clip(CircleShape)
                        .background(followBgColor)
                        .clickable { onToggleFollow() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (item.isFollowing) Icons.Default.Check else Icons.Default.Add,
                        contentDescription = "Seguir",
                        tint = Color.White,
                        modifier = Modifier.size(if (isDefaultClean) 11.dp else 14.dp)
                    )
                }
            }

            // B. Corazón de Like (Micro-animación elástica por GPU al dar like)
            val likeScale by animateFloatAsState(
                targetValue = if (item.isLiked) 1.25f else 1f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessLow
                ),
                label = "likeScale"
            )

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.alpha(rightRailAlpha)
            ) {
                IconButton(
                    onClick = onToggleLike,
                    modifier = Modifier.size(actionButtonSize)
                ) {
                    Icon(
                        imageVector = Icons.Default.Favorite,
                        contentDescription = "Like",
                        tint = if (item.isLiked) TikTokPink else TikTokIconWhite,
                        modifier = Modifier
                            .size(if (isDefaultClean) 26.dp else 34.dp)
                            .graphicsLayer {
                                scaleX = likeScale
                                scaleY = likeScale
                            }
                    )
                }
                Text(
                    text = formatCount(item.likesCount),
                    color = Color.White,
                    fontSize = if (isDefaultClean) 11.sp else 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            // C. Burbuja de Comentarios (Sólo en UI Actual; eliminada en UI Default)
            if (!isDefaultClean) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    IconButton(
                        onClick = { /* Comentarios */ },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ChatBubble,
                            contentDescription = "Comentarios",
                            tint = TikTokIconWhite,
                            modifier = Modifier.size(31.dp)
                        )
                    }
                    Text(
                        text = formatCount(item.commentsCount),
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // D. Marcador Guardar / Favoritos con rebote elástico fluido
            val bookmarkScale by animateFloatAsState(
                targetValue = if (item.isBookmarked) 1.25f else 1f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessLow
                ),
                label = "bookmarkScale"
            )

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.alpha(rightRailAlpha)
            ) {
                IconButton(
                    onClick = onToggleBookmark,
                    modifier = Modifier.size(actionButtonSize)
                ) {
                    Icon(
                        imageVector = Icons.Default.Bookmark,
                        contentDescription = "Guardar",
                        tint = if (item.isBookmarked) TikTokBookmarkYellow else TikTokIconWhite,
                        modifier = Modifier
                            .size(if (isDefaultClean) 25.dp else 33.dp)
                            .graphicsLayer {
                                scaleX = bookmarkScale
                                scaleY = bookmarkScale
                            }
                    )
                }
                Text(
                    text = formatCount(item.bookmarksCount),
                    color = Color.White,
                    fontSize = if (isDefaultClean) 11.sp else 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            // E. Compartir (Micro-animación táctil al pulsar)
            val shareInteraction = remember { MutableInteractionSource() }
            val isSharePressed by shareInteraction.collectIsPressedAsState()
            val shareScale by animateFloatAsState(
                targetValue = if (isSharePressed) 0.86f else 1f,
                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                label = "shareScale"
            )

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.alpha(rightRailAlpha)
            ) {
                IconButton(
                    onClick = {
                        onShare()
                        val shareUrl = item.videoUrl.ifEmpty { item.pageUrl.ifEmpty { "https://all18.app/v/${item.id}" } }
                        val sendIntent = Intent(Intent.ACTION_SEND).apply {
                            putExtra(Intent.EXTRA_TEXT, "Mira este video en All18: $shareUrl")
                            type = "text/plain"
                        }
                        val shareIntent = Intent.createChooser(sendIntent, "Compartir video")
                        try {
                            context.startActivity(shareIntent)
                        } catch (_: Exception) {}
                        try {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                            val clip = android.content.ClipData.newPlainText("All18 Video Link", shareUrl)
                            clipboard?.setPrimaryClip(clip)
                            Toast.makeText(context, "Enlace copiado al portapapeles", Toast.LENGTH_SHORT).show()
                        } catch (_: Exception) {}
                    },
                    interactionSource = shareInteraction,
                    modifier = Modifier.size(actionButtonSize)
                ) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = "Compartir",
                        tint = TikTokIconWhite,
                        modifier = Modifier
                            .size(if (isDefaultClean) 24.dp else 30.dp)
                            .graphicsLayer {
                                scaleX = shareScale
                                scaleY = shareScale
                            }
                    )
                }
                Text(
                    text = formatCount(item.sharesCount),
                    color = Color.White,
                    fontSize = if (isDefaultClean) 11.sp else 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            // F. Disco de Vinilo Giratorio TikTok (Sólo en UI Actual; eliminado en UI Default)
            if (!isDefaultClean) {
                val infiniteTransition = rememberInfiniteTransition(label = "vinyl_disc")
                val rotation by infiniteTransition.animateFloat(
                    initialValue = 0f,
                    targetValue = 360f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(4000, easing = LinearEasing),
                        repeatMode = RepeatMode.Restart
                    ),
                    label = "disc_rotation"
                )

                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF141414))
                        .border(1.5.dp, Color(0xFF282828), CircleShape)
                        .rotate(if (isPlaying && isActive) rotation else 0f),
                    contentAlignment = Alignment.Center
                ) {
                    AsyncImage(
                        model = item.authorAvatarUrl,
                        contentDescription = null,
                        modifier = Modifier
                            .size(26.dp)
                            .clip(CircleShape)
                            .border(1.dp, Color(0xFF333333), CircleShape)
                    )
                }
            }
        }

        // 5. Superposición Inferior Izquierda: Creador @handle, Descripción con Hashtags
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth(if (isDefaultClean) 0.83f else 0.78f)
                .padding(
                    start = if (isDefaultClean) 12.dp else 14.dp,
                    bottom = if (isDefaultClean) 10.dp else 22.dp
                )
        ) {
            // Usuario @handle en negrita (más compacto en default)
            Text(
                text = "@${item.authorHandle.removePrefix("@")}",
                color = Color.White,
                fontSize = if (isDefaultClean) 13.5.sp else 16.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(if (isDefaultClean) 2.dp else 6.dp))

            // Descripción con hashtags resaltados y "...más" (más compacta en default)
            val annotatedDesc = remember(item.description) {
                buildAnnotatedString {
                    val words = item.description.split(" ")
                    words.forEachIndexed { idx, word ->
                        if (word.startsWith("#") || word.startsWith("@")) {
                            withStyle(style = SpanStyle(color = Color.White, fontWeight = FontWeight.Bold)) {
                                append(word)
                            }
                        } else {
                            withStyle(style = SpanStyle(color = Color.White.copy(alpha = 0.95f))) {
                                append(word)
                            }
                        }
                        if (idx < words.size - 1) append(" ")
                    }
                    withStyle(style = SpanStyle(color = Color.White.copy(alpha = 0.6f), fontWeight = FontWeight.Bold)) {
                        append(" ...más")
                    }
                }
            }

            Text(
                text = annotatedDesc,
                fontSize = if (isDefaultClean) 12.sp else 14.sp,
                maxLines = if (isDefaultClean) 2 else 3,
                overflow = TextOverflow.Ellipsis,
                lineHeight = if (isDefaultClean) 16.sp else 19.sp
            )

            // "Ver traducción" (Sólo en UI Actual; eliminada en UI Default)
            if (!isDefaultClean) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Ver traducción",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            // Pista de Audio con nota musical (Sólo en UI Actual; eliminada en UI Default)
            if (!isDefaultClean) {
                Spacer(modifier = Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.MusicNote,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = item.musicTitle,
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        // 6. Barra de Progreso Inferior Sincronizada con Reproduccion Real: ━━━●━━━━━━━━━━━━
        if (!item.isPhotoPost) {
            BoxWithConstraints(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(14.dp)
                    .pointerInput(durationMs, isFirstFrameRendered) {
                        detectTapGestures { tapOffset ->
                            if (durationMs > 0L && isFirstFrameRendered) {
                                val fraction = (tapOffset.x / size.width.toFloat()).coerceIn(0f, 1f)
                                val seekPos = (fraction * durationMs).toLong()
                                player?.seekTo(seekPos)
                                currentPositionMs = seekPos
                            }
                        }
                    },
                contentAlignment = Alignment.CenterStart
            ) {
                val totalWidth = maxWidth
                // Progreso estrictamente sincronizado con ExoPlayer; si no ha renderizado frame o esta cargando, es 0f
                val currentProgress = if (durationMs > 0L && isFirstFrameRendered && isActive) {
                    (currentPositionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
                } else {
                    0f
                }

                // Linea base gris semitransparente (ultra ligera)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.dp)
                        .background(Color.White.copy(alpha = 0.20f))
                )

                // Linea de progreso activa en blanco
                Box(
                    modifier = Modifier
                        .fillMaxWidth(currentProgress)
                        .height(2.dp)
                        .background(Color.White.copy(alpha = 0.95f))
                )

                // Punto Scrubber TikTok (●) al final del progreso con desvanecimiento fluido
                val thumbAlpha by animateFloatAsState(
                    targetValue = if (isFirstFrameRendered && currentProgress > 0.001f) 1f else 0f,
                    animationSpec = tween(250),
                    label = "thumbAlpha"
                )
                if (thumbAlpha > 0.01f) {
                    val thumbOffset = (totalWidth * currentProgress) - 3.dp
                    Box(
                        modifier = Modifier
                            .offset(x = thumbOffset.coerceAtLeast(0.dp))
                            .size(6.dp)
                            .graphicsLayer { alpha = thumbAlpha }
                            .clip(CircleShape)
                            .background(Color.White)
                    )
                }
            }
        }

        // 7. Particulas de Doble Toque (Corazones Voladores TikTok)
        heartParticles.forEach { particle ->
            KeyframeHeartParticle(
                particle = particle,
                onFinished = {
                    heartParticles = heartParticles.filterNot { it.id == particle.id }
                }
            )
        }
    }
}

@Composable
private fun TikTokPhotoImage(
    photoUrl: String,
    title: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var isLandscape by remember(photoUrl) { mutableStateOf<Boolean?>(null) }
    var isRotated by remember(photoUrl) { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        // 1. Fondo ambiental cinematografico desenfocado para fotos horizontales
        if (isLandscape == true && !isRotated) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(photoUrl)
                    .crossfade(true)
                    .memoryCachePolicy(CachePolicy.ENABLED)
                    .diskCachePolicy(CachePolicy.ENABLED)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(0.20f)
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.60f))
            )
        }

        // 2. Imagen Principal en calidad 1080p completa (Size.ORIGINAL) y orientacion inteligente
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(photoUrl)
                .size(Size.ORIGINAL)
                .crossfade(150)
                .memoryCachePolicy(CachePolicy.ENABLED)
                .diskCachePolicy(CachePolicy.ENABLED)
                .build(),
            contentDescription = title,
            contentScale = if (isLandscape == true && !isRotated) ContentScale.Fit else ContentScale.Crop,
            onSuccess = { state ->
                val w = state.painter.intrinsicSize.width
                val h = state.painter.intrinsicSize.height
                if (w > 0 && h > 0) {
                    isLandscape = (w > h * 1.05f)
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .rotate(if (isRotated) 90f else 0f)
                .scale(if (isRotated) 1.78f else 1f)
        )

        // 3. Insignia tactil de giro / visualizacion horizontal HD (Estilo TikTok)
        if (isLandscape == true) {
            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(top = 84.dp, end = 16.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.Black.copy(alpha = 0.7f))
                    .border(0.5.dp, Color.White.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
                    .clickable { isRotated = !isRotated }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.ScreenRotation,
                    contentDescription = "Girar",
                    tint = Color.White,
                    modifier = Modifier.size(13.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = if (isRotated) "Normal" else "Horizontal 1080p",
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun KeyframeHeartParticle(
    particle: HeartTapParticle,
    onFinished: () -> Unit
) {
    val scale = remember { Animatable(0.4f) }
    val alpha = remember { Animatable(1f) }
    val translateY = remember { Animatable(0f) }

    LaunchedEffect(particle.id) {
        // Pop & float animation
        scale.animateTo(
            targetValue = 1.25f,
            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)
        )
        translateY.animateTo(
            targetValue = -70f,
            animationSpec = tween(400, easing = FastOutSlowInEasing)
        )
        alpha.animateTo(
            targetValue = 0f,
            animationSpec = tween(250, easing = LinearEasing)
        )
        onFinished()
    }

    Box(
        modifier = Modifier
            .size(80.dp)
            .graphicsLayer {
                translationX = particle.offset.x - 40
                translationY = particle.offset.y - 40 + translateY.value
                scaleX = scale.value
                scaleY = scale.value
                rotationZ = particle.rotation
                this.alpha = alpha.value
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.Favorite,
            contentDescription = null,
            tint = TikTokPink,
            modifier = Modifier.fillMaxSize()
        )
    }
}

/**
 * Formateo numérico corto estilo TikTok (ej. "1.5k", "3k", "12.5k", "1.2M", sin comas)
 */
private fun formatCount(count: Int): String {
    return when {
        count >= 1_000_000 -> {
            val v = count / 1_000_000.0
            if (count % 1_000_000 == 0) "${count / 1_000_000}M"
            else String.format(Locale.US, "%.1fM", v).replace(".0M", "M")
        }
        count >= 1_000 -> {
            val v = count / 1_000.0
            if (count % 1_000 == 0) "${count / 1_000}k"
            else String.format(Locale.US, "%.1fk", v).replace(".0k", "k")
        }
        else -> count.toString()
    }
}

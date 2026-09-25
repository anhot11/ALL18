@file:kotlin.OptIn(
    androidx.media3.common.util.UnstableApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class
)

package com.all18.nativeapp.ui.xfeed

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.all18.nativeapp.core.model.StreamQuality
import com.all18.nativeapp.core.model.VideoItem
import com.all18.nativeapp.core.model.XPostItem
import com.all18.nativeapp.core.player.SilentPlayerManager
import com.all18.nativeapp.core.repository.MultiSourceRepository
import com.all18.nativeapp.core.repository.UserLibraryRepository
import com.all18.nativeapp.ui.theme.PornhubOrange
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

private val XBlue = Color(0xFF1D9BF0)
private val XTextSecondary = Color(0xFF71767B)
private val XHeartPink = Color(0xFFF91880)
private val XRepostGreen = Color(0xFF00BA7C)
private val XTranslucentBg = Color.Black.copy(alpha = 0.55f)

@OptIn(UnstableApi::class, ExperimentalMaterial3Api::class)
@Composable
fun XVideoPlayerScreen(
    videoPosts: List<XPostItem>,
    initialIndex: Int,
    onBack: () -> Unit,
    onLoadMore: () -> Unit = {},
    onToggleLike: (String) -> Unit,
    onToggleRepost: (String) -> Unit = {},
    onToggleBookmark: (String) -> Unit
) {
    val pagerState = rememberPagerState(
        initialPage = initialIndex.coerceIn(0, (videoPosts.size - 1).coerceAtLeast(0)),
        pageCount = { videoPosts.size }
    )

    // Paginación continua hacia abajo: cargar más videos antes de llegar al final
    LaunchedEffect(pagerState.currentPage, videoPosts.size) {
        if (videoPosts.isNotEmpty() && pagerState.currentPage >= videoPosts.size - 3) {
            onLoadMore()
        }
    }

    // Estado global de visibilidad de UI: conmuta al tocar la pantalla
    var isUiVisible by remember { mutableStateOf(true) }

    BackHandler(onBack = onBack)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        VerticalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            val post = videoPosts[page]
            val isCurrentPage = pagerState.currentPage == page

            XSingleVideoPage(
                post = post,
                isCurrentPage = isCurrentPage,
                isUiVisible = isUiVisible,
                onToggleUi = { isUiVisible = !isUiVisible },
                onBack = onBack,
                onToggleLike = { onToggleLike(post.id) },
                onToggleRepost = { onToggleRepost(post.id) },
                onToggleBookmark = { onToggleBookmark(post.id) }
            )
        }
    }
}

@OptIn(UnstableApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun XSingleVideoPage(
    post: XPostItem,
    isCurrentPage: Boolean,
    isUiVisible: Boolean,
    onToggleUi: () -> Unit,
    onBack: () -> Unit,
    onToggleLike: () -> Unit,
    onToggleRepost: () -> Unit = {},
    onToggleBookmark: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var isFollowing by remember { mutableStateOf(false) }
    var isExpandedText by remember { mutableStateOf(false) }

    // Likes y Guardados con estado reactivo inmediato y persistencia
    val videoId = post.videoItem?.id ?: post.id
    var isLiked by remember(post.id) {
        mutableStateOf(post.isLiked || UserLibraryRepository.isVideoLiked(videoId))
    }
    var likesCount by remember(post.id) {
        mutableIntStateOf(post.likesCount)
    }
    var isBookmarked by remember(post.id) {
        mutableStateOf(post.isBookmarked || UserLibraryRepository.isVideoSaved(videoId))
    }

    // Calidad de video y opciones
    var showQualitySheet by remember { mutableStateOf(false) }
    var availableQualities by remember(post.id) {
        mutableStateOf<List<StreamQuality>>(
            listOf(
                StreamQuality(label = "Auto"),
                StreamQuality(label = "1080p"),
                StreamQuality(label = "720p"),
                StreamQuality(label = "480p"),
                StreamQuality(label = "360p")
            )
        )
    }
    var selectedQuality by remember(post.id) { mutableStateOf(availableQualities.first()) }
    var currentStreamUrl by remember(post.id) { mutableStateOf("") }

    // Reproductor ExoPlayer dedicado y silenciado para esta página
    var isPlaying by remember { mutableStateOf(true) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var currentPosMs by remember { mutableLongStateOf(0L) }
    var playbackSpeed by remember { mutableFloatStateOf(1f) }
    var isMuted by remember { mutableStateOf(true) } // Siempre silenciado (0 dB)
    var isScrubbing by remember { mutableStateOf(false) }
    var scrubPosMs by remember { mutableLongStateOf(0L) }

    val exoPlayer = remember {
        SilentPlayerManager.createSilentPlayer(context).apply {
            playWhenReady = true
        }
    }

    // Aplicar selección de calidad
    val applyQuality: (StreamQuality) -> Unit = { quality ->
        selectedQuality = quality
        showQualitySheet = false
        Toast.makeText(context, "Calidad: ${quality.label}", Toast.LENGTH_SHORT).show()

        if (quality.isHlsTrack) {
            val builder = exoPlayer.trackSelectionParameters.buildUpon()
            if (quality.label == "Auto") {
                builder.clearVideoSizeConstraints()
                builder.clearOverridesOfType(C.TRACK_TYPE_VIDEO)
            } else {
                builder.setMaxVideoSize(quality.width, quality.height)
                builder.setMinVideoSize(quality.width, quality.height)
            }
            exoPlayer.trackSelectionParameters = builder.build()
        } else if (quality.url.isNotEmpty() && quality.url != currentStreamUrl) {
            val curPos = exoPlayer.currentPosition
            val wasPlaying = exoPlayer.isPlaying
            currentStreamUrl = quality.url
            exoPlayer.setMediaItem(MediaItem.fromUri(quality.url), curPos)
            exoPlayer.prepare()
            if (wasPlaying) exoPlayer.play()
        } else {
            val targetHeight = quality.label.filter { it.isDigit() }.toIntOrNull()
            val builder = exoPlayer.trackSelectionParameters.buildUpon()
            if (targetHeight != null && targetHeight > 0) {
                builder.setMaxVideoSize(Int.MAX_VALUE, targetHeight)
                builder.setMinVideoSize(0, targetHeight)
            } else {
                builder.clearVideoSizeConstraints()
                builder.clearOverridesOfType(C.TRACK_TYPE_VIDEO)
            }
            exoPlayer.trackSelectionParameters = builder.build()
        }
    }

    DisposableEffect(post.id) {
        var isDisposed = false
        if (post.videoItem != null) {
            UserLibraryRepository.addToHistory(post.videoItem)
            scope.launch {
                val url = MultiSourceRepository.extractStreamUrl(post.videoItem)
                if (!isDisposed && url.isNotEmpty()) {
                    currentStreamUrl = url
                    exoPlayer.setMediaItem(MediaItem.fromUri(url))
                    exoPlayer.prepare()
                    if (isCurrentPage) {
                        exoPlayer.play()
                    }
                }
                val discreteQualities = MultiSourceRepository.extractStreamQualities(post.videoItem)
                if (!isDisposed && discreteQualities.isNotEmpty()) {
                    val list = mutableListOf<StreamQuality>()
                    list.add(StreamQuality(label = "Auto", url = url))
                    list.addAll(discreteQualities)
                    availableQualities = list
                }
            }
        }

        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    durationMs = exoPlayer.duration.coerceAtLeast(0L)
                }
            }
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
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
            isDisposed = true
            exoPlayer.removeListener(listener)
            exoPlayer.stop()
            exoPlayer.release()
        }
    }

    // Control de reproducción según la página activa
    LaunchedEffect(isCurrentPage) {
        if (isCurrentPage) {
            exoPlayer.play()
        } else {
            exoPlayer.pause()
        }
    }

    // Actualizador de progreso del video (ignora cuando el usuario está arrastrando la barra)
    LaunchedEffect(isCurrentPage, isPlaying, isScrubbing) {
        while (isCurrentPage && isPlaying && !isScrubbing) {
            currentPosMs = exoPlayer.currentPosition.coerceAtLeast(0L)
            durationMs = exoPlayer.duration.coerceAtLeast(0L)
            delay(250)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                detectTapGestures(onTap = { onToggleUi() })
            }
    ) {
        // 1. REPRODUCTOR DE VIDEO NATIVO O MINIATURA
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = false
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // 2. CAPA DE GRADIENTE PARA CONTRASTE DE TEXTO (Sólo visible cuando la UI está activa)
        AnimatedVisibility(
            visible = isUiVisible,
            enter = fadeIn(tween(250)),
            exit = fadeOut(tween(250)),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.6f),
                                Color.Transparent,
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.85f)
                            )
                        )
                    )
            )
        }

        // 3. BARRA SUPERIOR (Captura 1): Botón Volver + Menú Más Opciones
        AnimatedVisibility(
            visible = isUiVisible,
            enter = fadeIn(tween(250)) + slideInVertically(initialOffsetY = { -it }),
            exit = fadeOut(tween(250)) + slideOutVertically(targetOffsetY = { -it }),
            modifier = Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth()
                .padding(top = 16.dp, start = 14.dp, end = 14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Botón Volver (Círculo translúcido)
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(XTranslucentBg)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Volver",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Menú ⋮ Más Opciones (Círculo translúcido)
                IconButton(
                    onClick = {
                        showQualitySheet = true
                    },
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(XTranslucentBg)
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "Calidad y opciones",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        // 4. CAPA INFERIOR CALCADA A LA CAPTURA 1 DE X
        AnimatedVisibility(
            visible = isUiVisible,
            enter = fadeIn(tween(250)) + slideInVertically(initialOffsetY = { it }),
            exit = fadeOut(tween(250)) + slideOutVertically(targetOffsetY = { it }),
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // A. Fila de Autor: Avatar + Nombre + Verificado Azul + @handle + Botón 'Seguir'
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(post.authorAvatarUrl)
                            .crossfade(true)
                            .build(),
                        contentDescription = post.authorName,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF161616))
                    )

                    Spacer(modifier = Modifier.width(10.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = post.authorName,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (post.isVerified) {
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = "Verificado",
                                    tint = XBlue,
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                        }
                        Text(
                            text = "@${post.authorHandle.removePrefix("@")}",
                            color = XTextSecondary,
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // Botón Seguir / Siguiendo (Píldora blanca)
                    Button(
                        onClick = {
                            isFollowing = !isFollowing
                            val msg = if (isFollowing) "Siguiendo a @${post.authorHandle}" else "Dejaste de seguir"
                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isFollowing) Color.Transparent else Color.White,
                            contentColor = if (isFollowing) Color.White else Color.Black
                        ),
                        border = if (isFollowing) androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF555555)) else null,
                        shape = RoundedCornerShape(20.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                        modifier = Modifier.height(34.dp)
                    ) {
                        Text(
                            text = if (isFollowing) "Siguiendo" else "Seguir",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // B. Contenido del Tweet con hashtags y 'Mostrar más'
                val annotatedText = remember(post.content, isExpandedText) {
                    buildAnnotatedString {
                        val displayText = if (isExpandedText || post.content.length <= 150) {
                            post.content
                        } else {
                            post.content.take(130) + "..."
                        }
                        val words = displayText.split(" ")
                        words.forEachIndexed { idx, word ->
                            if (word.startsWith("#") || word.startsWith("@")) {
                                withStyle(style = SpanStyle(color = XBlue, fontWeight = FontWeight.Normal)) {
                                    append(word)
                                }
                            } else {
                                withStyle(style = SpanStyle(color = Color.White)) {
                                    append(word)
                                }
                            }
                            if (idx < words.size - 1) append(" ")
                        }
                    }
                }

                Text(
                    text = annotatedText,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    color = Color.White
                )

                if (post.content.length > 150) {
                    Text(
                        text = if (isExpandedText) "Mostrar menos" else "Mostrar más",
                        color = XBlue,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .clickable { isExpandedText = !isExpandedText }
                            .padding(top = 2.dp)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // C. Barra de Interacción (❤️ Me Gusta | 🔖 Guardar | ➦ Compartir Link)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 1. Me Gusta (❤️)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(XTranslucentBg)
                            .clickable {
                                isLiked = !isLiked
                                likesCount = if (isLiked) likesCount + 1 else (likesCount - 1).coerceAtLeast(0)
                                onToggleLike()
                                val video = post.videoItem ?: VideoItem(
                                    id = post.id,
                                    title = post.content.take(80),
                                    thumbUrl = post.images.firstOrNull() ?: post.authorAvatarUrl,
                                    pageUrl = post.videoItem?.pageUrl ?: "",
                                    durationText = "𝕏 Post",
                                    author = post.authorName,
                                    source = "𝕏",
                                    isFavorite = isLiked
                                )
                                UserLibraryRepository.setVideoLiked(video, isLiked)
                            }
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Icon(
                            imageVector = if (isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = "Me gusta",
                            tint = if (isLiked) XHeartPink else Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = formatCount(likesCount),
                            color = if (isLiked) XHeartPink else Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // 2. Guardar / Marcador (🔖)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(XTranslucentBg)
                            .clickable {
                                isBookmarked = !isBookmarked
                                onToggleBookmark()
                                val video = post.videoItem ?: VideoItem(
                                    id = post.id,
                                    title = post.content.take(80),
                                    thumbUrl = post.images.firstOrNull() ?: post.authorAvatarUrl,
                                    pageUrl = post.videoItem?.pageUrl ?: "",
                                    durationText = "𝕏 Post",
                                    author = post.authorName,
                                    source = "𝕏"
                                )
                                UserLibraryRepository.setVideoSaved(video, isBookmarked)
                                Toast.makeText(
                                    context,
                                    if (isBookmarked) "Guardado en marcadores" else "Eliminado de marcadores",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Icon(
                            imageVector = if (isBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                            contentDescription = "Guardar",
                            tint = if (isBookmarked) XBlue else Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isBookmarked) "Guardado" else "Guardar",
                            color = if (isBookmarked) XBlue else Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    // 3. Compartir Link (➦)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(XTranslucentBg)
                            .clickable {
                                val videoUrl = post.videoItem?.pageUrl?.takeIf { it.isNotBlank() }
                                    ?: post.videoItem?.thumbUrl?.takeIf { it.isNotBlank() }
                                    ?: "https://x.com/${post.authorHandle.removePrefix("@")}/status/${post.id}"

                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clip = ClipData.newPlainText("Video URL", videoUrl)
                                clipboard.setPrimaryClip(clip)
                                Toast.makeText(context, "Enlace copiado: $videoUrl", Toast.LENGTH_LONG).show()

                                try {
                                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_SUBJECT, post.content.take(50))
                                        putExtra(Intent.EXTRA_TEXT, "Mira este video en X: $videoUrl")
                                    }
                                    context.startActivity(Intent.createChooser(shareIntent, "Compartir enlace del video"))
                                } catch (e: Exception) {
                                    // Clipboard ya se copió
                                }
                            }
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "Compartir",
                            tint = Color.White,
                            modifier = Modifier.size(19.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Compartir",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // D. Barra de Progreso Fina e Interactiva (Permite buscar tiempo arrastrando o tocando sin agrandar el tamaño visual)
                val effectivePosMs = if (isScrubbing) scrubPosMs else currentPosMs
                val progressFraction = if (durationMs > 0) (effectivePosMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) else 0f

                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(24.dp)
                        .pointerInput(durationMs) {
                            detectTapGestures(
                                onPress = { offset ->
                                    if (durationMs > 0) {
                                        isScrubbing = true
                                        val fraction = (offset.x / size.width.toFloat()).coerceIn(0f, 1f)
                                        val targetMs = (fraction * durationMs).toLong()
                                        scrubPosMs = targetMs
                                        currentPosMs = targetMs
                                        exoPlayer.seekTo(targetMs)
                                        tryAwaitRelease()
                                        isScrubbing = false
                                    }
                                }
                            )
                        }
                        .pointerInput(durationMs) {
                            detectHorizontalDragGestures(
                                onDragStart = { offset ->
                                    if (durationMs > 0) {
                                        isScrubbing = true
                                        val fraction = (offset.x / size.width.toFloat()).coerceIn(0f, 1f)
                                        val targetMs = (fraction * durationMs).toLong()
                                        scrubPosMs = targetMs
                                        currentPosMs = targetMs
                                    }
                                },
                                onDragEnd = {
                                    if (durationMs > 0 && isScrubbing) {
                                        exoPlayer.seekTo(scrubPosMs)
                                        currentPosMs = scrubPosMs
                                        isScrubbing = false
                                    }
                                },
                                onDragCancel = {
                                    isScrubbing = false
                                },
                                onHorizontalDrag = { change, _ ->
                                    change.consume()
                                    if (durationMs > 0) {
                                        val fraction = (change.position.x / size.width.toFloat()).coerceIn(0f, 1f)
                                        val targetMs = (fraction * durationMs).toLong()
                                        scrubPosMs = targetMs
                                        currentPosMs = targetMs
                                        exoPlayer.seekTo(targetMs)
                                    }
                                }
                            )
                        },
                    contentAlignment = Alignment.CenterStart
                ) {
                    val trackWidth = maxWidth

                    // 1. Pista inactiva de fondo fina (3dp)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(if (isScrubbing) 4.dp else 3.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(Color(0xFF333333))
                    )

                    // 2. Pista activa blanca fina (3dp)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(progressFraction)
                            .height(if (isScrubbing) 4.dp else 3.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(Color.White)
                    )

                    // 3. Indicador circular (Thumb) sutil que se amplía al tocar
                    val thumbSize = if (isScrubbing) 12.dp else 7.dp
                    val thumbOffset = ((trackWidth * progressFraction) - (thumbSize / 2)).coerceAtLeast(0.dp)
                    Box(
                        modifier = Modifier
                            .offset(x = thumbOffset)
                            .size(thumbSize)
                            .clip(CircleShape)
                            .background(Color.White)
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                // E. Controles Inferiores de Reproducción (Play/Pause, Tiempo, Velocidad, Mute, Fullscreen, Descarga)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Play / Pause (❚❚ / ▶)
                    IconButton(
                        onClick = {
                            if (isPlaying) {
                                exoPlayer.pause()
                            } else {
                                exoPlayer.play()
                            }
                        },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) "Pausar" else "Reproducir",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    // Tiempo Restante reactivo (ej. "-3:43")
                    val remainingMs = (durationMs - effectivePosMs).coerceAtLeast(0L)
                    val remainingStr = formatTime(remainingMs)
                    Text(
                        text = "-$remainingStr",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )

                    // Velocidad de Reproducción ("1x")
                    Text(
                        text = "${playbackSpeed.toInt()}x",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clickable {
                                playbackSpeed = when (playbackSpeed) {
                                    1f -> 1.5f
                                    1.5f -> 2f
                                    else -> 1f
                                }
                                exoPlayer.setPlaybackSpeed(playbackSpeed)
                            }
                            .padding(4.dp)
                    )

                    // Icono de Altavoz / Mute (Estrictamente silencioso - regla de 0 dB)
                    IconButton(
                        onClick = {
                            // Mantenemos 0 dB estrictamente
                            Toast.makeText(context, "Modo silencioso protegido (0 dB)", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.VolumeOff,
                            contentDescription = "Silencio 0dB",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Pantalla Completa / Expandir
                    IconButton(
                        onClick = {
                            onToggleUi()
                        },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Fullscreen,
                            contentDescription = "Pantalla completa",
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    // Botón Descargar (Como en TikTok)
                    IconButton(
                        onClick = {
                            Toast.makeText(context, "Descargando video...", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.FileDownload,
                            contentDescription = "Descargar video",
                            tint = Color.White,
                            modifier = Modifier.size(21.dp)
                        )
                    }
                }
            }
        }

        // 5. MENÚ INFERIOR DE CALIDAD Y AJUSTES (3 PUNTOS)
        if (showQualitySheet) {
            ModalBottomSheet(
                onDismissRequest = { showQualitySheet = false },
                containerColor = Color(0xFF16181C),
                dragHandle = { BottomSheetDefaults.DragHandle(color = Color(0xFF71767B)) }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 8.dp)
                        .navigationBarsPadding()
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.HighQuality,
                            contentDescription = null,
                            tint = XBlue,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Calidad de reproducción",
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    availableQualities.forEach { quality ->
                        val isSelected = quality.label == selectedQuality.label
                        Surface(
                            color = if (isSelected) XBlue.copy(alpha = 0.18f) else Color.Transparent,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { applyQuality(quality) }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = when (quality.label) {
                                        "Auto" -> "Auto (Mejor disponible)"
                                        "1080p" -> "1080p Full HD"
                                        "720p" -> "720p HD"
                                        "480p" -> "480p SD"
                                        "360p" -> "360p Ahorro de datos"
                                        else -> quality.label
                                    },
                                    color = if (isSelected) XBlue else Color.White,
                                    fontSize = 15.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Seleccionada",
                                        tint = XBlue,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider(color = Color(0xFF2F3336), thickness = 0.5.dp)
                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Speed,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Velocidad de reproducción",
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        listOf(0.5f, 1.0f, 1.25f, 1.5f, 2.0f).forEach { speed ->
                            val isSpeedSelected = playbackSpeed == speed
                            Surface(
                                color = if (isSpeedSelected) XBlue else Color(0xFF24272C),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .clickable {
                                        playbackSpeed = speed
                                        exoPlayer.setPlaybackSpeed(speed)
                                        showQualitySheet = false
                                        Toast.makeText(context, "Velocidad: ${speed}x", Toast.LENGTH_SHORT).show()
                                    }
                            ) {
                                Text(
                                    text = "${speed}x",
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = if (isSpeedSelected) FontWeight.Bold else FontWeight.Normal,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }
}

private fun formatCount(count: Int): String {
    return when {
        count >= 1_000_000 -> String.format(Locale.US, "%.1fM", count / 1_000_000.0)
        count >= 1_000 -> String.format(Locale.US, "%.1fK", count / 1_000.0)
        else -> count.toString()
    }
}

private fun formatTime(millis: Long): String {
    val totalSeconds = millis / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format(Locale.US, "%d:%02d", minutes, seconds)
}

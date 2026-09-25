package com.all18.nativeapp.ui.tiktok

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.all18.nativeapp.core.model.TikTokLiveItem
import com.all18.nativeapp.core.player.SilentPlayerManager
import kotlinx.coroutines.delay
import java.util.Locale
import kotlin.math.roundToInt
import kotlin.random.Random

private val TikTokPink = Color(0xFFFE2C55)

private data class LiveHeartParticle(
    val id: Long,
    val initialOffset: Offset,
    val color: Color,
    val sizeDp: Float
)

@OptIn(UnstableApi::class)
@kotlin.OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun TikTokLiveScreen(
    viewModel: TikTokViewModel,
    onExitLive: () -> Unit
) {
    BackHandler {
        onExitLive()
    }

    val context = LocalContext.current
    val liveRooms by viewModel.liveRooms.collectAsState()
    val isLiveLoading by viewModel.isLiveLoading.collectAsState()

    LaunchedEffect(Unit) {
        if (liveRooms.isEmpty()) {
            viewModel.loadLiveRooms()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        if (isLiveLoading && liveRooms.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = TikTokPink, modifier = Modifier.size(36.dp))
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = "Conectando a directos en vivo...",
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 13.sp
                    )
                }
            }
        } else if (liveRooms.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                    Text(
                        text = "No se encontraron transmisiones activas en este momento.",
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Button(
                        onClick = { viewModel.loadLiveRooms() },
                        colors = ButtonDefaults.buttonColors(containerColor = TikTokPink)
                    ) {
                        Text("Reintentar", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        } else {
            val pagerState = rememberPagerState(
                initialPage = viewModel.currentLiveIndex.coerceIn(0, liveRooms.size - 1),
                pageCount = { liveRooms.size }
            )

            LaunchedEffect(pagerState.currentPage) {
                viewModel.onLivePageSelected(pagerState.currentPage)
            }

            VerticalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                val room = liveRooms[page]
                val isActive = pagerState.currentPage == page
                val streamUrl = viewModel.liveStreamUrls[room.id]

                TikTokLiveRoomPage(
                    room = room,
                    isActive = isActive,
                    streamUrl = streamUrl,
                    onToggleFollow = { viewModel.toggleFollowLive(room.username) },
                    onSendGift = { giftName ->
                        viewModel.sendLiveGift(room.id, giftName)
                        Toast.makeText(context, "¡Enviaste $giftName a ${room.displayName}!", Toast.LENGTH_SHORT).show()
                    },
                    onAddHeart = { viewModel.addLiveHeart(room.id) },
                    onExitLive = onExitLive
                )
            }
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun TikTokLiveRoomPage(
    room: TikTokLiveItem,
    isActive: Boolean,
    streamUrl: String?,
    onToggleFollow: () -> Unit,
    onSendGift: (String) -> Unit,
    onAddHeart: () -> Unit,
    onExitLive: () -> Unit
) {
    val context = LocalContext.current
    val heartParticles = remember { mutableStateListOf<LiveHeartParticle>() }

    // ExoPlayer lifecycle for live stream
    var player: ExoPlayer? by remember { mutableStateOf(null) }
    var isPlayerReady by remember { mutableStateOf(false) }

    DisposableEffect(streamUrl, isActive) {
        if (isActive && !streamUrl.isNullOrBlank()) {
            val exoplayer = SilentPlayerManager.createSilentPlayer(context).apply {
                repeatMode = Player.REPEAT_MODE_OFF
                val mediaItem = MediaItem.fromUri(streamUrl)
                setMediaItem(mediaItem)
                prepare()
                playWhenReady = true
                addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        isPlayerReady = (playbackState == Player.STATE_READY)
                    }
                })
            }
            player = exoplayer

            onDispose {
                exoplayer.release()
                player = null
                isPlayerReady = false
            }
        } else {
            player?.pause()
            onDispose {
                player?.release()
                player = null
                isPlayerReady = false
            }
        }
    }

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        // Capa de Video / Snapshot de fondo con detección de toques para corazones flotantes
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { tapOffset ->
                            onAddHeart()
                            val colors = listOf(TikTokPink, Color(0xFFFF5252), Color(0xFFFF4081), Color(0xFFFF80AB))
                            heartParticles.add(
                                LiveHeartParticle(
                                    id = System.currentTimeMillis() + Random.nextInt(1000),
                                    initialOffset = tapOffset,
                                    color = colors.random(),
                                    sizeDp = Random.nextInt(28, 44).toFloat()
                                )
                            )
                        },
                        onDoubleTap = { tapOffset ->
                            onAddHeart()
                            repeat(3) {
                                val colors = listOf(TikTokPink, Color(0xFFFF1744), Color(0xFFFF80AB))
                                heartParticles.add(
                                    LiveHeartParticle(
                                        id = System.currentTimeMillis() + Random.nextInt(2000),
                                        initialOffset = Offset(
                                            tapOffset.x + Random.nextInt(-40, 40),
                                            tapOffset.y + Random.nextInt(-40, 40)
                                        ),
                                        color = colors.random(),
                                        sizeDp = Random.nextInt(32, 48).toFloat()
                                    )
                                )
                            }
                        }
                    )
                }
        ) {
        // Video de fondo o Snapshot mientras conecta
        if (player != null && !streamUrl.isNullOrBlank()) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        this.player = player
                        useController = false
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                        layoutParams = FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                    }
                },
                update = { view ->
                    if (view.player != player) {
                        view.player = player
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        }

        // Snapshot de la cámara si el reproductor aún está cargando
        if (!isPlayerReady || streamUrl.isNullOrBlank()) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(room.snapshotUrl)
                    .crossfade(true)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )

            // Gradiente oscuro suave para garantizar legibilidad
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.45f),
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.65f)
                            )
                        )
                    )
            )
        }
        }

        // Sistema de Partículas de Corazones Flotantes (Live Hearts)
        heartParticles.forEach { particle ->
            key(particle.id) {
                AnimatedHeart(
                    particle = particle,
                    onAnimationEnd = { heartParticles.remove(particle) }
                )
            }
        }

        // ==========================================
        // CABECERA SUPERIOR MINIMALISTA
        // ==========================================
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Lado Izquierdo: Píldora compacta del Creador
            Row(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.38f))
                    .padding(start = 3.dp, end = 7.dp, top = 3.dp, bottom = 3.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(room.avatarUrl)
                        .crossfade(true)
                        .build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .border(0.8.dp, Color.White.copy(alpha = 0.85f), CircleShape)
                )

                Spacer(modifier = Modifier.width(6.dp))

                Column(modifier = Modifier.widthIn(max = 95.dp)) {
                    Text(
                        text = room.displayName,
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${formatLiveNumber(room.likesCount)} likes",
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 9.sp,
                        maxLines = 1
                    )
                }

                Spacer(modifier = Modifier.width(6.dp))

                // Botón '+' o '✓' estilo TikTok avatar
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(if (room.isFollowing) Color.White.copy(alpha = 0.25f) else TikTokPink)
                        .clickable { onToggleFollow() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (room.isFollowing) Icons.Default.Check else Icons.Default.Add,
                        contentDescription = "Seguir",
                        tint = Color.White,
                        modifier = Modifier.size(12.dp)
                    )
                }
            }

            // Lado Derecho: Contador de Espectadores + Botón Cerrar ✕
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Píldora de espectadores
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.Black.copy(alpha = 0.38f))
                        .padding(horizontal = 7.dp, vertical = 3.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "👥 ${formatLiveNumber(room.viewersCount)}",
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Botón Cerrar ✕
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.38f))
                        .clickable { onExitLive() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Salir de LIVE",
                        tint = Color.White,
                        modifier = Modifier.size(15.dp)
                    )
                }
            }
        }

        // ==========================================
        // BARRA INFERIOR MINIMALISTA (Solo iconos limpios, sin texto innecesario)
        // ==========================================
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 10.dp, vertical = 8.dp)
        ) {
            // Nombre de usuario y descripción sutil (sin cajas negras)
            Column(modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)) {
                Text(
                    text = "@${room.username}",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.labelMedium.copy(
                        shadow = androidx.compose.ui.graphics.Shadow(
                            color = Color.Black.copy(alpha = 0.8f),
                            blurRadius = 4f
                        )
                    )
                )
                if (room.roomSubject.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = room.roomSubject,
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall.copy(
                            shadow = androidx.compose.ui.graphics.Shadow(
                                color = Color.Black.copy(alpha = 0.8f),
                                blurRadius = 4f
                            )
                        )
                    )
                }
            }

            // Fila de acciones compacta: Entrada de texto + Iconos limpios (sin texto abajo)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Cápsula "Añadir comentario..."
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .height(34.dp)
                        .clip(RoundedCornerShape(17.dp))
                        .background(Color.Black.copy(alpha = 0.38f))
                        .border(0.5.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(17.dp))
                        .clickable {
                            Toast.makeText(context, "Chat en vivo activado", Toast.LENGTH_SHORT).show()
                        }
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Añadir comentario...",
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 11.sp
                    )
                }

                // Regalo 🎁 (Icono limpio sin texto)
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.38f))
                        .clickable { onSendGift("Regalo") },
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = "🎁", fontSize = 17.sp)
                }

                // Rosa 🌹 (Icono limpio sin texto)
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.38f))
                        .clickable { onSendGift("Rosa") },
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = "🌹", fontSize = 17.sp)
                }

                // Corazón ❤️ (Icono limpio sin texto)
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.38f))
                        .clickable { onAddHeart() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Favorite,
                        contentDescription = "Me gusta",
                        tint = TikTokPink,
                        modifier = Modifier.size(17.dp)
                    )
                }

                // Compartir ↗ (Icono limpio sin texto)
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.38f))
                        .clickable {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clip = ClipData.newPlainText("Live URL", "https://chaturbate.com/${room.username}/")
                            clipboard.setPrimaryClip(clip)
                            Toast.makeText(context, "Enlace copiado", Toast.LENGTH_SHORT).show()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = "Compartir",
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun AnimatedHeart(
    particle: LiveHeartParticle,
    onAnimationEnd: () -> Unit
) {
    val progress = remember { Animatable(0f) }

    LaunchedEffect(particle.id) {
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 1400, easing = LinearEasing)
        )
        onAnimationEnd()
    }

    val currentVal = progress.value
    val offsetY = -currentVal * 280f
    val offsetX = kotlin.math.sin(currentVal * 6.28f * 1.5f) * 30f
    val alpha = (1f - currentVal).coerceIn(0f, 1f)
    val scale = (0.5f + currentVal * 0.8f).coerceAtMost(1.2f)

    Icon(
        imageVector = Icons.Default.Favorite,
        contentDescription = null,
        tint = particle.color.copy(alpha = alpha),
        modifier = Modifier
            .offset {
                IntOffset(
                    (particle.initialOffset.x + offsetX).roundToInt(),
                    (particle.initialOffset.y + offsetY).roundToInt()
                )
            }
            .scale(scale)
            .size(particle.sizeDp.dp)
    )
}

private fun formatLiveNumber(count: Int): String {
    return when {
        count >= 1_000_000 -> String.format(Locale.US, "%.1fM", count / 1_000_000.0)
        count >= 1_000 -> String.format(Locale.US, "%.1fK", count / 1_000.0)
        else -> count.toString()
    }
}

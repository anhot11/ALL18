package com.all18.nativeapp.ui.profile

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.Coil
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.all18.nativeapp.core.model.VideoItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

private val ProfileOrange = Color(0xFFFF9000)
private val ProfileRed = Color(0xFFFE2C55)
private val ProfileBlue = Color(0xFF1D9BF0)
private val ProfileDivider = Color(0xFF1E1E1E)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    viewModel: ProfileViewModel,
    onVideoClick: (VideoItem) -> Unit,
    onNavigateToAllTube: () -> Unit = {},
    onNavigateToTikTok: () -> Unit = {}
) {
    val likedVideos by viewModel.likedVideos.collectAsState()
    val savedVideos by viewModel.savedVideos.collectAsState()
    val historyVideos by viewModel.historyVideos.collectAsState()
    val selectedTab by viewModel.selectedTab.collectAsState()

    var showSettingsSheet by remember { mutableStateOf(false) }
    var pendingClearTarget by remember { mutableStateOf<ProfileTab?>(null) }
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    // Cuadro de diálogo de confirmación para vaciar listas
    if (pendingClearTarget != null) {
        val tabToClear = pendingClearTarget!!
        AlertDialog(
            onDismissRequest = { pendingClearTarget = null },
            containerColor = Color(0xFF1A1A1A),
            title = {
                Text(
                    text = "¿Vaciar ${tabToClear.title}?",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            },
            text = {
                Text(
                    text = "Se eliminarán todos los videos de esta lista. Esta acción no se puede deshacer.",
                    color = Color(0xFFAAAAAA),
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        when (tabToClear) {
                            ProfileTab.Liked -> viewModel.clearLiked()
                            ProfileTab.Saved -> viewModel.clearSaved()
                            ProfileTab.History -> viewModel.clearHistory()
                        }
                        pendingClearTarget = null
                        Toast.makeText(context, "Lista vaciada", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Text(
                        text = "Vaciar",
                        color = ProfileRed,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingClearTarget = null }) {
                    Text(text = "Cancelar", color = Color.White)
                }
            }
        )
    }

    // ModalBottomSheet para Ajustes & Limpieza de Caché
    if (showSettingsSheet) {
        ModalBottomSheet(
            onDismissRequest = { showSettingsSheet = false },
            containerColor = Color(0xFF141414),
            contentColor = Color.White,
            scrimColor = Color.Black.copy(alpha = 0.75f),
            dragHandle = {
                BottomSheetDefaults.DragHandle(color = Color(0xFF444444))
            }
        ) {
            ProfileSettingsContent(
                likedCount = likedVideos.size,
                savedCount = savedVideos.size,
                historyCount = historyVideos.size,
                onClearCache = {
                    coroutineScope.launch(Dispatchers.IO) {
                        val loader = Coil.imageLoader(context)
                        loader.memoryCache?.clear()
                        loader.diskCache?.clear()
                    }
                    Toast.makeText(context, "Caché de imágenes liberada", Toast.LENGTH_SHORT).show()
                },
                onClearTab = { tab ->
                    showSettingsSheet = false
                    pendingClearTarget = tab
                },
                onClose = { showSettingsSheet = false }
            )
        }
    }

    Scaffold(
        containerColor = Color.Black,
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "@usuario",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Verificado",
                            tint = ProfileBlue,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                },
                actions = {
                    val settingsInteraction = remember { MutableInteractionSource() }
                    val isSettingsPressed by settingsInteraction.collectIsPressedAsState()
                    val settingsScale by animateFloatAsState(
                        targetValue = if (isSettingsPressed) 0.85f else 1f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessLow
                        ),
                        label = "settingsScale"
                    )

                    IconButton(
                        onClick = { showSettingsSheet = true },
                        interactionSource = settingsInteraction,
                        modifier = Modifier.graphicsLayer {
                            scaleX = settingsScale
                            scaleY = settingsScale
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Ajustes",
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Black)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Color.Black)
        ) {
            // 1. Cabecera de Perfil (Avatar con halo degradado + Contadores interactivos)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Avatar Circular con micro-escala
                val avatarInteraction = remember { MutableInteractionSource() }
                val isAvatarPressed by avatarInteraction.collectIsPressedAsState()
                val avatarScale by animateFloatAsState(
                    targetValue = if (isAvatarPressed) 0.94f else 1f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessLow
                    ),
                    label = "avatarScale"
                )

                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .graphicsLayer {
                            scaleX = avatarScale
                            scaleY = avatarScale
                        }
                        .clip(CircleShape)
                        .background(
                            Brush.sweepGradient(
                                listOf(ProfileOrange, ProfileRed, ProfileBlue, ProfileOrange)
                            )
                        )
                        .padding(2.5.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF141414)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = "Avatar",
                        tint = Color.White,
                        modifier = Modifier.size(40.dp)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Usuario All18n",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFF1E1710))
                            .border(0.6.dp, ProfileOrange.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                            .padding(horizontal = 6.dp, vertical = 1.dp)
                    ) {
                        Text(
                            text = "VIP AMOLED",
                            color = ProfileOrange,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Fila de Métricas Interactivas: Me Gusta | Guardados | Historial
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ProfileStatPill(
                        modifier = Modifier.weight(1f),
                        count = likedVideos.size,
                        label = "Me gusta",
                        icon = Icons.Default.Favorite,
                        accentColor = ProfileRed,
                        isSelected = selectedTab == ProfileTab.Liked,
                        onClick = { viewModel.selectTab(ProfileTab.Liked) }
                    )
                    ProfileStatPill(
                        modifier = Modifier.weight(1f),
                        count = savedVideos.size,
                        label = "Guardados",
                        icon = Icons.Default.Bookmark,
                        accentColor = ProfileOrange,
                        isSelected = selectedTab == ProfileTab.Saved,
                        onClick = { viewModel.selectTab(ProfileTab.Saved) }
                    )
                    ProfileStatPill(
                        modifier = Modifier.weight(1f),
                        count = historyVideos.size,
                        label = "Historial",
                        icon = Icons.Default.History,
                        accentColor = ProfileBlue,
                        isSelected = selectedTab == ProfileTab.History,
                        onClick = { viewModel.selectTab(ProfileTab.History) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // 2. Selector de Pestañas con animación fluida de color e indicador
            TabRow(
                selectedTabIndex = selectedTab.ordinal,
                containerColor = Color.Black,
                contentColor = Color.White,
                indicator = {},
                divider = { HorizontalDivider(color = ProfileDivider, thickness = 0.5.dp) }
            ) {
                ProfileTab.entries.forEach { tab ->
                    val isSelected = selectedTab == tab
                    val tabColor = when (tab) {
                        ProfileTab.Liked -> ProfileRed
                        ProfileTab.Saved -> ProfileOrange
                        ProfileTab.History -> ProfileBlue
                    }
                    val tabCount = when (tab) {
                        ProfileTab.Liked -> likedVideos.size
                        ProfileTab.Saved -> savedVideos.size
                        ProfileTab.History -> historyVideos.size
                    }
                    val animatedTint by animateColorAsState(
                        targetValue = if (isSelected) tabColor else Color(0xFF777777),
                        animationSpec = tween(200),
                        label = "tabTint_${tab.name}"
                    )

                    Tab(
                        selected = isSelected,
                        onClick = { viewModel.selectTab(tab) },
                        text = {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.padding(vertical = 4.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = when (tab) {
                                            ProfileTab.Liked -> Icons.Default.Favorite
                                            ProfileTab.Saved -> Icons.Default.Bookmark
                                            ProfileTab.History -> Icons.Default.History
                                        },
                                        contentDescription = tab.title,
                                        tint = animatedTint,
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Spacer(modifier = Modifier.width(5.dp))
                                    Text(
                                        text = "${tab.title} (${tabCount})",
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) Color.White else Color(0xFF777777),
                                        fontSize = 12.sp
                                    )
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Box(
                                    modifier = Modifier
                                        .width(if (isSelected) 42.dp else 0.dp)
                                        .height(3.dp)
                                        .clip(RoundedCornerShape(1.5.dp))
                                        .background(if (isSelected) tabColor else Color.Transparent)
                                )
                            }
                        }
                    )
                }
            }

            // 3. Barra de acción contextual rápida (Contador + Botón Vaciar)
            val activeList = when (selectedTab) {
                ProfileTab.Liked -> likedVideos
                ProfileTab.Saved -> savedVideos
                ProfileTab.History -> historyVideos
            }

            if (activeList.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${activeList.size} ${if (activeList.size == 1) "video" else "videos"}",
                        color = Color(0xFF777777),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )

                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFF161111))
                            .border(0.6.dp, Color(0xFF2C1919), RoundedCornerShape(6.dp))
                            .clickable { pendingClearTarget = selectedTab }
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteOutline,
                            contentDescription = "Vaciar",
                            tint = Color(0xFFFE2C55),
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Vaciar",
                            color = Color(0xFFFE2C55),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            // 4. Transición animada suave de contenido entre pestañas
            AnimatedContent(
                targetState = selectedTab,
                transitionSpec = {
                    if (targetState.ordinal > initialState.ordinal) {
                        (slideInHorizontally(animationSpec = tween(220)) { it / 3 } + fadeIn(animationSpec = tween(200)))
                            .togetherWith(slideOutHorizontally(animationSpec = tween(180)) { -it / 3 } + fadeOut(animationSpec = tween(160)))
                    } else {
                        (slideInHorizontally(animationSpec = tween(220)) { -it / 3 } + fadeIn(animationSpec = tween(200)))
                            .togetherWith(slideOutHorizontally(animationSpec = tween(180)) { it / 3 } + fadeOut(animationSpec = tween(160)))
                    }
                },
                label = "profileTabTransition",
                modifier = Modifier.fillMaxSize()
            ) { currentTab ->
                val listForTab = when (currentTab) {
                    ProfileTab.Liked -> likedVideos
                    ProfileTab.Saved -> savedVideos
                    ProfileTab.History -> historyVideos
                }

                if (listForTab.isEmpty()) {
                    ProfileEmptyState(
                        tab = currentTab,
                        onNavigateToAllTube = onNavigateToAllTube,
                        onNavigateToTikTok = onNavigateToTikTok
                    )
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 10.dp, end = 10.dp, top = 6.dp, bottom = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(listForTab, key = { it.id }) { video ->
                            ProfileVideoCard(
                                video = video,
                                onClick = { onVideoClick(video) },
                                onRemove = {
                                    when (currentTab) {
                                        ProfileTab.Liked -> viewModel.removeLiked(video)
                                        ProfileTab.Saved -> viewModel.removeSaved(video)
                                        ProfileTab.History -> viewModel.removeHistory(video)
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Píldora interactiva de métricas con micro-escala acelerada por GPU y respuesta táctil.
 */
@Composable
private fun ProfileStatPill(
    modifier: Modifier = Modifier,
    count: Int,
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    accentColor: Color,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val isPressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.94f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "statScale"
    )
    val bgColor by animateColorAsState(
        targetValue = if (isSelected) accentColor.copy(alpha = 0.14f) else Color(0xFF121212),
        animationSpec = tween(200),
        label = "statBg"
    )
    val borderColor by animateColorAsState(
        targetValue = if (isSelected) accentColor.copy(alpha = 0.55f) else Color(0xFF222222),
        animationSpec = tween(200),
        label = "statBorder"
    )

    Column(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(12.dp))
            .background(bgColor)
            .border(0.8.dp, borderColor, RoundedCornerShape(12.dp))
            .clickable(
                interactionSource = interaction,
                indication = null
            ) { onClick() }
            .padding(vertical = 10.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = accentColor,
                modifier = Modifier.size(15.dp)
            )
            Spacer(modifier = Modifier.width(5.dp))
            Text(
                text = count.toString(),
                color = Color.White,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 16.sp
            )
        }
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            color = if (isSelected) Color.White else Color(0xFF888888),
            fontSize = 11.sp,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}

/**
 * Tarjeta de video con rendimiento optimizado (Coil disk/memory cache, GPU scale)
 * y botón de eliminación rápida directa sin abrir el video.
 */
@Composable
private fun ProfileVideoCard(
    video: VideoItem,
    onClick: () -> Unit,
    onRemove: () -> Unit
) {
    val context = LocalContext.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "cardScale"
    )

    val (sourceLabel, sourceColor) = remember(video.source) {
        when (video.source.lowercase()) {
            "xvideos" -> "XVIDEOS" to Color(0xFFE50914)
            "xnxx" -> "XNXX" to Color(0xFF00A2FF)
            "pornhub" -> "PORNHUB" to Color(0xFFFF9000)
            "tiktok" -> "TIKTOK" to Color(0xFFFE2C55)
            "𝕏", "x" -> "𝕏" to Color.White
            "youporn" -> "YOUPORN" to Color(0xFFFF007F)
            "eporner" -> "EPORNER" to Color(0xFFFF0055)
            "redtube" -> "REDTUBE" to Color(0xFFD50000)
            else -> video.source.take(10).uppercase() to Color(0xFF1D9BF0)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFF0F0F0F))
            .border(0.6.dp, Color(0xFF222222), RoundedCornerShape(10.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = null
            ) { onClick() }
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 10f)
                .background(Color(0xFF141414))
        ) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(video.thumbUrl)
                    .crossfade(200)
                    .memoryCachePolicy(CachePolicy.ENABLED)
                    .diskCachePolicy(CachePolicy.ENABLED)
                    .build(),
                contentDescription = video.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )

            // Badge de Origen (Top Left)
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(6.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color(0xEE000000))
                    .padding(horizontal = 5.dp, vertical = 2.dp)
            ) {
                Text(
                    text = sourceLabel,
                    color = sourceColor,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Black
                )
            }

            // Botón de Eliminación Rápida (Top Right)
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(Color(0xCC000000))
                    .clickable { onRemove() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Eliminar",
                    tint = Color.White.copy(alpha = 0.85f),
                    modifier = Modifier.size(13.dp)
                )
            }

            // Duración (Bottom Right)
            if (video.durationText.isNotBlank()) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(6.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0xDD000000))
                        .padding(horizontal = 5.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = video.durationText,
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 7.dp)) {
            Text(
                text = video.title,
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 16.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = if (video.author.isNotBlank()) video.author else "All18n Video",
                color = Color(0xFF888888),
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * Estado vacío elegante con botones de acción directa a AllTube y TikTok.
 */
@Composable
private fun ProfileEmptyState(
    tab: ProfileTab,
    onNavigateToAllTube: () -> Unit,
    onNavigateToTikTok: () -> Unit
) {
    val tabColor = when (tab) {
        ProfileTab.Liked -> ProfileRed
        ProfileTab.Saved -> ProfileOrange
        ProfileTab.History -> ProfileBlue
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp, vertical = 24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(tabColor.copy(alpha = 0.12f))
                    .border(1.dp, tabColor.copy(alpha = 0.35f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = when (tab) {
                        ProfileTab.Liked -> Icons.Default.FavoriteBorder
                        ProfileTab.Saved -> Icons.Default.BookmarkBorder
                        ProfileTab.History -> Icons.Default.History
                    },
                    contentDescription = null,
                    tint = tabColor,
                    modifier = Modifier.size(36.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = when (tab) {
                    ProfileTab.Liked -> "Sin videos con Me gusta"
                    ProfileTab.Saved -> "Sin videos guardados"
                    ProfileTab.History -> "Historial vacío"
                },
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = when (tab) {
                    ProfileTab.Liked -> "Pulsa el corazón en AllTube, TikTok o 𝕏 para coleccionar tus favoritos."
                    ProfileTab.Saved -> "Guarda videos para verlos más tarde con el icono de marcador."
                    ProfileTab.History -> "Los videos que reproduzcas aparecerán aquí automáticamente."
                },
                color = Color(0xFF888888),
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                lineHeight = 18.sp
            )

            Spacer(modifier = Modifier.height(20.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = onNavigateToAllTube,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF1E1E1E),
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(20.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = ProfileOrange,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = "AllTube", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }

                Button(
                    onClick = onNavigateToTikTok,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF1E1E1E),
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(20.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Favorite,
                        contentDescription = null,
                        tint = ProfileRed,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = "TikTok", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

/**
 * Contenido de la hoja de Ajustes y Almacenamiento.
 */
@Composable
private fun ProfileSettingsContent(
    likedCount: Int,
    savedCount: Int,
    historyCount: Int,
    onClearCache: () -> Unit,
    onClearTab: (ProfileTab) -> Unit,
    onClose: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp)
            .padding(bottom = 24.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = null,
                    tint = ProfileOrange,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Ajustes de Perfil & Almacenamiento",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            }
            IconButton(onClick = onClose, modifier = Modifier.size(28.dp)) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Cerrar",
                    tint = Color(0xFF888888),
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "ALMACENAMIENTO Y CACHÉ",
            color = Color(0xFF666666),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.5.sp
        )

        Spacer(modifier = Modifier.height(8.dp))

        SettingsTile(
            icon = Icons.Default.Storage,
            iconTint = ProfileBlue,
            title = "Limpiar caché de imágenes",
            subtitle = "Libera memoria RAM y espacio en disco (Coil)",
            onClick = onClearCache
        )

        if (historyCount > 0) {
            Spacer(modifier = Modifier.height(8.dp))
            SettingsTile(
                icon = Icons.Default.History,
                iconTint = ProfileBlue,
                title = "Vaciar historial de reproducción",
                subtitle = "$historyCount videos registrados",
                onClick = { onClearTab(ProfileTab.History) }
            )
        }

        if (savedCount > 0) {
            Spacer(modifier = Modifier.height(8.dp))
            SettingsTile(
                icon = Icons.Default.Bookmark,
                iconTint = ProfileOrange,
                title = "Vaciar videos guardados",
                subtitle = "$savedCount videos guardados",
                onClick = { onClearTab(ProfileTab.Saved) }
            )
        }

        if (likedCount > 0) {
            Spacer(modifier = Modifier.height(8.dp))
            SettingsTile(
                icon = Icons.Default.Favorite,
                iconTint = ProfileRed,
                title = "Vaciar Me gusta",
                subtitle = "$likedCount videos con Me gusta",
                onClick = { onClearTab(ProfileTab.Liked) }
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        Text(
            text = "ACERCA DE LA APLICACIÓN",
            color = Color(0xFF666666),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.5.sp
        )

        Spacer(modifier = Modifier.height(8.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFF0F0F0F))
                .border(0.6.dp, Color(0xFF222222), RoundedCornerShape(10.dp))
                .padding(14.dp)
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = "All18n Native", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Text(text = "v1.0.0", color = ProfileOrange, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Aceleración por GPU activada • Tema AMOLED Ultra Dark • Motor ExoPlayer & Coil optimizados",
                    color = Color(0xFF888888),
                    fontSize = 11.sp,
                    lineHeight = 15.sp
                )
            }
        }
    }
}

@Composable
private fun SettingsTile(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: Color,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val isPressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.98f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "tileScale"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFF101010))
            .border(0.6.dp, Color(0xFF222222), RoundedCornerShape(10.dp))
            .clickable(
                interactionSource = interaction,
                indication = null
            ) { onClick() }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(iconTint.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(18.dp)
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = subtitle,
                color = Color(0xFF777777),
                fontSize = 11.sp
            )
        }
    }
}

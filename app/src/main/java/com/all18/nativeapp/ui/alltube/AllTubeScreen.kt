package com.all18.nativeapp.ui.alltube

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import com.all18.nativeapp.ui.tiktok.TikTokUiMode
import com.all18.nativeapp.ui.tiktok.TikTokUiPreferences
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.all18.nativeapp.core.model.VideoItem
import com.all18.nativeapp.core.repository.UserLibraryRepository
import com.all18.nativeapp.core.util.RecentSearchManager
import com.all18.nativeapp.ui.theme.*

// Colores oficiales de la interfaz de Pornhub
private val PornhubOrangeOfficial = Color(0xFFFF9000)
private val PornhubBlueVerified = Color(0xFF2196F3)
private val PornhubDarkPill = Color(0xFF1A1A1A)
private val PornhubTextGray = Color(0xFF888888)
private val PornhubCardDivider = Color(0xFF141414)

@Composable
private fun rememberShimmerBrush(): Brush {
    val shimmerColors = listOf(
        Color(0xFF141414),
        Color(0xFF242424),
        Color(0xFF141414)
    )
    val transition = rememberInfiniteTransition(label = "alltubeShimmer")
    val translateAnim = transition.animateFloat(
        initialValue = 0f,
        targetValue = 1400f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1100, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "alltubeShimmerTranslate"
    )
    return Brush.linearGradient(
        colors = shimmerColors,
        start = Offset(translateAnim.value - 450f, translateAnim.value - 450f),
        end = Offset(translateAnim.value, translateAnim.value)
    )
}

@Composable
private fun VideoCardSkeleton(brush: Brush) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 7.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(12.dp))
                .background(brush)
        )
        Spacer(modifier = Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(brush)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.92f)
                        .height(14.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(brush)
                )
                Spacer(modifier = Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.55f)
                        .height(12.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(brush)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AllTubeScreen(
    viewModel: AllTubeViewModel,
    onNavigateToProfile: () -> Unit = {},
    onNavigateToDirectory: () -> Unit = {},
    onVideoClick: (VideoItem) -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val selectedSource by viewModel.selectedSource.collectAsState()
    val selectedCategory by viewModel.selectedCategory.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()

    val context = LocalContext.current
    var isSearchActive by remember { mutableStateOf(false) }
    var searchInput by remember { mutableStateOf(searchQuery) }
    val focusManager = LocalFocusManager.current
    val listState = rememberLazyListState()

    val showSourceBadge by viewModel.showSourceBadge.collectAsState()
    var showOptionsSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val recentSearches by RecentSearchManager.recentSearches.collectAsState()
    var activeActionVideo by remember { mutableStateOf<VideoItem?>(null) }
    val actionSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    LaunchedEffect(Unit) {
        RecentSearchManager.init(context)
    }

    // Paginación continua proactiva (inicia carga con 5 elementos de margen para evitar pausas)
    val shouldLoadMore = remember {
        derivedStateOf {
            val total = listState.layoutInfo.totalItemsCount
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            total > 0 && lastVisible >= total - 5
        }
    }

    LaunchedEffect(shouldLoadMore.value) {
        if (shouldLoadMore.value) {
            viewModel.loadNextPage()
        }
    }

    // Pre-calentamiento proactivo de imágenes (Coil) para desplazamiento a 60/120 FPS sin parpadeos
    LaunchedEffect(listState.firstVisibleItemIndex, state) {
        if (state is AllTubeUiState.Success) {
            val videos = (state as AllTubeUiState.Success).videos
            val imageLoader = coil.Coil.imageLoader(context)
            val upcoming = videos.drop(listState.firstVisibleItemIndex + 2).take(5)
            upcoming.forEach { v ->
                if (v.thumbUrl.isNotBlank()) {
                    imageLoader.enqueue(
                        ImageRequest.Builder(context)
                            .data(v.thumbUrl)
                            .memoryCachePolicy(CachePolicy.ENABLED)
                            .diskCachePolicy(CachePolicy.ENABLED)
                            .build()
                    )
                }
            }
        }
    }

    Scaffold(
        containerColor = Color.Black,
        topBar = {
            Column(
                modifier = Modifier
                    .background(Color.Black)
                    .statusBarsPadding()
            ) {
                // Barra Superior Calcada a Pornhub: Menú Naranja + Género ⚥▼ | Logo Pornhub | Lupa + Perfil
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    if (!isSearchActive) {
                        // Extremo Izquierdo: Menú Hamburguesa Naranja + Selector Género
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            IconButton(
                                onClick = { showOptionsSheet = true },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Menu,
                                    contentDescription = "Opciones",
                                    tint = PornhubOrangeOfficial,
                                    modifier = Modifier.size(24.dp)
                                )
                            }

                            // Símbolo de Orientación / Género: ⚥▼
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.clickable { /* Orientación */ }
                            ) {
                                Text(
                                    text = "⚥",
                                    color = Color.White,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.width(2.dp))
                                Text(
                                    text = "▼",
                                    color = Color(0xFFAAAAAA),
                                    fontSize = 9.sp
                                )
                            }
                        }

                        // Centro: Logo Oficial Pornhub
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clickable {
                                viewModel.refreshFeed()
                            }
                        ) {
                            Text(
                                text = "Porn",
                                fontWeight = FontWeight.Black,
                                fontSize = 23.sp,
                                color = Color.White,
                                letterSpacing = (-0.5).sp
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(PornhubOrangeOfficial)
                                    .padding(horizontal = 6.dp, vertical = 2.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "hub",
                                    fontWeight = FontWeight.Black,
                                    fontSize = 18.sp,
                                    color = Color.Black,
                                    letterSpacing = (-0.5).sp
                                )
                            }
                        }

                        // Extremo Derecho: Lupa de Búsqueda + Perfil
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            IconButton(
                                onClick = { isSearchActive = true },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = "Buscar",
                                    tint = Color.White,
                                    modifier = Modifier.size(22.dp)
                                )
                            }

                            IconButton(
                                onClick = onNavigateToProfile,
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Person,
                                    contentDescription = "Perfil",
                                    tint = Color(0xFF888888),
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                    } else {
                        // Barra de Búsqueda Desplegada
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(40.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(PornhubDarkPill)
                                .padding(horizontal = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = null,
                                tint = Color(0xFF888888),
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            BasicTextField(
                                value = searchInput,
                                onValueChange = { searchInput = it },
                                singleLine = true,
                                textStyle = TextStyle(
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium
                                ),
                                cursorBrush = SolidColor(PornhubOrangeOfficial),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                keyboardActions = KeyboardActions(onSearch = {
                                    focusManager.clearFocus()
                                    if (searchInput.isNotBlank()) {
                                        RecentSearchManager.addSearch(context, searchInput)
                                    }
                                    viewModel.search(searchInput)
                                }),
                                modifier = Modifier.weight(1f),
                                decorationBox = { innerTextField ->
                                    if (searchInput.isEmpty()) {
                                        Text(
                                            text = "Buscar en Pornhub...",
                                            color = Color(0xFF666666),
                                            fontSize = 14.sp
                                        )
                                    }
                                    innerTextField()
                                }
                            )
                            if (searchInput.isNotEmpty()) {
                                IconButton(
                                    onClick = { searchInput = "" },
                                    modifier = Modifier.size(26.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Limpiar",
                                        tint = Color(0xFFAAAAAA),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                            IconButton(
                                onClick = {
                                    isSearchActive = false
                                    searchInput = ""
                                    viewModel.search("")
                                },
                                modifier = Modifier.size(26.dp)
                            ) {
                                Text(
                                    text = "✕",
                                    color = PornhubOrangeOfficial,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                // Fila de Búsquedas Recientes / Tendencias con Chips Interactivos de 1 Toque
                if (isSearchActive) {
                    val searchTrends = remember {
                        listOf("Amateur", "Cosplay", "Latinas", "POV", "HD", "Hentai", "4K", "Japonesas", "Verificados", "Trending")
                    }
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 5.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = if (recentSearches.isNotEmpty()) "BÚSQUEDAS RECIENTES" else "TENDENCIAS POPULARES",
                                color = Color(0xFF888888),
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp
                            )
                            if (recentSearches.isNotEmpty()) {
                                Text(
                                    text = "Borrar todo",
                                    color = PornhubOrangeOfficial,
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier
                                        .clickable { RecentSearchManager.clearAll(context) }
                                        .padding(4.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (recentSearches.isNotEmpty()) {
                                items(recentSearches) { query ->
                                    Surface(
                                        shape = RoundedCornerShape(16.dp),
                                        color = Color(0xFF202020),
                                        border = BorderStroke(0.7.dp, Color(0xFF333333)),
                                        modifier = Modifier.clickable {
                                            searchInput = query
                                            focusManager.clearFocus()
                                            RecentSearchManager.addSearch(context, query)
                                            viewModel.search(query)
                                        }
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.padding(start = 10.dp, end = 6.dp, top = 4.dp, bottom = 4.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.History,
                                                contentDescription = null,
                                                tint = Color(0xFF999999),
                                                modifier = Modifier.size(13.dp)
                                            )
                                            Spacer(modifier = Modifier.width(5.dp))
                                            Text(
                                                text = query,
                                                color = Color.White,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Medium
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Box(
                                                modifier = Modifier
                                                    .size(18.dp)
                                                    .clickable {
                                                        RecentSearchManager.removeSearch(context, query)
                                                    },
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Close,
                                                    contentDescription = "Eliminar",
                                                    tint = Color(0xFF777777),
                                                    modifier = Modifier.size(12.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            } else {
                                items(searchTrends) { tag ->
                                    Surface(
                                        shape = RoundedCornerShape(16.dp),
                                        color = Color(0xFF1C1C1C),
                                        border = BorderStroke(0.7.dp, PornhubOrangeOfficial.copy(alpha = 0.35f)),
                                        modifier = Modifier.clickable {
                                            searchInput = tag
                                            focusManager.clearFocus()
                                            RecentSearchManager.addSearch(context, tag)
                                            viewModel.search(tag)
                                        }
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                        ) {
                                            Text(
                                                text = "#",
                                                color = PornhubOrangeOfficial,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Spacer(modifier = Modifier.width(3.dp))
                                            Text(
                                                text = tag,
                                                color = Color(0xFFE0E0E0),
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Medium
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Selector de Fuentes con Scroll Horizontal y Animación
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(viewModel.sources) { src ->
                        val isSelected = selectedSource == src
                        val bgCol by animateColorAsState(
                            targetValue = if (isSelected) PornhubOrangeOfficial else Color(0xFF1C1C1C),
                            animationSpec = tween(180),
                            label = "srcBg_$src"
                        )
                        val textCol by animateColorAsState(
                            targetValue = if (isSelected) Color.Black else Color(0xFFB0B0B0),
                            animationSpec = tween(180),
                            label = "srcText_$src"
                        )
                        Box(
                            modifier = Modifier
                                .height(30.dp)
                                .clip(RoundedCornerShape(15.dp))
                                .background(bgCol)
                                .border(
                                    width = 0.8.dp,
                                    color = if (isSelected) Color.Transparent else Color(0xFF2D2D2D),
                                    shape = RoundedCornerShape(15.dp)
                                )
                                .clickable { viewModel.selectSource(src) }
                                .padding(horizontal = 14.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = src,
                                color = textCol,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }

                // Filtro de Categorías con Scroll Horizontal y Animación
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 5.dp),
                    horizontalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    items(viewModel.categories) { cat ->
                        val isSelected = selectedCategory == cat && !isSearchActive
                        val catBg by animateColorAsState(
                            targetValue = if (isSelected) Color(0x33FF9000) else Color(0xFF141414),
                            animationSpec = tween(180),
                            label = "catBg_$cat"
                        )
                        val catBorder by animateColorAsState(
                            targetValue = if (isSelected) PornhubOrangeOfficial else Color(0xFF242424),
                            animationSpec = tween(180),
                            label = "catBorder_$cat"
                        )
                        val catText by animateColorAsState(
                            targetValue = if (isSelected) PornhubOrangeOfficial else Color(0xFF888888),
                            animationSpec = tween(180),
                            label = "catText_$cat"
                        )
                        Box(
                            modifier = Modifier
                                .height(27.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(catBg)
                                .border(
                                    width = 0.8.dp,
                                    color = catBorder,
                                    shape = RoundedCornerShape(14.dp)
                                )
                                .clickable {
                                    if (isSearchActive) {
                                        isSearchActive = false
                                        searchInput = ""
                                    }
                                    viewModel.selectCategory(cat)
                                }
                                .padding(horizontal = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = cat,
                                color = catText,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }

                HorizontalDivider(color = Color(0xFF1A1A1A), thickness = 0.5.dp)
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Color.Black)
        ) {
            when (val s = state) {
                is AllTubeUiState.Loading -> {
                    // Shimmer Skeleton Loading fluido y de alta gama
                    val shimmerBrush = rememberShimmerBrush()
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(top = 6.dp, bottom = 24.dp)
                    ) {
                        repeat(4) {
                            VideoCardSkeleton(brush = shimmerBrush)
                        }
                    }
                }
                is AllTubeUiState.Error -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                            Text(text = s.message, color = TextSecondary, fontSize = 14.sp)
                            Spacer(modifier = Modifier.height(14.dp))
                            Button(
                                onClick = { viewModel.loadFeed(1) },
                                colors = ButtonDefaults.buttonColors(containerColor = PornhubOrangeOfficial, contentColor = Color.Black),
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text("Reintentar", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
                is AllTubeUiState.Success -> {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(top = 4.dp, bottom = 20.dp)
                    ) {
                        items(
                            items = s.videos,
                            key = { "${it.source}_${it.id}_${it.title.hashCode()}" }
                        ) { video ->
                            AuthenticPornhubCard(
                                video = video,
                                showSourceBadge = showSourceBadge,
                                onClick = { onVideoClick(video) },
                                onMoreClick = { activeActionVideo = video }
                            )
                        }
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(
                                    color = PornhubOrangeOfficial,
                                    modifier = Modifier.size(26.dp),
                                    strokeWidth = 2.5.dp
                                )
                            }
                        }
                    }
                }
            }
        }

        if (showOptionsSheet) {
            ModalBottomSheet(
                onDismissRequest = { showOptionsSheet = false },
                sheetState = sheetState,
                containerColor = Color(0xFF161616),
                scrimColor = Color.Black.copy(alpha = 0.75f),
                dragHandle = {
                    BottomSheetDefaults.DragHandle(color = Color(0xFF555555))
                }
            ) {
                AllTubeOptionsContent(
                    showSourceBadge = showSourceBadge,
                    onToggleShowSourceBadge = { viewModel.toggleShowSourceBadge(it) },
                    selectedSource = selectedSource,
                    sources = viewModel.sources,
                    onSelectSource = { viewModel.selectSource(it) },
                    onNavigateToDirectory = {
                        showOptionsSheet = false
                        onNavigateToDirectory()
                    },
                    onNavigateToProfile = {
                        showOptionsSheet = false
                        onNavigateToProfile()
                    },
                    onClose = { showOptionsSheet = false }
                )
            }
        }

        // Hoja Inferior para Menú Rápido de Tarjetas (Favoritos, Compartir, Copiar enlace, Navegador)
        activeActionVideo?.let { targetVideo ->
            val isSaved by remember(targetVideo.id) {
                derivedStateOf { UserLibraryRepository.isVideoSaved(targetVideo.id) }
            }
            ModalBottomSheet(
                onDismissRequest = { activeActionVideo = null },
                sheetState = actionSheetState,
                containerColor = Color(0xFF161616),
                scrimColor = Color.Black.copy(alpha = 0.75f),
                dragHandle = {
                    BottomSheetDefaults.DragHandle(color = Color(0xFF555555))
                }
            ) {
                VideoQuickActionsSheet(
                    video = targetVideo,
                    isSaved = isSaved,
                    onToggleSave = {
                        UserLibraryRepository.toggleSaveVideo(targetVideo)
                    },
                    onShare = {
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            val shareUrl = targetVideo.pageUrl
                            putExtra(Intent.EXTRA_SUBJECT, targetVideo.title)
                            putExtra(Intent.EXTRA_TEXT, "${targetVideo.title}\n$shareUrl")
                        }
                        context.startActivity(Intent.createChooser(shareIntent, "Compartir video"))
                        activeActionVideo = null
                    },
                    onCopyLink = {
                        val shareUrl = targetVideo.pageUrl
                        val clip = ClipData.newPlainText("video_url", shareUrl)
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                        clipboard?.setPrimaryClip(clip)
                        Toast.makeText(context, "Enlace copiado al portapapeles", Toast.LENGTH_SHORT).show()
                        activeActionVideo = null
                    },
                    onOpenBrowser = {
                        val url = targetVideo.pageUrl
                        if (url.isNotEmpty()) {
                            try {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                context.startActivity(intent)
                            } catch (_: Exception) {}
                        }
                        activeActionVideo = null
                    },
                    onClose = { activeActionVideo = null }
                )
            }
        }
    }
}

/**
 * Tarjeta de Video Optimizada con aceleración por GPU, micro-animación al pulsar,
 * caché Coil ultrarrápido y estética moderna Pornhub.
 */
@Composable
fun AuthenticPornhubCard(
    video: VideoItem,
    showSourceBadge: Boolean = false,
    onClick: () -> Unit,
    onMoreClick: (() -> Unit)? = null
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.98f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "cardScale"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 5.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null
            ) { onClick() }
    ) {
        // 1. Miniatura 16:9 con bordes redondeados modernos y caché Coil de alto rendimiento
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF0F0F0F))
                .border(0.5.dp, Color(0xFF222222), RoundedCornerShape(12.dp))
        ) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(video.thumbUrl)
                    .crossfade(200)
                    .memoryCachePolicy(CachePolicy.ENABLED)
                    .diskCachePolicy(CachePolicy.ENABLED)
                    .build(),
                contentDescription = video.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )

            // Etiqueta de la API / Web de origen (si está activada)
            if (showSourceBadge) {
                val (sourceLabel, sourceColor) = when (video.source.lowercase()) {
                    "xvideos" -> "XVIDEOS" to Color(0xFFE50914)
                    "xnxx" -> "XNXX" to Color(0xFF00A2FF)
                    "pornhub" -> "PORNHUB" to Color(0xFFFF9000)
                    "youporn" -> "YOUPORN" to Color(0xFFFF007F)
                    "eporner" -> "EPORNER" to Color(0xFFFF0055)
                    "redtube" -> "REDTUBE" to Color(0xFFD50000)
                    "justporn" -> "JUSTPORN" to Color(0xFFFF6F00)
                    "porn.com", "porncom" -> "PORN.COM" to Color(0xFF00C853)
                    "xhamster" -> "XHAMSTER" to Color(0xFF5333ED)
                    "porn300" -> "PORN300" to Color(0xFF00E676)
                    "porntrex" -> "PORNTREX" to Color(0xFFFF3D00)
                    else -> video.source.uppercase() to Color(0xFF1D9BF0)
                }

                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xEE000000))
                        .border(0.8.dp, sourceColor.copy(alpha = 0.8f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 7.dp, vertical = 3.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(sourceColor)
                        )
                        Text(
                            text = sourceLabel,
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 0.5.sp
                        )
                    }
                }
            }

            // Duración clásica en esquina inferior derecha (ej. "18:50")
            val duration = if (video.durationText.isNotEmpty()) video.durationText else "17:52"
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color(0xDD000000))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = duration,
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // 2. Fila con Avatar del Creador / Canal + Título y Metadatos
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 2.dp),
            verticalAlignment = Alignment.Top
        ) {
            // Normalizar y limpiar autor (en caso de que algún scraper devuelva tags o JSON)
            val cleanAuthor = remember(video.author) {
                var a = video.author.trim()
                if (a.startsWith("{") && a.contains("\"")) {
                    val match = Regex("\"(?:tag_name|name|author|title)\"\\s*:\\s*\"([^\"]+)\"").find(a)
                    a = match?.groupValues?.get(1) ?: "All18"
                }
                if (a.isBlank() || a.equals("null", ignoreCase = true)) "All18" else a
            }
            // Avatar circular con gradiente e inicial del canal
            val authorInitial = remember(cleanAuthor) {
                cleanAuthor.trim().firstOrNull { it.isLetterOrDigit() }?.uppercase() ?: "A"
            }
            val avatarGrad = remember(cleanAuthor) {
                val seed = kotlin.math.abs(cleanAuthor.hashCode())
                when (seed % 5) {
                    0 -> listOf(Color(0xFFFF8800), Color(0xFFFF3366))
                    1 -> listOf(Color(0xFF7928CA), Color(0xFFFF0080))
                    2 -> listOf(Color(0xFF0070F3), Color(0xFF00DFD8))
                    3 -> listOf(Color(0xFFFF416C), Color(0xFFFF4B2B))
                    else -> listOf(Color(0xFF11998E), Color(0xFF38EF7D))
                }
            }

            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(Brush.linearGradient(avatarGrad)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = authorInitial,
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.width(9.dp))

            Column(
                modifier = Modifier.weight(1f)
            ) {
                // Título del video
                Text(
                    text = video.title,
                    color = Color(0xFFF0F0F0),
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 18.sp
                )

                Spacer(modifier = Modifier.height(3.dp))

                // Fila de Autor con Verificado + Vistas
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = cleanAuthor,
                        color = Color(0xFFCCCCCC),
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Verificado",
                        tint = PornhubBlueVerified,
                        modifier = Modifier.size(13.dp)
                    )
                    Text(
                        text = " • ",
                        color = PornhubTextGray,
                        fontSize = 11.5.sp
                    )
                    Text(
                        text = "${if (video.views.isNotEmpty()) video.views else "288K"} vistas",
                        color = PornhubTextGray,
                        fontSize = 11.5.sp
                    )
                }
            }

            // Icono de opciones (3 puntos) interactivo
            Box(
                modifier = Modifier
                    .padding(start = 2.dp)
                    .size(32.dp)
                    .clip(CircleShape)
                    .clickable { onMoreClick?.invoke() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "Opciones del video",
                    tint = PornhubTextGray,
                    modifier = Modifier.size(19.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AllTubeOptionsContent(
    showSourceBadge: Boolean,
    onToggleShowSourceBadge: (Boolean) -> Unit,
    selectedSource: String,
    sources: List<String>,
    onSelectSource: (String) -> Unit,
    onNavigateToDirectory: () -> Unit,
    onNavigateToProfile: () -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        TikTokUiPreferences.load(context)
    }
    val tikTokUiMode by TikTokUiPreferences.uiMode.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(bottom = 36.dp)
    ) {
        // Cabecera del panel de opciones
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = "Opciones de AllTube",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Black
                )
                Text(
                    text = "Ajustes de visualización y proveedores",
                    color = Color(0xFF888888),
                    fontSize = 12.sp
                )
            }
            IconButton(onClick = onClose) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Cerrar",
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // Opción 1: Ver de qué API / Web es el video desde la miniatura
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF1F1F1F))
                .border(
                    width = 1.dp,
                    color = if (showSourceBadge) PornhubOrangeOfficial.copy(alpha = 0.6f) else Color(0xFF333333),
                    shape = RoundedCornerShape(12.dp)
                )
                .padding(16.dp)
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(PornhubOrangeOfficial.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Public,
                                contentDescription = null,
                                tint = PornhubOrangeOfficial,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Ver origen (API / Web) en miniatura",
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Muestra la web de procedencia (XVideos, XNXX) directamente en la miniatura de cada video",
                                color = Color(0xFFAAAAAA),
                                fontSize = 11.sp,
                                lineHeight = 15.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Switch(
                        checked = showSourceBadge,
                        onCheckedChange = onToggleShowSourceBadge,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.Black,
                            checkedTrackColor = PornhubOrangeOfficial,
                            uncheckedThumbColor = Color(0xFF888888),
                            uncheckedTrackColor = Color(0xFF2A2A2A)
                        )
                    )
                }

                // Vista previa de las etiquetas cuando está activado
                AnimatedVisibility(visible = showSourceBadge) {
                    Column(modifier = Modifier.padding(top = 14.dp)) {
                        HorizontalDivider(color = Color(0xFF2A2A2A), thickness = 0.8.dp)
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "Etiquetas activas en la miniatura:",
                            color = Color(0xFF888888),
                            fontSize = 11.sp
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Badge XVideos
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Color(0xEE000000))
                                    .border(0.8.dp, Color(0xFFE50914).copy(alpha = 0.8f), RoundedCornerShape(4.dp))
                                    .padding(horizontal = 6.dp, vertical = 3.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFFE50914))
                                    )
                                    Text(
                                        text = "XVIDEOS",
                                        color = Color.White,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                }
                            }

                            // Badge Pornhub
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Color(0xEE000000))
                                    .border(0.8.dp, Color(0xFFFF9000).copy(alpha = 0.8f), RoundedCornerShape(4.dp))
                                    .padding(horizontal = 6.dp, vertical = 3.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFFFF9000))
                                    )
                                    Text(
                                        text = "PORNHUB",
                                        color = Color.White,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                }
                            }

                            // Badge YouPorn
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Color(0xEE000000))
                                    .border(0.8.dp, Color(0xFFFF007F).copy(alpha = 0.8f), RoundedCornerShape(4.dp))
                                    .padding(horizontal = 6.dp, vertical = 3.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFFFF007F))
                                    )
                                    Text(
                                        text = "YOUPORN",
                                        color = Color.White,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                }
                            }

                            // Badge XNXX
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Color(0xEE000000))
                                    .border(0.8.dp, Color(0xFF00A2FF).copy(alpha = 0.8f), RoundedCornerShape(4.dp))
                                    .padding(horizontal = 6.dp, vertical = 3.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFF00A2FF))
                                    )
                                    Text(
                                        text = "XNXX",
                                        color = Color.White,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                }
                            }

                            // Badge EPorner
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Color(0xEE000000))
                                    .border(0.8.dp, Color(0xFFFF0055).copy(alpha = 0.8f), RoundedCornerShape(4.dp))
                                    .padding(horizontal = 6.dp, vertical = 3.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFFFF0055))
                                    )
                                    Text(
                                        text = "EPORNER",
                                        color = Color.White,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                }
                            }

                            // Badge RedTube
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Color(0xEE000000))
                                    .border(0.8.dp, Color(0xFFD50000).copy(alpha = 0.8f), RoundedCornerShape(4.dp))
                                    .padding(horizontal = 6.dp, vertical = 3.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFFD50000))
                                    )
                                    Text(
                                        text = "REDTUBE",
                                        color = Color.White,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                }
                            }

                            // Badge JustPorn
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Color(0xEE000000))
                                    .border(0.8.dp, Color(0xFFFF6F00).copy(alpha = 0.8f), RoundedCornerShape(4.dp))
                                    .padding(horizontal = 6.dp, vertical = 3.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFFFF6F00))
                                    )
                                    Text(
                                        text = "JUSTPORN",
                                        color = Color.White,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                }
                            }

                            // Badge Porn.com
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Color(0xEE000000))
                                    .border(0.8.dp, Color(0xFF00C853).copy(alpha = 0.8f), RoundedCornerShape(4.dp))
                                    .padding(horizontal = 6.dp, vertical = 3.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFF00C853))
                                    )
                                    Text(
                                        text = "PORN.COM",
                                        color = Color.White,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                }
                            }

                            // Badge xHamster
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Color(0xEE000000))
                                    .border(0.8.dp, Color(0xFF5333ED).copy(alpha = 0.8f), RoundedCornerShape(4.dp))
                                    .padding(horizontal = 6.dp, vertical = 3.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFF5333ED))
                                    )
                                    Text(
                                        text = "XHAMSTER",
                                        color = Color.White,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                }
                            }

                            // Badge Porn300
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Color(0xEE000000))
                                    .border(0.8.dp, Color(0xFF00E676).copy(alpha = 0.8f), RoundedCornerShape(4.dp))
                                    .padding(horizontal = 6.dp, vertical = 3.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFF00E676))
                                    )
                                    Text(
                                        text = "PORN300",
                                        color = Color.White,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                }
                            }

                            // Badge PornTrex
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Color(0xEE000000))
                                    .border(0.8.dp, Color(0xFFFF3D00).copy(alpha = 0.8f), RoundedCornerShape(4.dp))
                                    .padding(horizontal = 6.dp, vertical = 3.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFFFF3D00))
                                    )
                                    Text(
                                        text = "PORNTREX",
                                        color = Color.White,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Opción 2: Selector de Plataforma / Web Fuente
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF1F1F1F))
                .padding(16.dp)
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF2A2A2A)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Filtrar por proveedor",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Plataforma activa: $selectedSource",
                            color = Color(0xFFAAAAAA),
                            fontSize = 11.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    sources.forEach { src ->
                        val isSelected = selectedSource == src
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) PornhubOrangeOfficial else Color(0xFF2B2B2B))
                                .clickable { onSelectSource(src) }
                                .padding(horizontal = 14.dp, vertical = 9.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = src,
                                color = if (isSelected) Color.Black else Color.White,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Opción 3: Modo de Interfaz de TikTok (migrado desde las 3 rayitas de TikTok)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF1F1F1F))
                .border(
                    width = 1.dp,
                    color = Color(0xFFFE2C55).copy(alpha = 0.35f),
                    shape = RoundedCornerShape(12.dp)
                )
                .padding(16.dp)
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFFE2C55).copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.ViewStream,
                            contentDescription = null,
                            tint = Color(0xFFFE2C55),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Modo de Interfaz de TikTok",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Estilo de los elementos en el reproductor vertical",
                            color = Color(0xFFAAAAAA),
                            fontSize = 11.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Modo Limpio: UI default de TikTok
                Surface(
                    onClick = {
                        TikTokUiPreferences.setUiMode(context, TikTokUiMode.DEFAULT_CLEAN)
                    },
                    color = if (tikTokUiMode == TikTokUiMode.DEFAULT_CLEAN) Color(0xFF2A2A2A) else Color(0xFF141414),
                    shape = RoundedCornerShape(10.dp),
                    border = if (tikTokUiMode == TikTokUiMode.DEFAULT_CLEAN) BorderStroke(1.dp, Color(0xFFFE2C55).copy(alpha = 0.7f)) else null,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = (tikTokUiMode == TikTokUiMode.DEFAULT_CLEAN),
                            onClick = null,
                            colors = RadioButtonDefaults.colors(
                                selectedColor = Color(0xFFFE2C55),
                                unselectedColor = Color.Gray
                            )
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "2. UI default de TikTok",
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(Color(0xFFFE2C55).copy(alpha = 0.2f))
                                        .padding(horizontal = 5.dp, vertical = 2.dp)
                                ) {
                                    Text("Limpio", color = Color(0xFFFE2C55), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Sin comentarios, sin disco giratorio, sin sonido original ni traducción. Textos y @ compactos, iconos reducidos y transparentes.",
                                color = Color(0xFFAAAAAA),
                                fontSize = 11.sp,
                                lineHeight = 14.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Modo Clásico: UI actual de TikTok
                Surface(
                    onClick = {
                        TikTokUiPreferences.setUiMode(context, TikTokUiMode.ACTUAL)
                    },
                    color = if (tikTokUiMode == TikTokUiMode.ACTUAL) Color(0xFF2A2A2A) else Color(0xFF141414),
                    shape = RoundedCornerShape(10.dp),
                    border = if (tikTokUiMode == TikTokUiMode.ACTUAL) BorderStroke(1.dp, Color(0xFFFE2C55).copy(alpha = 0.7f)) else null,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = (tikTokUiMode == TikTokUiMode.ACTUAL),
                            onClick = null,
                            colors = RadioButtonDefaults.colors(
                                selectedColor = Color(0xFFFE2C55),
                                unselectedColor = Color.Gray
                            )
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "1. UI actual de TikTok",
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Diseño clásico completo: incluye botón de comentarios, disco giratorio, pista de audio, ver traducción e iconos estándar.",
                                color = Color(0xFFAAAAAA),
                                fontSize = 11.sp,
                                lineHeight = 14.sp
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Opción: Directorio 18+ Completo (+2,280 Sitios y 98 Categorías)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF1F1F1F))
                .clickable { onNavigateToDirectory() }
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(PornhubOrangeOfficial.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Explore,
                            contentDescription = null,
                            tint = PornhubOrangeOfficial,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Directorio 18+ (+2,280 Sitios)",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Explora 98 categorías de tubes, juegos, hentai y cams",
                            color = Color(0xFFAAAAAA),
                            fontSize = 11.sp
                        )
                    }
                }

                Icon(
                    imageVector = Icons.Default.ArrowForward,
                    contentDescription = null,
                    tint = Color(0xFF888888),
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Opción 4: Acceso directo a Mi Perfil
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF1F1F1F))
                .clickable { onNavigateToProfile() }
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF2A2A2A)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Mi Perfil y Biblioteca",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Ver historial, videos guardados y favoritos",
                            color = Color(0xFFAAAAAA),
                            fontSize = 11.sp
                        )
                    }
                }

                Icon(
                    imageVector = Icons.Default.ArrowForward,
                    contentDescription = null,
                    tint = Color(0xFF888888),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

/**
 * Hoja de opciones rápidas al pulsar los 3 puntos de una tarjeta de video.
 * Diseño AMOLED elegante, rápido y sin sobrecarga gráfica.
 */
@Composable
private fun VideoQuickActionsSheet(
    video: VideoItem,
    isSaved: Boolean,
    onToggleSave: () -> Unit,
    onShare: () -> Unit,
    onCopyLink: () -> Unit,
    onOpenBrowser: () -> Unit,
    onClose: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(bottom = 36.dp)
    ) {
        // Cabecera con previsualización del video
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(width = 86.dp, height = 50.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF222222))
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(video.thumbUrl)
                        .crossfade(true)
                        .build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = video.title,
                    color = Color.White,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 18.sp
                )
                Spacer(modifier = Modifier.height(3.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = video.source.uppercase(),
                        color = PornhubOrangeOfficial,
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    if (video.durationText.isNotEmpty()) {
                        Text(
                            text = " • ${video.durationText}",
                            color = Color(0xFF888888),
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }

        HorizontalDivider(color = Color(0xFF242424), thickness = 0.8.dp)
        Spacer(modifier = Modifier.height(10.dp))

        // Opción 1: Guardar en Favoritos
        QuickActionItem(
            icon = if (isSaved) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
            iconTint = if (isSaved) Color(0xFFFF1744) else Color.White,
            title = if (isSaved) "Eliminar de Favoritos" else "Guardar en Favoritos",
            subtitle = if (isSaved) "Guardado en tu biblioteca personal" else "Accede fácilmente desde tu perfil",
            onClick = onToggleSave
        )

        // Opción 2: Compartir video
        QuickActionItem(
            icon = Icons.Default.Share,
            iconTint = Color.White,
            title = "Compartir video",
            subtitle = "Enviar enlace a WhatsApp, Telegram y más",
            onClick = onShare
        )

        // Opción 3: Copiar enlace
        QuickActionItem(
            icon = Icons.Default.ContentCopy,
            iconTint = Color.White,
            title = "Copiar enlace directo",
            subtitle = "Copiar URL del video al portapapeles",
            onClick = onCopyLink
        )

        // Opción 4: Abrir en navegador externo
        QuickActionItem(
            icon = Icons.Default.OpenInBrowser,
            iconTint = Color.White,
            title = "Abrir en navegador",
            subtitle = "Ver página original en Chrome u otro explorador",
            onClick = onOpenBrowser
        )
    }
}

@Composable
private fun QuickActionItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: Color,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 11.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(Color(0xFF222222)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = subtitle,
                color = Color(0xFF888888),
                fontSize = 11.5.sp
            )
        }
    }
}


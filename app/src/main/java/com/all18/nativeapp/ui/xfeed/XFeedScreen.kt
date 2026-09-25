package com.all18.nativeapp.ui.xfeed

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.all18.nativeapp.core.model.VideoItem
import com.all18.nativeapp.core.model.XPostItem
import com.all18.nativeapp.ui.theme.*
import java.util.Locale

// Paleta oficial de Twitter / X (Lights Out / AMOLED Black)
private val XBlue = Color(0xFF1D9BF0)
private val XTextSecondary = Color(0xFF71767B)
private val XDivider = Color(0xFF2F3336)
private val XMediaBorder = Color(0xFF2F3336)
private val XHeartPink = Color(0xFFF91880)
private val XRepostGreen = Color(0xFF00BA7C)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun XFeedScreen(
    viewModel: XFeedViewModel,
    onNavigateToProfile: () -> Unit = {},
    onFullscreenMediaChange: (Boolean) -> Unit = {},
    onVideoClick: (VideoItem) -> Unit = {}
) {
    val state by viewModel.uiState.collectAsState()
    val selectedTab by viewModel.selectedTab.collectAsState()
    val listState = rememberLazyListState()

    var activeVideoList by remember { mutableStateOf<List<XPostItem>?>(null) }
    var activeVideoIndex by remember { mutableIntStateOf(0) }

    var activePhotoPost by remember { mutableStateOf<XPostItem?>(null) }
    var activePhotoIndex by remember { mutableIntStateOf(0) }

    val isFullscreen = activeVideoList != null || activePhotoPost != null
    LaunchedEffect(isFullscreen) {
        onFullscreenMediaChange(isFullscreen)
    }

    val currentPostsList = (state as? XFeedUiState.Success)?.posts ?: emptyList()
    val currentVideoPosts = remember(currentPostsList) {
        currentPostsList.filter { it.videoItem != null }
    }

    if (activeVideoList != null) {
        val displayedVideos = if (currentVideoPosts.isNotEmpty()) currentVideoPosts else activeVideoList!!
        XVideoPlayerScreen(
            videoPosts = displayedVideos,
            initialIndex = activeVideoIndex,
            onBack = { activeVideoList = null },
            onLoadMore = { viewModel.loadNextPage() },
            onToggleLike = { id -> viewModel.toggleLike(id) },
            onToggleRepost = { id -> viewModel.toggleRepost(id) },
            onToggleBookmark = { id -> viewModel.toggleBookmark(id) }
        )
    } else if (activePhotoPost != null) {
        val currentPhoto = currentPostsList.find { it.id == activePhotoPost?.id } ?: activePhotoPost!!
        XPhotoViewerScreen(
            post = currentPhoto,
            initialImageIndex = activePhotoIndex,
            onBack = { activePhotoPost = null },
            onToggleLike = { viewModel.toggleLike(currentPhoto.id) },
            onToggleBookmark = { viewModel.toggleBookmark(currentPhoto.id) }
        )
    } else {
        // Paginación continua
        val shouldLoadMore = remember {
            derivedStateOf {
                val total = listState.layoutInfo.totalItemsCount
                val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                total > 0 && lastVisible >= total - 2
            }
        }

        LaunchedEffect(shouldLoadMore.value) {
            if (shouldLoadMore.value) {
                viewModel.loadNextPage()
            }
        }

    Scaffold(
        containerColor = Color.Black,
        topBar = {
            Column(modifier = Modifier.background(Color.Black)) {
                // Cabecera Superior Oficial de X: Avatar Perfil | Logo '𝕏' Blanco | Ajustes
                TopAppBar(
                    title = {
                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "𝕏",
                                fontWeight = FontWeight.Black,
                                fontSize = 22.sp,
                                color = Color.White
                            )
                        }
                    },
                    navigationIcon = {
                        Box(
                            modifier = Modifier
                                .padding(start = 14.dp)
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF1F1F1F))
                                .clickable { onNavigateToProfile() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = "Perfil",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = { /* Opciones */ }) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = "Destacados",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Black)
                )

                // Pestañas Oficiales: "Para ti" | "Siguiendo" con la barra azul de X (#1D9BF0)
                TabRow(
                    selectedTabIndex = if (selectedTab == "Para ti") 0 else 1,
                    containerColor = Color.Black,
                    contentColor = Color.White,
                    indicator = {},
                    divider = { HorizontalDivider(color = XDivider, thickness = 0.5.dp) }
                ) {
                    Tab(
                        selected = selectedTab == "Para ti",
                        onClick = { viewModel.selectTab("Para ti") },
                        text = {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "Para ti",
                                    fontWeight = if (selectedTab == "Para ti") FontWeight.Bold else FontWeight.Normal,
                                    color = if (selectedTab == "Para ti") Color.White else XTextSecondary,
                                    fontSize = 15.sp
                                )
                                if (selectedTab == "Para ti") {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Box(
                                        modifier = Modifier
                                            .width(56.dp)
                                            .height(3.5.dp)
                                            .clip(RoundedCornerShape(2.dp))
                                            .background(XBlue)
                                    )
                                }
                            }
                        }
                    )
                    Tab(
                        selected = selectedTab == "Siguiendo",
                        onClick = { viewModel.selectTab("Siguiendo") },
                        text = {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "Siguiendo",
                                    fontWeight = if (selectedTab == "Siguiendo") FontWeight.Bold else FontWeight.Normal,
                                    color = if (selectedTab == "Siguiendo") Color.White else XTextSecondary,
                                    fontSize = 15.sp
                                )
                                if (selectedTab == "Siguiendo") {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Box(
                                        modifier = Modifier
                                            .width(68.dp)
                                            .height(3.5.dp)
                                            .clip(RoundedCornerShape(2.dp))
                                            .background(XBlue)
                                    )
                                }
                            }
                        }
                    )
                }
            }
        },
        floatingActionButton = {
            // Botón Flotante de Redactar Tweet (FAB azul oficial de X)
            FloatingActionButton(
                onClick = { /* Redactar */ },
                containerColor = XBlue,
                contentColor = Color.White,
                shape = CircleShape,
                modifier = Modifier
                    .padding(bottom = 12.dp, end = 4.dp)
                    .size(54.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = "Publicar",
                    modifier = Modifier.size(24.dp)
                )
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
                is XFeedUiState.Loading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = XBlue)
                    }
                }
                is XFeedUiState.Error -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                            Text(text = s.message, color = XTextSecondary, fontSize = 14.sp)
                            Spacer(modifier = Modifier.height(14.dp))
                            Button(
                                onClick = { viewModel.loadFeed(1) },
                                colors = ButtonDefaults.buttonColors(containerColor = XBlue, contentColor = Color.White),
                                shape = RoundedCornerShape(20.dp)
                            ) {
                                Text("Reintentar", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
                is XFeedUiState.Success -> {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 80.dp)
                    ) {
                        items(s.posts, key = { it.id }) { post ->
                            AuthenticXPostCard(
                                post = post,
                                onVideoClick = { clickedPost ->
                                    val videoPosts = s.posts.filter { it.videoItem != null }
                                    val idx = videoPosts.indexOfFirst { it.id == clickedPost.id }.coerceAtLeast(0)
                                    activeVideoList = videoPosts
                                    activeVideoIndex = idx
                                    viewModel.loadNextPage()
                                },
                                onPhotoClick = { clickedPost, imgIdx ->
                                    activePhotoPost = clickedPost
                                    activePhotoIndex = imgIdx
                                },
                                onToggleLike = { viewModel.toggleLike(post.id) },
                                onToggleRepost = { viewModel.toggleRepost(post.id) },
                                onToggleBookmark = { viewModel.toggleBookmark(post.id) }
                            )
                        }
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(color = XBlue, modifier = Modifier.size(28.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}
}

/**
 * Tarjeta de Tweet calcada al 100% de la app oficial de X (Twitter)
 */
@Composable
fun AuthenticXPostCard(
    post: XPostItem,
    onVideoClick: (XPostItem) -> Unit,
    onPhotoClick: (XPostItem, Int) -> Unit,
    onToggleLike: () -> Unit,
    onToggleRepost: () -> Unit,
    onToggleBookmark: () -> Unit
) {
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Black)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Top
        ) {
            // Avatar Circular de X
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(post.authorAvatarUrl)
                    .crossfade(true)
                    .build(),
                contentDescription = post.authorName,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF161616))
            )

            // Columna Principal del Tweet
            Column(modifier = Modifier.weight(1f)) {
                // Fila Superior: Nombre + Verificado Azul + @handle · tiempo + Menú (...)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = post.authorName,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (post.isVerified) {
                        Spacer(modifier = Modifier.width(3.dp))
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Verificado",
                            tint = XBlue,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = "@${post.authorHandle.removePrefix("@")}",
                        color = XTextSecondary,
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = " · ${post.timeAgo}",
                        color = XTextSecondary,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Icon(
                        imageVector = Icons.Default.MoreHoriz,
                        contentDescription = "Más opciones",
                        tint = XTextSecondary,
                        modifier = Modifier
                            .size(18.dp)
                            .clickable { /* Opciones */ }
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Texto del Tweet con Hashtags en Azul Twitter (#1D9BF0)
                var isExpandedText by remember { mutableStateOf(false) }
                val isLongText = post.content.length > 150

                val displayText = if (isLongText && !isExpandedText) {
                    post.content.take(130).trimEnd() + "..."
                } else {
                    post.content
                }

                val annotatedText = remember(displayText) {
                    buildAnnotatedString {
                        val words = displayText.split(" ")
                        words.forEachIndexed { idx, word ->
                            if (word.startsWith("#") || word.startsWith("@")) {
                                withStyle(style = SpanStyle(color = XBlue, fontWeight = FontWeight.Normal)) {
                                    append(word)
                                }
                            } else {
                                withStyle(style = SpanStyle(color = Color(0xFFE7E9EA))) {
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
                    lineHeight = 20.sp
                )

                // Enlace 'Mostrar más' ÚNICAMENTE en el 1% de publicaciones que realmente tienen texto largo por ver
                if (isLongText) {
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

                // Contenido Multimedia Adjunto (Video o Cuadrícula de Fotos con esquinas redondeadas 16dp)
                if (post.videoItem != null) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(16f / 10f)
                            .clip(RoundedCornerShape(16.dp))
                            .border(0.8.dp, XMediaBorder, RoundedCornerShape(16.dp))
                            .background(Color(0xFF0A0A0A))
                            .clickable { onVideoClick(post) }
                    ) {
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(post.videoItem.thumbUrl)
                                .crossfade(true)
                                .build(),
                            contentDescription = post.videoItem.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )

                        // Botón Central de Reproducir (Círculo negro traslúcido con triángulo blanco)
                        Box(
                            modifier = Modifier
                                .align(Alignment.Center)
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.65f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = "Reproducir",
                                tint = Color.White,
                                modifier = Modifier.size(34.dp)
                            )
                        }

                        // Duración en la Esquina Inferior Izquierda (ej. "0:34") idéntico a la captura
                        val duration = if (post.videoItem.durationText.isNotEmpty()) post.videoItem.durationText else "0:34"
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(10.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color(0xDD000000))
                                .padding(horizontal = 7.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = duration,
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                } else if (post.images.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    when (post.images.size) {
                        1 -> {
                            AsyncImage(
                                model = ImageRequest.Builder(context)
                                    .data(post.images[0])
                                    .crossfade(true)
                                    .build(),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 200.dp, max = 340.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .border(0.8.dp, XMediaBorder, RoundedCornerShape(16.dp))
                                    .background(Color(0xFF0A0A0A))
                                    .clickable { onPhotoClick(post, 0) }
                            )
                        }
                        2 -> {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(180.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .border(0.8.dp, XMediaBorder, RoundedCornerShape(16.dp)),
                                horizontalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                post.images.forEachIndexed { idx, imgUrl ->
                                    AsyncImage(
                                        model = ImageRequest.Builder(context)
                                            .data(imgUrl)
                                            .crossfade(true)
                                            .build(),
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .weight(1f)
                                            .fillMaxHeight()
                                            .background(Color(0xFF0A0A0A))
                                            .clickable { onPhotoClick(post, idx) }
                                    )
                                }
                            }
                        }
                        else -> {
                            // Cuadrícula 2x2 de fotos con bordes redondeados
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(16.dp))
                                    .border(0.8.dp, XMediaBorder, RoundedCornerShape(16.dp)),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(140.dp),
                                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    post.images.take(2).forEachIndexed { idx, imgUrl ->
                                        AsyncImage(
                                            model = ImageRequest.Builder(context)
                                                .data(imgUrl)
                                                .crossfade(true)
                                                .build(),
                                            contentDescription = null,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier
                                                .weight(1f)
                                                .fillMaxHeight()
                                                .background(Color(0xFF0A0A0A))
                                                .clickable { onPhotoClick(post, idx) }
                                        )
                                    }
                                }
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(140.dp),
                                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    post.images.drop(2).take(2).forEachIndexed { idx, imgUrl ->
                                        AsyncImage(
                                            model = ImageRequest.Builder(context)
                                                .data(imgUrl)
                                                .crossfade(true)
                                                .build(),
                                            contentDescription = null,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier
                                                .weight(1f)
                                                .fillMaxHeight()
                                                .background(Color(0xFF0A0A0A))
                                                .clickable { onPhotoClick(post, idx + 2) }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Fila Inferior de Acciones de X (Comentarios, Retweet, Like, Vistas, Guardar + Compartir)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 1. Comentarios (💬)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { /* Comentar */ }
                    ) {
                        Icon(
                            imageVector = Icons.Default.ChatBubbleOutline,
                            contentDescription = "Comentar",
                            tint = XTextSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = formatXCount(post.commentsCount),
                            color = XTextSecondary,
                            fontSize = 12.sp
                        )
                    }

                    // 2. Retweet / Repost (🔁 - Verde al activarse)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { onToggleRepost() }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Repeat,
                            contentDescription = "Repost",
                            tint = if (post.isReposted) XRepostGreen else XTextSecondary,
                            modifier = Modifier.size(17.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = formatXCount(post.repostsCount),
                            color = if (post.isReposted) XRepostGreen else XTextSecondary,
                            fontSize = 12.sp
                        )
                    }

                    // 3. Like (🤍 - Rosa/Rojo al dar Like)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { onToggleLike() }
                    ) {
                        Icon(
                            imageVector = if (post.isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = "Like",
                            tint = if (post.isLiked) XHeartPink else XTextSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = formatXCount(post.likesCount),
                            color = if (post.isLiked) XHeartPink else XTextSecondary,
                            fontSize = 12.sp
                        )
                    }

                    // 4. Vistas / Estadísticas (📊)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.BarChart,
                            contentDescription = "Vistas",
                            tint = XTextSecondary,
                            modifier = Modifier.size(17.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (post.viewsCount.isNotEmpty()) post.viewsCount else "54 mil",
                            color = XTextSecondary,
                            fontSize = 12.sp
                        )
                    }

                    // 5. Grupo Derecho: Marcador Guardar (🔖) + Compartir
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        IconButton(
                            onClick = {
                                onToggleBookmark()
                                val msg = if (!post.isBookmarked) "Guardado en tu perfil" else "Eliminado de guardados"
                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = if (post.isBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                                contentDescription = "Guardar",
                                tint = if (post.isBookmarked) XBlue else XTextSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                        }

                        IconButton(
                            onClick = {
                                val shareUrl = post.videoItem?.pageUrl?.takeIf { it.isNotBlank() }
                                    ?: "https://x.com/${post.authorHandle.removePrefix("@")}/status/${post.id}"
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clip = ClipData.newPlainText("𝕏 Post URL", shareUrl)
                                clipboard.setPrimaryClip(clip)
                                Toast.makeText(context, "Enlace copiado al portapapeles", Toast.LENGTH_SHORT).show()
                                try {
                                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_TEXT, "${post.content}\n\n$shareUrl")
                                    }
                                    context.startActivity(Intent.createChooser(shareIntent, "Compartir publicación"))
                                } catch (_: Exception) {}
                            },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = "Compartir",
                                tint = XTextSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }

        // Línea divisoria delgada oficial entre Tweets (0.5dp #2F3336)
        HorizontalDivider(
            color = XDivider,
            thickness = 0.5.dp
        )
    }
}

/**
 * Formateo numérico oficial de X en español (ej. "99", "2 mil", "54 mil", "561 mil")
 */
private fun formatXCount(count: Int): String {
    return when {
        count >= 1_000_000 -> String.format(Locale.US, "%.1f M", count / 1_000_000.0)
        count >= 1_000 -> "${count / 1000} mil"
        else -> count.toString()
    }
}

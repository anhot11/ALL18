package com.all18.nativeapp.ui.xfeed

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.all18.nativeapp.core.model.XPostItem
import java.util.Locale

private val XBlue = Color(0xFF1D9BF0)
private val XTextSecondary = Color(0xFF71767B)
private val XHeartPink = Color(0xFFF91880)
private val XRepostGreen = Color(0xFF00BA7C)
private val XBorder = Color(0xFF2F3336)

@Composable
fun XPhotoViewerScreen(
    post: XPostItem,
    initialImageIndex: Int = 0,
    onBack: () -> Unit,
    onToggleLike: () -> Unit,
    onToggleRepost: () -> Unit = {},
    onToggleBookmark: () -> Unit
) {
    val context = LocalContext.current
    var isUiVisible by remember { mutableStateOf(true) }
    var isFollowing by remember { mutableStateOf(true) }

    var isLiked by remember(post.id, post.isLiked) { mutableStateOf(post.isLiked) }
    var likesCount by remember(post.id, post.likesCount) { mutableIntStateOf(post.likesCount) }
    var isBookmarked by remember(post.id, post.isBookmarked) { mutableStateOf(post.isBookmarked) }

    val images = remember(post) {
        if (post.images.isNotEmpty()) post.images else listOf(post.authorAvatarUrl)
    }

    val pagerState = rememberPagerState(
        initialPage = initialImageIndex.coerceIn(0, (images.size - 1).coerceAtLeast(0)),
        pageCount = { images.size }
    )

    BackHandler(onBack = onBack)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                detectTapGestures(onTap = { isUiVisible = !isUiVisible })
            }
    ) {
        // 1. VISOR DE FOTOS CENTRAL CON HORIZONTAL PAGER
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(images[page])
                        .crossfade(true)
                        .build(),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        // 2. CABECERA SUPERIOR CALCADA A LA CAPTURA 2
        AnimatedVisibility(
            visible = isUiVisible,
            enter = fadeIn(tween(250)) + slideInVertically(initialOffsetY = { -it }),
            exit = fadeOut(tween(250)) + slideOutVertically(targetOffsetY = { -it }),
            modifier = Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.75f))
                .padding(top = 10.dp, bottom = 10.dp, start = 8.dp, end = 12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Botón Volver (Flecha izquierda)
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Volver",
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }

                // Avatar Circular del Autor
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(post.authorAvatarUrl)
                        .crossfade(true)
                        .build(),
                    contentDescription = post.authorName,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF161616))
                )

                Spacer(modifier = Modifier.width(10.dp))

                // Nombre, Verificado e Información del Handle
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
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

                // Botón 'Siguiendo' / 'Seguir' (Píldora blanca)
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
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
                    modifier = Modifier.height(32.dp)
                ) {
                    Text(
                        text = if (isFollowing) "Siguiendo" else "Seguir",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.width(4.dp))

                // Menú ⋮ Más opciones
                IconButton(
                    onClick = {
                        Toast.makeText(context, "Opciones de publicación", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "Más opciones",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        // 3. BARRA INFERIOR CALCADA A LA CAPTURA 2 (Estadísticas + 'Postea tu respuesta')
        AnimatedVisibility(
            visible = isUiVisible,
            enter = fadeIn(tween(250)) + slideInVertically(initialOffsetY = { it }),
            exit = fadeOut(tween(250)) + slideOutVertically(targetOffsetY = { it }),
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.85f))
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Fila 1: Indicador de Múltiples Imágenes (si hay más de 1)
                if (images.size > 1) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        images.forEachIndexed { idx, _ ->
                            Box(
                                modifier = Modifier
                                    .padding(horizontal = 3.dp)
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (pagerState.currentPage == idx) XBlue else Color(0xFF555555)
                                    )
                            )
                        }
                    }
                }

                // Fila 2: Acciones Oficiales de 𝕏 (❤️ Me gusta | 📊 Vistas | 🔖 Guardar | ➦ Compartir)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 1. Me gusta (❤️)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                val newLiked = !isLiked
                                isLiked = newLiked
                                likesCount = if (newLiked) likesCount + 1 else (likesCount - 1).coerceAtLeast(0)
                                onToggleLike()
                            }
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                    ) {
                        Icon(
                            imageVector = if (isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = "Me gusta",
                            tint = if (isLiked) XHeartPink else XTextSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = formatCount(likesCount),
                            color = if (isLiked) XHeartPink else XTextSecondary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    // 2. Vistas (📊)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.BarChart,
                            contentDescription = "Vistas",
                            tint = XTextSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = if (post.viewsCount.isNotEmpty()) post.viewsCount else "1.5K",
                            color = XTextSecondary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    // 3. Guardado (🔖)
                    IconButton(
                        onClick = {
                            val newBookmarked = !isBookmarked
                            isBookmarked = newBookmarked
                            onToggleBookmark()
                            val msg = if (newBookmarked) "Guardado en tu perfil" else "Eliminado de guardados"
                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = if (isBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                            contentDescription = "Guardar",
                            tint = if (isBookmarked) XBlue else XTextSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // 4. Compartir (➦)
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
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "Compartir",
                            tint = XTextSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Fila 3: Barra de Respuesta 'Postea tu respuesta' (Idéntica a Captura 2)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(24.dp))
                        .background(Color(0xFF16181C))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Avatar Usuario
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF2F3336)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = "Tu avatar",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Text(
                        text = "Postea tu respuesta",
                        color = XTextSecondary,
                        fontSize = 14.sp,
                        modifier = Modifier.weight(1f)
                    )

                    // Iconos Multimedia a la Derecha: Galería, GIF, Expandir
                    Icon(
                        imageVector = Icons.Default.Image,
                        contentDescription = "Imagen",
                        tint = XBlue,
                        modifier = Modifier
                            .size(20.dp)
                            .clickable { Toast.makeText(context, "Adjuntar imagen", Toast.LENGTH_SHORT).show() }
                    )

                    Spacer(modifier = Modifier.width(12.dp))

                    Icon(
                        imageVector = Icons.Default.Gif,
                        contentDescription = "GIF",
                        tint = XBlue,
                        modifier = Modifier
                            .size(22.dp)
                            .clickable { Toast.makeText(context, "Adjuntar GIF", Toast.LENGTH_SHORT).show() }
                    )

                    Spacer(modifier = Modifier.width(12.dp))

                    Icon(
                        imageVector = Icons.Default.OpenInFull,
                        contentDescription = "Expandir respuesta",
                        tint = XBlue,
                        modifier = Modifier
                            .size(18.dp)
                            .clickable { Toast.makeText(context, "Editor completo", Toast.LENGTH_SHORT).show() }
                    )
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

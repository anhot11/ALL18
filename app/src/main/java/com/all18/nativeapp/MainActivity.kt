package com.all18.nativeapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.all18.nativeapp.core.model.VideoItem
import com.all18.nativeapp.ui.alltube.AllTubeScreen
import com.all18.nativeapp.ui.alltube.AllTubeViewModel
import com.all18.nativeapp.ui.theme.*
import com.all18.nativeapp.ui.tiktok.TikTokScreen
import com.all18.nativeapp.ui.tiktok.TikTokViewModel
import com.all18.nativeapp.ui.profile.ProfileScreen
import com.all18.nativeapp.ui.profile.ProfileViewModel
import com.all18.nativeapp.ui.watch.WatchScreen
import com.all18.nativeapp.ui.xfeed.XFeedScreen
import com.all18.nativeapp.ui.xfeed.XFeedViewModel

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.lifecycle.lifecycleScope
import com.all18.nativeapp.core.repository.PhotoRepository
import com.all18.nativeapp.core.model.AdultSite
import com.all18.nativeapp.ui.directory.DirectoryScreen
import com.all18.nativeapp.ui.directory.DirectoryViewModel
import com.all18.nativeapp.ui.browser.InAppBrowserScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

enum class MainTab(val title: String) {
    AllTube("AllTube"),
    TikTok("TikTok"),
    Directory("Directorio"),
    X("𝕏"),
    Profile("Perfil")
}

class MainActivity : ComponentActivity() {
    private val allTubeViewModel: AllTubeViewModel by viewModels()
    private val tikTokViewModel: TikTokViewModel by viewModels()
    private val directoryViewModel: DirectoryViewModel by viewModels()
    private val xFeedViewModel: XFeedViewModel by viewModels()
    private val profileViewModel: ProfileViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize Coil ImageLoader with AppNetworkClient (DoH / ISP bypass for all thumbnails)
        val imageLoader = coil.ImageLoader.Builder(this)
            .okHttpClient(com.all18.nativeapp.core.network.AppNetworkClient.client)
            .build()
        coil.Coil.setImageLoader(imageLoader)

        // Preload creators and hot photo pools in background
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                PhotoRepository.preloadData()
            } catch (_: Exception) {}
        }

        setContent {
            All18NativeTheme {
                var selectedTab by remember { mutableStateOf(MainTab.AllTube) }
                var activeWatchingVideo by remember { mutableStateOf<VideoItem?>(null) }
                var activeInAppBrowserSite by remember { mutableStateOf<AdultSite?>(null) }
                var isFullscreenMediaActive by remember { mutableStateOf(false) }

                if (activeWatchingVideo != null) {
                    WatchScreen(
                        video = activeWatchingVideo!!,
                        onBack = { activeWatchingVideo = null }
                    )
                } else if (activeInAppBrowserSite != null) {
                    InAppBrowserScreen(
                        url = activeInAppBrowserSite!!.url,
                        title = activeInAppBrowserSite!!.name,
                        onClose = { activeInAppBrowserSite = null }
                    )
                } else {
                    Scaffold(
                        containerColor = AmoledBlack,
                        bottomBar = {
                            if (!isFullscreenMediaActive) {
                                Surface(
                                    color = Color.Black,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .navigationBarsPadding()
                                        .height(46.dp)
                                ) {
                                    Column(modifier = Modifier.fillMaxSize()) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(0.5.dp)
                                                .background(Color(0xFF1E1E1E))
                                        )
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .weight(1f),
                                            horizontalArrangement = Arrangement.SpaceAround,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            MainTab.entries.forEach { tab ->
                                                val isSelected = selectedTab == tab
                                                Box(
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .fillMaxHeight()
                                                        .clickable(
                                                            interactionSource = remember { MutableInteractionSource() },
                                                            indication = null
                                                        ) {
                                                            selectedTab = tab
                                                        },
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    when (tab) {
                                                        MainTab.AllTube -> {
                                                            Row(
                                                                verticalAlignment = Alignment.CenterVertically,
                                                                horizontalArrangement = Arrangement.Center
                                                            ) {
                                                                Text(
                                                                    text = "all",
                                                                    color = if (isSelected) Color.White else Color(0xFF777777),
                                                                    fontSize = 15.sp,
                                                                    fontWeight = FontWeight.Black,
                                                                    letterSpacing = (-0.4).sp
                                                                )
                                                                Spacer(modifier = Modifier.width(2.dp))
                                                                Box(
                                                                    modifier = Modifier
                                                                        .clip(RoundedCornerShape(3.dp))
                                                                        .background(if (isSelected) Color(0xFFFFA31A) else Color(0xFF442D00))
                                                                        .padding(horizontal = 4.dp, vertical = 1.dp),
                                                                    contentAlignment = Alignment.Center
                                                                ) {
                                                                    Text(
                                                                        text = "tube",
                                                                        color = if (isSelected) Color.Black else Color(0xFF181818),
                                                                        fontSize = 11.sp,
                                                                        fontWeight = FontWeight.Black,
                                                                        letterSpacing = (-0.4).sp
                                                                    )
                                                                }
                                                            }
                                                        }
                                                        MainTab.TikTok -> {
                                                            if (isSelected) {
                                                                Icon(
                                                                    painter = painterResource(id = R.drawable.ic_tiktok_color),
                                                                    contentDescription = "TikTok",
                                                                    modifier = Modifier.size(24.dp),
                                                                    tint = Color.Unspecified
                                                                )
                                                            } else {
                                                                Icon(
                                                                    painter = painterResource(id = R.drawable.ic_tiktok),
                                                                    contentDescription = "TikTok",
                                                                    modifier = Modifier.size(24.dp),
                                                                    tint = Color(0xFF777777)
                                                                )
                                                            }
                                                        }
                                                        MainTab.Directory -> {
                                                            Icon(
                                                                imageVector = Icons.Default.Explore,
                                                                contentDescription = "Directorio",
                                                                modifier = Modifier.size(24.dp),
                                                                tint = if (isSelected) PornhubOrange else Color(0xFF777777)
                                                            )
                                                        }
                                                        MainTab.X -> {
                                                            Icon(
                                                                painter = painterResource(id = R.drawable.ic_x_logo),
                                                                contentDescription = "𝕏",
                                                                modifier = Modifier.size(20.dp),
                                                                tint = if (isSelected) Color.White else Color(0xFF777777)
                                                            )
                                                        }
                                                        MainTab.Profile -> {
                                                            Icon(
                                                                imageVector = Icons.Default.Person,
                                                                contentDescription = "Perfil",
                                                                modifier = Modifier.size(24.dp),
                                                                tint = if (isSelected) PornhubOrange else Color(0xFF777777)
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                    }
                    ) { innerPadding ->
                        val saveableStateHolder = rememberSaveableStateHolder()
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(if (isFullscreenMediaActive) PaddingValues(0.dp) else innerPadding)
                                .background(AmoledBlack)
                        ) {
                            saveableStateHolder.SaveableStateProvider(key = selectedTab) {
                                when (selectedTab) {
                                    MainTab.AllTube -> {
                                        AllTubeScreen(
                                            viewModel = allTubeViewModel,
                                            onNavigateToProfile = { selectedTab = MainTab.Profile },
                                            onNavigateToDirectory = { selectedTab = MainTab.Directory },
                                            onVideoClick = { video -> activeWatchingVideo = video }
                                        )
                                    }
                                    MainTab.TikTok -> {
                                        TikTokScreen(
                                            viewModel = tikTokViewModel
                                        )
                                    }
                                    MainTab.Directory -> {
                                        DirectoryScreen(
                                            viewModel = directoryViewModel,
                                            onOpenSite = { site -> activeInAppBrowserSite = site }
                                        )
                                    }
                                    MainTab.X -> {
                                        XFeedScreen(
                                            viewModel = xFeedViewModel,
                                            onNavigateToProfile = { selectedTab = MainTab.Profile },
                                            onFullscreenMediaChange = { isFullscreen ->
                                                isFullscreenMediaActive = isFullscreen
                                            },
                                            onVideoClick = { video -> activeWatchingVideo = video }
                                        )
                                    }
                                    MainTab.Profile -> {
                                        ProfileScreen(
                                            viewModel = profileViewModel,
                                            onVideoClick = { video -> activeWatchingVideo = video },
                                            onNavigateToAllTube = { selectedTab = MainTab.AllTube },
                                            onNavigateToTikTok = { selectedTab = MainTab.TikTok }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

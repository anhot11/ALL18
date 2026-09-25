package com.all18.nativeapp.core.player

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.all18.nativeapp.core.network.AppNetworkClient

/**
 * Native Media3 ExoPlayer manager with guaranteed silent playback (volume = 0f)
 * and resilient network routing via AppNetworkClient (DoH / ISP bypass).
 */
class SilentPlayerManager(context: Context) {
    val player: ExoPlayer = createSilentPlayer(context)

    fun playStream(url: String) {
        val mediaItem = MediaItem.fromUri(url)
        player.setMediaItem(mediaItem)
        player.prepare()
        player.playWhenReady = true
    }

    fun pause() {
        player.pause()
    }

    fun release() {
        player.release()
    }

    companion object {
        fun createSilentPlayer(context: Context): ExoPlayer {
            val dataSourceFactory = OkHttpDataSource.Factory(AppNetworkClient.client)
            val mediaSourceFactory = DefaultMediaSourceFactory(context)
                .setDataSourceFactory(dataSourceFactory)

            return ExoPlayer.Builder(context)
                .setMediaSourceFactory(mediaSourceFactory)
                .build().apply {
                    volume = 0f // HARD MUTED - 0.0 Sound guaranteed
                    repeatMode = ExoPlayer.REPEAT_MODE_ONE
                }
        }
    }
}

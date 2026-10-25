package ru.truepath.app.ui.components

import android.view.LayoutInflater
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.AudioAttributes
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import ru.truepath.app.R
import ru.truepath.app.domain.ArticleBlock
import ru.truepath.app.domain.isSafeWebUrl
import ru.truepath.app.ui.theme.LocalTruePathPalette

@Composable
fun InlineVideo(block: ArticleBlock.Video) {
    val palette = LocalTruePathPalette.current
    val inspection = LocalInspectionMode.current
    var requested by rememberSaveable(block.url) { mutableStateOf(false) }
    var position by rememberSaveable(block.url) { mutableLongStateOf(0L) }
    var resumeOnCreate by rememberSaveable(block.url) { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9).clip(RoundedCornerShape(6.dp)).background(Color(0xFF101612))) {
            AnimatedContent(requested && !inspection && isSafeWebUrl(block.url),
                transitionSpec = { fadeIn(tween(220)).togetherWith(fadeOut(tween(140))) }, label = "video-start") { active ->
                if (active) ActiveVideo(block.url, position, resumeOnCreate, onRelease = {
                    position = it; resumeOnCreate = false
                }) else Box(Modifier.fillMaxSize().clickable(enabled = isSafeWebUrl(block.url)) {
                    resumeOnCreate = true; requested = true
                }, contentAlignment = Alignment.Center) {
                    block.poster?.let { RemotePhoto(it, Modifier.fillMaxSize()) }
                    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .25f)))
                    Surface(color = Color(0xFFFFFEFA), shape = RoundedCornerShape(8.dp)) {
                        Icon(Icons.Outlined.PlayArrow, "Воспроизвести ${block.title}", Modifier.padding(13.dp).size(25.dp), tint = Color(0xFF202925))
                    }
                }
            }
        }
        Text(block.title, style = MaterialTheme.typography.titleMedium)
        if (block.caption.isNotBlank()) Text(block.caption, style = MaterialTheme.typography.bodySmall, color = palette.muted)
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun ActiveVideo(url: String, position: Long, autoplay: Boolean, onRelease: (Long) -> Unit) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var loading by remember(url) { mutableStateOf(true) }
    var playing by remember(url) { mutableStateOf(false) }
    var error by remember(url) { mutableStateOf<String?>(null) }
    val latestRelease by rememberUpdatedState(onRelease)
    val player = remember(url, context) {
        ExoPlayer.Builder(context.applicationContext).build().apply {
            setAudioAttributes(AudioAttributes.DEFAULT, true)
            setHandleAudioBecomingNoisy(true)
            setMediaItem(MediaItem.fromUri(url))
            seekTo(position)
            playWhenReady = autoplay
        }
    }
    DisposableEffect(player, lifecycle) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) { loading = state == Player.STATE_BUFFERING }
            override fun onIsPlayingChanged(isPlaying: Boolean) { playing = isPlaying }
            override fun onPlayerError(problem: PlaybackException) {
                loading = false
                error = "Не удалось воспроизвести видео. Проверьте подключение или откройте оригинал."
            }
        }
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP) player.pause()
        }
        player.addListener(listener)
        lifecycle.addObserver(observer)
        player.prepare()
        onDispose {
            latestRelease(player.currentPosition.coerceAtLeast(0))
            lifecycle.removeObserver(observer)
            player.removeListener(listener)
            player.release()
        }
    }
    Box(Modifier.fillMaxSize()) {
        AndroidView(
            factory = { LayoutInflater.from(it).inflate(R.layout.view_inline_video, null, false) as PlayerView },
            modifier = Modifier.fillMaxSize(),
            onRelease = { it.player = null; it.keepScreenOn = false },
            update = { it.player = player; it.keepScreenOn = playing },
        )
        if (loading && error == null) CircularProgressIndicator(Modifier.align(Alignment.Center).size(28.dp), color = Color.White, strokeWidth = 2.dp)
        if (error != null) Column(Modifier.fillMaxSize().background(Color(0xEE101612)).padding(14.dp),
            verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            Text(error.orEmpty(), style = MaterialTheme.typography.bodySmall, color = Color.White)
            Row {
                TextButton(onClick = { error = null; loading = true; player.prepare(); player.play() }) { Text("Повторить", color = Color.White) }
                TextButton(onClick = { openWebLink(context, url) }) { Text("Оригинал", color = Color.White) }
            }
        }
    }
}

package ru.truepath.app.ui.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import coil.request.ImageRequest
import ru.truepath.app.R
import ru.truepath.app.domain.ArticleBlock
import ru.truepath.app.domain.isSafeWebUrl
import ru.truepath.app.ui.theme.*

private fun previewResource(photo: ArticleBlock.Photo) = when (photo.localPreview) {
    "event" -> R.drawable.cover_event
    "science" -> R.drawable.cover_science
    "city" -> R.drawable.cover_city
    else -> null
}

@Composable
fun RemotePhoto(photo: ArticleBlock.Photo, modifier: Modifier = Modifier, contentScale: ContentScale = ContentScale.Crop) {
    val context = LocalContext.current
    val preview = previewResource(photo)
    var attempt by remember(photo.url) { mutableIntStateOf(0) }
    var failed by remember(photo.url, attempt) { mutableStateOf(false) }
    var loading by remember(photo.url, attempt) { mutableStateOf(true) }
    val inspection = LocalInspectionMode.current
    Box(modifier.background(Line.copy(alpha = .55f))) {
        if (preview != null) Image(painterResource(preview), photo.description, Modifier.matchParentSize(), contentScale = contentScale)
        if (!inspection && isSafeWebUrl(photo.url)) key(photo.url, attempt) {
            AsyncImage(
                model = ImageRequest.Builder(context).data(photo.url).crossfade(180).build(),
                contentDescription = photo.description, modifier = Modifier.matchParentSize(), contentScale = contentScale,
                onLoading = { loading = true; failed = false },
                onSuccess = { loading = false; failed = false },
                onError = { loading = false; failed = true },
            )
        }
        if (preview == null && (inspection || failed || !isSafeWebUrl(photo.url))) {
            Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Outlined.ImageNotSupported, null, tint = Muted)
                Text("Фото недоступно", style = MaterialTheme.typography.bodySmall, color = Muted)
                if (!inspection && isSafeWebUrl(photo.url)) TextButton(onClick = { attempt++ }) { Text("Повторить") }
            }
        } else if (preview == null && loading) CircularProgressIndicator(Modifier.align(Alignment.Center).size(23.dp), strokeWidth = 2.dp)
        if (preview != null && failed) Surface(Modifier.align(Alignment.BottomEnd).padding(7.dp), color = Paper.copy(alpha = .9f), shape = RoundedCornerShape(3.dp)) {
            Text("Сохранённая иллюстрация", Modifier.padding(horizontal = 6.dp, vertical = 3.dp), style = MaterialTheme.typography.labelSmall, color = Muted)
        }
    }
}

fun openWebLink(context: Context, url: String, video: Boolean = false) {
    if (!isSafeWebUrl(url)) {
        Toast.makeText(context, "Ссылка недоступна", Toast.LENGTH_SHORT).show()
        return
    }
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
    if (video) intent.setDataAndType(Uri.parse(url), "video/mp4")
    try { context.startActivity(intent) }
    catch (_: Exception) {
        if (video) runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
            .onFailure { Toast.makeText(context, "Нет приложения для открытия видео", Toast.LENGTH_SHORT).show() }
        else Toast.makeText(context, "Не удалось открыть ссылку", Toast.LENGTH_SHORT).show()
    }
}

@Composable
fun RichArticleText(text: String) {
    val context = LocalContext.current
    val annotated = remember(text) {
        buildAnnotatedString {
            var cursor = 0
            Regex("https://[^\\s<>]+") .findAll(text).forEach { match ->
                val url = match.value.trimEnd('.', ',', ')', '»')
                append(text.substring(cursor, match.range.first))
                if (isSafeWebUrl(url)) withLink(LinkAnnotation.Clickable(
                    tag = url,
                    styles = TextLinkStyles(SpanStyle(color = Accent, textDecoration = TextDecoration.Underline)),
                    linkInteractionListener = { openWebLink(context, url) },
                )) { append(url) } else append(url)
                append(match.value.drop(url.length))
                cursor = match.range.last + 1
            }
            append(text.substring(cursor))
        }
    }
    androidx.compose.foundation.text.selection.SelectionContainer {
        Text(annotated, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
fun ArticleContentBlock(block: ArticleBlock, onPhoto: (ArticleBlock.Photo) -> Unit) {
    val context = LocalContext.current
    when (block) {
        is ArticleBlock.Paragraph -> RichArticleText(block.text)
        is ArticleBlock.Photo -> Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
            RemotePhoto(block, Modifier.fillMaxWidth().aspectRatio(1.5f).clickable { onPhoto(block) })
            Text(listOf(block.caption, block.credit).filter { it.isNotBlank() }.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = Muted)
        }
        is ArticleBlock.Video -> Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Box(Modifier.fillMaxWidth().aspectRatio(16f / 9).background(Ink).clickable { openWebLink(context, block.url, video = true) }, contentAlignment = Alignment.Center) {
                block.poster?.let { RemotePhoto(it, Modifier.fillMaxSize()) }
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .25f)))
                Surface(color = Paper, shape = RoundedCornerShape(8.dp)) { Icon(Icons.Outlined.PlayArrow, "Воспроизвести ${block.title}", Modifier.padding(13.dp).size(25.dp), tint = Ink) }
            }
            Text(block.title, style = MaterialTheme.typography.titleMedium)
            if (block.caption.isNotBlank()) Text(block.caption, style = MaterialTheme.typography.bodySmall, color = Muted)
        }
        is ArticleBlock.Link -> Surface(color = Color.Transparent, border = androidx.compose.foundation.BorderStroke(1.dp, Line), shape = RoundedCornerShape(6.dp)) {
            Row(Modifier.fillMaxWidth().clickable { openWebLink(context, block.url) }.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(13.dp)) {
                Icon(Icons.Outlined.NorthEast, null, Modifier.size(20.dp), tint = Accent)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(block.title, style = MaterialTheme.typography.titleMedium)
                    if (block.description.isNotBlank()) Text(block.description, style = MaterialTheme.typography.bodySmall, color = Muted)
                    Text(runCatching { Uri.parse(block.url).host }.getOrNull().orEmpty(), style = MaterialTheme.typography.labelSmall, color = Accent)
                }
            }
        }
    }
}

@Composable
fun PhotoViewer(photo: ArticleBlock.Photo, onDismiss: () -> Unit) {
    var zoom by remember(photo.url) { mutableFloatStateOf(1f) }
    var pan by remember(photo.url) { mutableStateOf(Offset.Zero) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Box(Modifier.fillMaxSize().background(Color(0xFF111713))) {
            RemotePhoto(photo, Modifier.fillMaxSize().padding(vertical = 80.dp)
                .pointerInput(photo.url) { detectTransformGestures { _, delta, scale, _ -> zoom = (zoom * scale).coerceIn(1f, 4f); pan = if (zoom > 1f) pan + delta else Offset.Zero } }
                .graphicsLayer { scaleX = zoom; scaleY = zoom; translationX = pan.x; translationY = pan.y }, ContentScale.Fit)
            IconButton(onClick = onDismiss, modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(12.dp)) { Icon(Icons.Outlined.Close, "Закрыть фото", tint = Paper) }
            Column(Modifier.align(Alignment.BottomStart).navigationBarsPadding().padding(24.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(photo.caption, style = MaterialTheme.typography.bodySmall, color = Paper)
                if (photo.credit.isNotBlank()) Text(photo.credit, style = MaterialTheme.typography.labelSmall, color = Paper.copy(alpha = .6f))
            }
        }
    }
}

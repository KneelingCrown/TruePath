package ru.truepath.app.domain

/** Ordered blocks preserve the placement of media inside an article. */
sealed interface ArticleBlock {
    data class Paragraph(val text: String) : ArticleBlock
    data class Photo(val url: String, val description: String, val caption: String = "", val credit: String = "", val localPreview: String = "") : ArticleBlock
    data class Video(val url: String, val title: String, val poster: Photo? = null, val caption: String = "") : ArticleBlock
    data class Link(val url: String, val title: String, val description: String = "") : ArticleBlock
}

fun isSafeWebUrl(value: String): Boolean = runCatching {
    val uri = java.net.URI(value)
    uri.scheme.equals("https", ignoreCase = true) && !uri.host.isNullOrBlank() && uri.userInfo == null
}.getOrDefault(false)

package ru.truepath.app.domain

import org.junit.Assert.*
import org.junit.Test
import ru.truepath.app.data.DemoNews

class NewsContentTest {
    @Test fun `web media and links allow https but reject executable and malformed urls`() {
        assertTrue(isSafeWebUrl("https://example.org/news?story=1#photo"))
        assertTrue(isSafeWebUrl("HTTPS://example.org/video.mp4"))
        listOf("", "javascript:alert(1)", "intent://example", "file:///sdcard/a", "content://a", "http://example.org", "https://", "https://user:secret@example.org", "https://example.org/bad path").forEach {
            assertFalse(it, isSafeWebUrl(it))
        }
    }

    @Test fun `demo articles preserve ordered mixed content and usable image fallbacks`() {
        DemoNews.articles.forEach { article ->
            assertNotNull(article.cover)
            assertTrue(isSafeWebUrl(article.cover!!.url))
            assertTrue(article.cover.localPreview.isNotBlank())
            assertTrue(article.body.first() is ArticleBlock.Paragraph)
            assertTrue(article.body.any { it is ArticleBlock.Photo })
            assertTrue(article.body.any { it is ArticleBlock.Video && isSafeWebUrl(it.url) })
            assertTrue(article.body.any { it is ArticleBlock.Link && isSafeWebUrl(it.url) })
        }
    }
}

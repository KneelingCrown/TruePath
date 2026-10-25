package ru.truepath.app.ui

import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalInspectionMode
import org.junit.Rule
import org.junit.Test
import ru.truepath.app.AppState
import ru.truepath.app.AppTab
import ru.truepath.app.data.DemoNews
import ru.truepath.app.domain.*
import ru.truepath.app.ui.screens.*
import ru.truepath.app.ui.theme.TruePathTheme

/** Real Compose layouts rendered on the host: no emulator or APK. */
class DesignSnapshotTest {
    @get:Rule val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_5,
        theme = "android:Theme.Material.Light.NoActionBar",
        showSystemUi = false,
    )

    private val article = DemoNews.articles.first()
    private fun completeSession(): ResearchSession {
        val nodes = listOf(
            GraphNode("material", article.title, NodeKind.MATERIAL),
            GraphNode("claim", article.claim, NodeKind.CLAIM, TrustStatus.CONTRADICTED),
            GraphNode("question", "Контекст найден", NodeKind.CLAIM, TrustStatus.SUPPORTED),
            GraphNode("source", "Полный документ", NodeKind.SOURCE),
            GraphNode("retelling", "Короткий пересказ", NodeKind.SOURCE),
            GraphNode("evidence", "Важное ограничение", NodeKind.EVIDENCE, TrustStatus.SUPPORTED,
                description = "В полном объявлении уточняется область действия переноса.",
                quote = article.evidence, sourceLabel = "Учебный первоисточник"),
        )
        val edges = listOf("material" to "claim", "claim" to "question", "source" to "evidence", "evidence" to "claim", "source" to "question", "retelling" to "claim")
            .mapIndexed { i, (from, to) -> GraphEdge("edge:$i", from, to, "") }
        return ResearchSession("preview", article.title, article.summary, 1792918800000L, scenarioId = article.id,
            nodes = nodes, edges = edges, status = RunStatus.COMPLETE, summary = article.conclusion, planB = article.planB)
    }

    @Test fun feed() = paparazzi.snapshot {
        CompositionLocalProvider(LocalInspectionMode provides true) {
            TruePathTheme { AppShell(AppTab.FEED, {}, {}) { FeedScreen(AppState(loading = false), {}, {}) } }
        }
    }

    @Test fun graphConclusion() = paparazzi.snapshot {
        CompositionLocalProvider(LocalInspectionMode provides true) {
            TruePathTheme { GraphScreen(completeSession(), {}, {}, {}, {}) }
        }
    }

    @Test fun graphAnswer() = paparazzi.snapshot {
        CompositionLocalProvider(LocalInspectionMode provides true) {
            TruePathTheme { GraphScreen(completeSession(), {}, {}, {}, {}, initialMenuOpen = true) }
        }
    }

    @Test fun history() = paparazzi.snapshot {
        TruePathTheme {
            AppShell(AppTab.HISTORY, {}, {}) {
                HistoryScreen(AppState(loading = false, sessions = listOf(completeSession())), {}, {}, {}, {})
            }
        }
    }

    @Test fun article() = paparazzi.snapshot {
        CompositionLocalProvider(LocalInspectionMode provides true) {
            TruePathTheme { NewsReader(article, {}, {}) }
        }
    }

    @Test fun check() = paparazzi.snapshot {
        TruePathTheme {
            AppShell(AppTab.CHECK, {}, {}) {
                CheckScreen(AppState(loading = false), {}, {}, {}, {}, {})
            }
        }
    }
}

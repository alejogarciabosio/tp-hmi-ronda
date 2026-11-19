package ar.edu.uade.ronda.ui.components

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.createComposeRule
import ar.edu.uade.ronda.ui.model.StaleBannerUi
import ar.edu.uade.ronda.ui.theme.RondaTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** TalkBack: la región viva no cambia con cada segundo, solo entre SIN DATOS y DESCONECTADO. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class StaleDataBannerUiTest {
    @get:Rule val rule = createComposeRule()

    private fun liveText(): String {
        val nodes = rule.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.LiveRegion)).fetchSemanticsNodes()
        assertEquals(1, nodes.size)
        val c = nodes.single().config
        return c.getOrNull(SemanticsProperties.ContentDescription).orEmpty().joinToString() +
            c.getOrNull(SemanticsProperties.Text).orEmpty().joinToString { it.text }
    }

    @Test fun laRegionVivaNoIncluyeLosSegundos() {
        var ui by mutableStateOf(StaleBannerUi(4))
        rule.setContent { RondaTheme { StaleDataBanner(ui) } }
        val at4 = liveText()
        ui = StaleBannerUi(7); rule.waitForIdle()
        assertEquals(at4, liveText())
        ui = StaleBannerUi(12); rule.waitForIdle()
        val at12 = liveText()
        assertNotEquals(at4, at12)
        ui = StaleBannerUi(31, "Política declarada"); rule.waitForIdle()
        assertEquals(at12, liveText())
    }
}

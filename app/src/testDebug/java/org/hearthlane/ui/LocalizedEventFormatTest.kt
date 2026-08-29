package org.hearthlane.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Robolectric/Compose tests for the localized [formattedEventTime] and
 * [formattedEventDateTime] wrappers. The qualifiers pin the simulated device
 * locale, so the rendered layout and month names are verified for English
 * (default) and Portuguese (Brazil) independently of the machine timezone (a
 * fixed UTC zone is injected for deterministic output).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LocalizedEventFormatTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val epoch = 1787072293.5

    @Test
    fun `english locale renders the english time and datetime formats`() {
        composeTestRule.setContent {
            Column {
                Text(formattedEventTime(epoch, zone = EventFormat.utcZone()))
                Text(formattedEventDateTime(epoch, zone = EventFormat.utcZone()))
            }
        }
        composeTestRule.onNodeWithText("16:58").assertExists()
        composeTestRule.onNodeWithText("Aug 18, 2026 · 16:58").assertExists()
    }

    @Test
    @Config(qualifiers = "pt-rBR")
    fun `pt-br locale renders the portuguese time and datetime formats`() {
        composeTestRule.setContent {
            Column {
                Text(formattedEventTime(epoch, zone = EventFormat.utcZone()))
                Text(formattedEventDateTime(epoch, zone = EventFormat.utcZone()))
            }
        }
        composeTestRule.onNodeWithText("16:58").assertExists()
        composeTestRule.onNodeWithText("18 de agosto de 2026 · 16:58").assertExists()
    }
}

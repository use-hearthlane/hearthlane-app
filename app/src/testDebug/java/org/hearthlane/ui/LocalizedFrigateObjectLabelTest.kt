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
 * Robolectric/Compose tests for [localizedFrigateObjectLabel]. The qualifiers
 * pin the simulated device locale, so the tests verify English and Portuguese
 * (Brazil) independently of the machine running them.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LocalizedFrigateObjectLabelTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `english locale renders english labels`() {
        composeTestRule.setContent {
            Column {
                Text(localizedFrigateObjectLabel("person"))
                Text(localizedFrigateObjectLabel("car"))
                Text(localizedFrigateObjectLabel("dog"))
                Text(localizedFrigateObjectLabel("truck"))
            }
        }
        composeTestRule.onNodeWithText("Person").assertExists()
        composeTestRule.onNodeWithText("Car").assertExists()
        composeTestRule.onNodeWithText("Dog").assertExists()
        composeTestRule.onNodeWithText("Truck").assertExists()
    }

    @Test
    @Config(qualifiers = "pt-rBR")
    fun `portuguese locale renders portuguese labels`() {
        composeTestRule.setContent {
            Column {
                Text(localizedFrigateObjectLabel("person"))
                Text(localizedFrigateObjectLabel("dog"))
                Text(localizedFrigateObjectLabel("cat"))
                Text(localizedFrigateObjectLabel("bird"))
            }
        }
        composeTestRule.onNodeWithText("Pessoa").assertExists()
        composeTestRule.onNodeWithText("Cachorro").assertExists()
        composeTestRule.onNodeWithText("Gato").assertExists()
        composeTestRule.onNodeWithText("Pássaro").assertExists()
    }

    @Test
    fun `unknown labels fall back to the original value`() {
        composeTestRule.setContent { Text(localizedFrigateObjectLabel("horse")) }
        composeTestRule.onNodeWithText("horse").assertExists()
    }

    @Test
    fun `null labels fall back to the generic activity text`() {
        composeTestRule.setContent { Text(localizedFrigateObjectLabel(null)) }
        composeTestRule.onNodeWithText("Activity").assertExists()
    }
}

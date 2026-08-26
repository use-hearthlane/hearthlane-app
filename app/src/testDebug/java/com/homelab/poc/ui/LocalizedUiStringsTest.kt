package com.homelab.poc.ui

import androidx.test.core.app.ApplicationProvider
import com.homelab.poc.R
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Resource-level coverage for the UI strings that back the main screens
 * (titles, Settings, events, empty/error/loading states, accessibility text).
 * Qualifiers pin the simulated device locale so English (default) and
 * Portuguese (Brazil) are verified independently of the machine running the
 * tests. Dynamic data (camera names, zones, ids, URLs) is intentionally absent:
 * it is never localized.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LocalizedUiStringsTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Test
    fun `english default provides english ui strings`() {
        assertEquals("Cameras", context.getString(R.string.home_title))
        assertEquals("Settings", context.getString(R.string.settings_screen_title))
        assertEquals("Server settings", context.getString(R.string.settings_title))
        assertEquals("Initial setup", context.getString(R.string.setup_title))
        assertEquals("Diagnostics", context.getString(R.string.diagnostics_title))
        assertEquals("Recent events", context.getString(R.string.recent_events_title))
        assertEquals("Event", context.getString(R.string.event_detail_title))
        assertEquals("Loading settings…", context.getString(R.string.settings_loading))
        assertEquals("Tailscale", context.getString(R.string.settings_tailscale_title))
        assertEquals("No recent events.", context.getString(R.string.recent_events_empty))
        assertEquals("Could not load events.", context.getString(R.string.recent_events_error))
        assertEquals("In progress", context.getString(R.string.event_in_progress))
        assertEquals("Duration", context.getString(R.string.event_detail_duration))
        assertEquals("Retry", context.getString(R.string.retry_button))
        assertEquals("MMM d, HH:mm", context.getString(R.string.event_time_format))
        assertEquals("MMM d, yyyy HH:mm:ss", context.getString(R.string.event_datetime_format))
    }

    @Test
    @Config(qualifiers = "pt-rBR")
    fun `pt-br locale provides portuguese ui strings`() {
        assertEquals("Câmeras", context.getString(R.string.home_title))
        assertEquals("Configurações", context.getString(R.string.settings_screen_title))
        assertEquals("Configurações do servidor", context.getString(R.string.settings_title))
        assertEquals("Configuração inicial", context.getString(R.string.setup_title))
        assertEquals("Diagnóstico", context.getString(R.string.diagnostics_title))
        assertEquals("Eventos recentes", context.getString(R.string.recent_events_title))
        assertEquals("Evento", context.getString(R.string.event_detail_title))
        assertEquals("Carregando configurações…", context.getString(R.string.settings_loading))
        assertEquals("Tailscale", context.getString(R.string.settings_tailscale_title))
        assertEquals("Nenhum evento recente.", context.getString(R.string.recent_events_empty))
        assertEquals("Não foi possível carregar os eventos.", context.getString(R.string.recent_events_error))
        assertEquals("Em andamento", context.getString(R.string.event_in_progress))
        assertEquals("Duração", context.getString(R.string.event_detail_duration))
        assertEquals("Tentar novamente", context.getString(R.string.retry_button))
        assertEquals("d 'de' MMM, HH:mm", context.getString(R.string.event_time_format))
        assertEquals("d 'de' MMM 'de' yyyy, HH:mm:ss", context.getString(R.string.event_datetime_format))
    }
}

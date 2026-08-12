package com.homelab.poc.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.security.SecureRandom

private val Context.appSettingsDataStore by preferencesDataStore(name = "app_settings")

/**
 * Persisted application settings (Preferences DataStore).
 *
 * V1 classification (see docs/V1.md section 12): the values held here are
 * configuration, not secrets — the Frigate URL and the auto-generated node
 * hostname suffix. They are deliberately stored plaintext with the standard
 * Android preferences mechanism; no encryption is added. Tailscale node
 * identity stays in `filesDir/tailscale` and is never written here.
 *
 * The [nodeSuffix] is the stable per-installation anchor of the embedded node
 * hostname: it is generated once on first run and reused forever, so the node
 * keeps its name (and therefore its enrolled identity) across app restarts.
 * It is an internal value, never part of the normal configuration flow.
 */
class AppSettings(
    private val dataStore: DataStore<Preferences>,
    private val defaultBaseUrl: String,
    private val scope: CoroutineScope,
) {
    /** True once the persisted values have been loaded into the flows below. */
    private val _ready = MutableStateFlow(false)
    val ready: StateFlow<Boolean> = _ready.asStateFlow()

    /** Frigate server URL. Seeded from the build-time default on first run. */
    private val _baseUrl = MutableStateFlow("")
    val baseUrl: StateFlow<String> = _baseUrl.asStateFlow()

    /** Auto-generated node hostname suffix; generated once and persisted. */
    private val _nodeSuffix = MutableStateFlow("")
    val nodeSuffix: StateFlow<String> = _nodeSuffix.asStateFlow()

    /** True once the administrator completed the V1.1 initial setup. The app
     *  gates on this: setup shows until it is set, then normal use never asks
     *  about infrastructure again. */
    private val _setupComplete = MutableStateFlow(false)
    val setupComplete: StateFlow<Boolean> = _setupComplete.asStateFlow()

    init {
        scope.launch {
            val prefs = dataStore.data.first()

            val storedUrl = prefs[BASE_URL]?.takeIf { it.isNotBlank() }
            val url = storedUrl ?: defaultBaseUrl
            if (storedUrl == null) {
                dataStore.edit { it[BASE_URL] = url }
            }
            _baseUrl.value = url

            val storedSuffix = prefs[NODE_SUFFIX]?.takeIf { it.isNotBlank() }
            val suffix = storedSuffix ?: generateSuffix()
            if (storedSuffix == null) {
                dataStore.edit { it[NODE_SUFFIX] = suffix }
            }
            _nodeSuffix.value = suffix

            _setupComplete.value = prefs[SETUP_COMPLETE] ?: false

            _ready.value = true
        }
    }

    /** Persists the Frigate server URL. Blank input is persisted as-is and is
     *  treated by the connection flow as an unset configuration. */
    suspend fun setBaseUrl(url: String) {
        val trimmed = url.trim()
        _baseUrl.value = trimmed
        dataStore.edit { it[BASE_URL] = trimmed }
    }

    /** Marks the V1.1 initial setup as complete (or reopens it when false). */
    suspend fun setSetupComplete(complete: Boolean) {
        _setupComplete.value = complete
        dataStore.edit { it[SETUP_COMPLETE] = complete }
    }

    companion object {
        fun create(context: Context, defaultBaseUrl: String, scope: CoroutineScope): AppSettings =
            AppSettings(context.appSettingsDataStore, defaultBaseUrl, scope)

        /** Test-only factory: takes a caller-owned DataStore. */
        fun createForTest(
            dataStore: DataStore<Preferences>,
            defaultBaseUrl: String,
            scope: CoroutineScope,
        ): AppSettings = AppSettings(dataStore, defaultBaseUrl, scope)

        /**
         * Stable embedded node hostname for a given persisted suffix. The
         * hostname is an internal value: it is never part of the normal
         * configuration flow.
         */
        fun nodeHostname(suffix: String): String = "family-camera-$suffix"

        /** Random lowercase-hex suffix. Deterministic length, cryptographically
         *  seeded so two installations never collide by chance. */
        fun generateSuffix(length: Int = 8): String {
            val chars = "0123456789abcdef"
            val random = SecureRandom()
            return buildString(length) {
                repeat(length) { append(chars[random.nextInt(chars.length)]) }
            }
        }

        private val BASE_URL = stringPreferencesKey("frigate_base_url")
        private val NODE_SUFFIX = stringPreferencesKey("node_hostname_suffix")
        private val SETUP_COMPLETE = booleanPreferencesKey("setup_complete")
    }
}

package com.manima.foodlens.core.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

data class UserSettings(
    val onboardingDone: Boolean = false,
    /** Anonymous scan analytics; off until the user opts in (SPEC §10.5). */
    val analyticsConsent: Boolean = false,
    /** Allergens the user wants highlighted. Matched against label declarations only (T7). */
    val allergenPrefs: Set<String> = emptySet(),
    /** Debug builds only: lets a physical phone reach the API on a LAN IP. */
    val apiBaseUrlOverride: String? = null
)

internal val Context.settingsStore: DataStore<Preferences> by preferencesDataStore(name = "aahar_settings")

class SettingsStore(private val store: DataStore<Preferences>) {
    val settings: Flow<UserSettings> = store.data.map { p ->
        UserSettings(
            onboardingDone = p[ONBOARDING] ?: false,
            analyticsConsent = p[CONSENT] ?: false,
            allergenPrefs = p[ALLERGENS] ?: emptySet(),
            apiBaseUrlOverride = p[BASE_URL]?.takeIf { it.isNotBlank() }
        )
    }

    suspend fun current(): UserSettings = settings.first()

    suspend fun setOnboardingDone(done: Boolean) = store.edit { it[ONBOARDING] = done }

    suspend fun setAnalyticsConsent(consent: Boolean) = store.edit { it[CONSENT] = consent }

    suspend fun setAllergenPrefs(codes: Set<String>) = store.edit { it[ALLERGENS] = codes }

    suspend fun setApiBaseUrlOverride(url: String?) = store.edit {
        if (url.isNullOrBlank()) it.remove(BASE_URL) else it[BASE_URL] = url.trim()
    }

    /**
     * Random installation ID: no hardware identifiers (SPEC §6.2). Rotated every 90 days.
     */
    suspend fun installId(now: Long = System.currentTimeMillis()): String {
        val prefs = store.data.first()
        val existing = prefs[INSTALL_ID]
        val created = prefs[INSTALL_ID_CREATED]?.toLongOrNull() ?: 0L
        if (existing != null && now - created < ROTATE_AFTER_MS) return existing
        val fresh = "aahar-" + UUID.randomUUID().toString()
        store.edit {
            it[INSTALL_ID] = fresh
            it[INSTALL_ID_CREATED] = now.toString()
        }
        return fresh
    }

    /** Guest "wipe local data": keeps nothing that identifies the install. */
    suspend fun clear() = store.edit { it.clear() }

    private companion object {
        val ONBOARDING = booleanPreferencesKey("onboarding_done")
        val CONSENT = booleanPreferencesKey("analytics_consent")
        val ALLERGENS = stringSetPreferencesKey("allergen_prefs")
        val BASE_URL = stringPreferencesKey("api_base_url_override")
        val INSTALL_ID = stringPreferencesKey("install_id")
        val INSTALL_ID_CREATED = stringPreferencesKey("install_id_created")
        const val ROTATE_AFTER_MS = 90L * 24 * 60 * 60 * 1000
    }
}

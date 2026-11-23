package com.manima.foodlens.core.datastore

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import java.util.UUID
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class StoresTest {
    private val dir = ApplicationProvider.getApplicationContext<android.app.Application>().filesDir

    private fun store(scope: TestScope, name: String = UUID.randomUUID().toString()) =
        PreferenceDataStoreFactory.create(scope = scope.backgroundScope) { File(dir, "$name.preferences_pb") }

    /** Reversible test cipher: Robolectric has no Android Keystore. */
    private val xor = object : SecretCipher {
        override fun encrypt(plain: ByteArray) = plain.map { (it.toInt() xor 0x5A).toByte() }.toByteArray()

        override fun decrypt(cipher: ByteArray) = encrypt(cipher)
    }

    @Test
    fun settingsDefaultsArePrivacyPreserving() = runTest(UnconfinedTestDispatcher()) {
        val s = SettingsStore(store(this)).current()
        assertFalse(s.onboardingDone)
        assertFalse("analytics off until opt-in", s.analyticsConsent)
        assertNull(s.apiBaseUrlOverride)
    }

    @Test
    fun installIdIsStableThenRotatesAfter90Days() = runTest(UnconfinedTestDispatcher()) {
        val settings = SettingsStore(store(this))
        val first = settings.installId(now = 0)
        assertEquals(first, settings.installId(now = 1000))
        assertTrue(first.startsWith("aahar-"))
        assertNotEquals(first, settings.installId(now = 91L * 24 * 60 * 60 * 1000))
    }

    @Test
    fun tokenIsEncryptedAtRestAndSurvivesReload() = runTest(UnconfinedTestDispatcher()) {
        val name = UUID.randomUUID().toString()
        val ds = store(this, name)
        TokenStore(ds, xor).save("secret-token")
        val raw = File(dir, "$name.preferences_pb").readBytes().toString(Charsets.ISO_8859_1)
        assertFalse("plain token must not be on disk", raw.contains("secret-token"))
        val reloaded = TokenStore(ds, xor)
        assertNull(reloaded.current())
        assertEquals("secret-token", reloaded.load())
        assertEquals("secret-token", reloaded.current())
        reloaded.clear()
        assertNull(reloaded.current())
    }
}

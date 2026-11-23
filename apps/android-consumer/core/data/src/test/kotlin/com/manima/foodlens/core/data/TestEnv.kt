package com.manima.foodlens.core.data

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.manima.foodlens.core.database.FoodLensDatabase
import com.manima.foodlens.core.datastore.SecretCipher
import com.manima.foodlens.core.datastore.SettingsStore
import com.manima.foodlens.core.datastore.TokenStore
import com.manima.foodlens.core.network.ClientHeaders
import com.manima.foodlens.core.network.FoodLensClient
import com.manima.foodlens.core.network.TokenProvider
import com.manima.foodlens.core.testing.Fixtures
import io.mockk.mockk
import java.io.File
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.Headers

/** Real Room (in-memory), real DataStore (temp file) and a real HTTP client against MockWebServer. */
class TestEnv(scope: CoroutineScope) {
    val context: Context = ApplicationProvider.getApplicationContext()
    val server = MockWebServer().apply { start() }
    val db: FoodLensDatabase = Room.inMemoryDatabaseBuilder(context, FoodLensDatabase::class.java)
        .allowMainThreadQueries()
        .build()
    val settings = SettingsStore(
        PreferenceDataStoreFactory.create(scope = scope) {
            File(context.cacheDir, "settings-${UUID.randomUUID()}.preferences_pb")
        }
    )
    val tokens = TokenStore(
        PreferenceDataStoreFactory.create(scope = scope) {
            File(context.cacheDir, "tokens-${UUID.randomUUID()}.preferences_pb")
        },
        object : SecretCipher {
            override fun encrypt(plain: ByteArray) = plain.reversedArray()

            override fun decrypt(cipher: ByteArray) = cipher.reversedArray()
        }
    )
    val client: FoodLensClient = FoodLensClient.create(
        baseUrl = server.url("/").toString(),
        headers = ClientHeaders(appVersionCode = 1, languageTag = { "en-IN" }),
        tokens = TokenProvider { tokens.current() }
    )
    val scheduler: WorkScheduler = mockk(relaxed = true)
    var now = 1_000L
    val clock = Clock { now }

    fun enqueue(fixture: String, code: Int = 200, headers: Map<String, String> = emptyMap()) {
        server.enqueue(
            MockResponse(
                code = code,
                headers = Headers.headersOf(
                    *headers.flatMap {
                        listOf(it.key, it.value)
                    }.toTypedArray(),
                    "Content-Type",
                    "application/json"
                ),
                body = Fixtures.read("api-examples/live/$fixture")
            )
        )
    }

    fun close() {
        db.close()
        server.close()
    }
}

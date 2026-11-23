package com.manima.foodlens.core.data.di

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.work.WorkManager
import com.manima.foodlens.core.data.ApiEndpoint
import com.manima.foodlens.core.data.AppConfig
import com.manima.foodlens.core.data.AppStatus
import com.manima.foodlens.core.data.AppStatusInterceptor
import com.manima.foodlens.core.data.ApplicationScope
import com.manima.foodlens.core.data.Clock
import com.manima.foodlens.core.data.IoDispatcher
import com.manima.foodlens.core.datastore.TokenStore
import com.manima.foodlens.core.network.ClientHeaders
import com.manima.foodlens.core.network.FoodLensClient
import com.manima.foodlens.core.network.TokenProvider
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.util.Locale
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import okhttp3.OkHttpClient

@Module
@InstallIn(SingletonComponent::class)
object DataModule {
    @Provides
    @Singleton
    @ApplicationScope
    fun applicationScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Provides
    @IoDispatcher
    fun ioDispatcher(): CoroutineDispatcher = Dispatchers.IO

    @Provides
    fun clock(): Clock = Clock.System

    @Provides
    @Singleton
    fun client(config: AppConfig, endpoint: ApiEndpoint, tokens: TokenStore, status: AppStatus): FoodLensClient =
        FoodLensClient.create(
            baseUrl = FoodLensClient.PLACEHOLDER_BASE_URL,
            headers = ClientHeaders(config.versionCode, ::languageTag),
            tokens = TokenProvider { tokens.current() },
            baseUrlProvider = endpoint,
            builder = OkHttpClient.Builder().addInterceptor(AppStatusInterceptor(status))
        )

    @Provides
    @Singleton
    fun workManager(@ApplicationContext context: Context): WorkManager = WorkManager.getInstance(context)

    /** Per-app language (SPEC §10.10) when set, else the device language. */
    private fun languageTag(): String =
        AppCompatDelegate.getApplicationLocales()[0]?.toLanguageTag() ?: Locale.getDefault().toLanguageTag()
}

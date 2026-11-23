package com.manima.foodlens

import android.os.Build
import com.manima.foodlens.core.data.AppConfig
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    @Singleton
    fun appConfig(): AppConfig = AppConfig(
        apiBaseUrl = if (isEmulator()) BuildConfig.API_BASE_URL else BuildConfig.API_BASE_URL_DEVICE,
        versionCode = BuildConfig.VERSION_CODE,
        versionName = BuildConfig.VERSION_NAME,
        isDebug = BuildConfig.DEBUG
    )

    /** Debug builds only differ: the emulator reaches the host at 10.0.2.2, a USB phone via adb reverse. */
    private fun isEmulator(): Boolean = Build.FINGERPRINT.startsWith("generic") ||
        Build.FINGERPRINT.contains("emulator") ||
        Build.HARDWARE == "ranchu" ||
        Build.HARDWARE == "goldfish" ||
        Build.PRODUCT.contains("sdk_gphone") ||
        Build.MODEL.contains("Android SDK built for")
}

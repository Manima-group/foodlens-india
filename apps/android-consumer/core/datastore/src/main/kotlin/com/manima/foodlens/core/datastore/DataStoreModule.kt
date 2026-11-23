package com.manima.foodlens.core.datastore

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DataStoreModule {
    @Provides
    @Singleton
    fun settings(@ApplicationContext context: Context) = SettingsStore(context.settingsStore)

    @Provides
    @Singleton
    fun tokens(@ApplicationContext context: Context) = TokenStore(context.tokenStore, TinkKeystoreCipher(context))
}

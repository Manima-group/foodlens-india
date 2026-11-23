package com.manima.foodlens.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Database(
    entities = [
        CachedProductEntity::class,
        HistoryEntity::class,
        FavoriteEntity::class,
        PendingLookupEntity::class,
        PendingScanEventEntity::class,
        ContributionEntity::class,
        ContributionPhotoEntity::class
    ],
    version = 1,
    exportSchema = true
)
abstract class FoodLensDatabase : RoomDatabase() {
    abstract fun products(): ProductDao

    abstract fun history(): HistoryDao

    abstract fun favorites(): FavoriteDao

    abstract fun pending(): PendingDao

    abstract fun contributions(): ContributionDao

    companion object {
        const val NAME = "aahar.db"
    }
}

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context): FoodLensDatabase =
        Room.databaseBuilder(context, FoodLensDatabase::class.java, FoodLensDatabase.NAME).build()

    @Provides fun products(db: FoodLensDatabase) = db.products()

    @Provides fun history(db: FoodLensDatabase) = db.history()

    @Provides fun favorites(db: FoodLensDatabase) = db.favorites()

    @Provides fun pending(db: FoodLensDatabase) = db.pending()

    @Provides fun contributions(db: FoodLensDatabase) = db.contributions()
}

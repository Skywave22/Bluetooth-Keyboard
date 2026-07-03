package com.bluepilot.remote.di

import android.content.Context
import androidx.room.Room
import com.bluepilot.remote.data.db.BluePilotDatabase
import com.bluepilot.remote.data.db.LayoutProfileDao
import com.bluepilot.remote.data.db.MacroDao
import com.bluepilot.remote.data.db.SkinDao
import com.bluepilot.remote.data.settings.DataStoreSettings
import com.bluepilot.remote.domain.SettingsStore
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Hilt wiring for the data layer: DataStore settings + Room database. */
@Module
@InstallIn(SingletonComponent::class)
abstract class DataBindingsModule {
    @Binds
    @Singleton
    abstract fun bindSettingsStore(impl: DataStoreSettings): SettingsStore
}

@Module
@InstallIn(SingletonComponent::class)
object DataProvidersModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): BluePilotDatabase =
        Room.databaseBuilder(context, BluePilotDatabase::class.java, "bluepilot.db")
            // v1 ships without user data yet; if we ever bump the schema before
            // adding real migrations, a destructive fallback beats a crash loop.
            .fallbackToDestructiveMigration()
            .build()

    @Provides fun provideLayoutProfileDao(db: BluePilotDatabase): LayoutProfileDao = db.layoutProfileDao()
    @Provides fun provideMacroDao(db: BluePilotDatabase): MacroDao = db.macroDao()
    @Provides fun provideSkinDao(db: BluePilotDatabase): SkinDao = db.skinDao()
}

package com.torrentclaw.movies.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.Room
import com.torrentclaw.movies.data.local.CachedMovieDao
import com.torrentclaw.movies.data.local.TorrentClawDatabase
import com.torrentclaw.movies.data.local.WatchlistDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

private const val PREFERENCES_FILE = "user_preferences"

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = PREFERENCES_FILE)

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): TorrentClawDatabase =
        Room.databaseBuilder(context, TorrentClawDatabase::class.java, TorrentClawDatabase.NAME)
            // Schema v1 ships with its export; future versions must add real migrations.
            .fallbackToDestructiveMigrationOnDowngrade()
            .build()

    @Provides
    @Singleton
    fun provideWatchlistDao(database: TorrentClawDatabase): WatchlistDao = database.watchlistDao()

    @Provides
    @Singleton
    fun provideCachedMovieDao(database: TorrentClawDatabase): CachedMovieDao =
        database.cachedMovieDao()

    @Provides
    @Singleton
    fun provideDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        context.dataStore
}

package com.torrentclaw.movies.di

import com.torrentclaw.movies.data.repository.MovieRepository
import com.torrentclaw.movies.data.repository.MovieRepositoryImpl
import com.torrentclaw.movies.data.repository.WatchlistRepository
import com.torrentclaw.movies.data.repository.WatchlistRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindMovieRepository(impl: MovieRepositoryImpl): MovieRepository

    @Binds
    @Singleton
    abstract fun bindWatchlistRepository(impl: WatchlistRepositoryImpl): WatchlistRepository
}

@Module
@InstallIn(SingletonComponent::class)
object DispatcherModule {

    /** Unqualified IO dispatcher; injected so tests can swap in a TestDispatcher. */
    @Provides
    @Singleton
    fun provideIoDispatcher(): CoroutineDispatcher = Dispatchers.IO
}

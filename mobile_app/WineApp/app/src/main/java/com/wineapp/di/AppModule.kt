package com.wineapp.di

import android.content.Context
import com.wineapp.BuildConfig
import androidx.room.Room
import dagger.hilt.android.qualifiers.ApplicationContext
import com.wineapp.data.local.AppDatabase
import com.wineapp.data.local.CellarDao
import com.wineapp.data.local.FavoriteDao
import com.wineapp.data.local.GameDao
import com.wineapp.data.local.ScanHistoryDao
import com.wineapp.data.local.WineDao
import com.wineapp.data.remote.ApiService
import com.wineapp.data.remote.gigachat.GigaChatApiService
import com.wineapp.data.remote.gigachat.GigaChatAuthService
import com.wineapp.data.remote.gigachat.GigaChatSslConfig
import com.wineapp.data.remote.gigachat.GigaChatTokenManager
import com.wineapp.data.repository.CellarRepositoryImpl
import com.wineapp.data.repository.BadgeRepositoryImpl
import com.wineapp.data.repository.FavoriteRepositoryImpl
import com.wineapp.data.repository.ScanHistoryRepositoryImpl
import com.wineapp.data.repository.SommelierRepositoryImpl
import com.wineapp.data.repository.WineRepositoryImpl
import com.wineapp.domain.repository.CellarRepository
import com.wineapp.domain.repository.BadgeRepository
import com.wineapp.domain.repository.FavoriteRepository
import com.wineapp.domain.repository.ScanHistoryRepository
import com.wineapp.domain.repository.SommelierRepository
import com.wineapp.domain.repository.WineRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Named
import javax.inject.Singleton
import okhttp3.OkHttpClient
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.serialization.json.Json

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context.applicationContext,
            AppDatabase::class.java,
            "wine_history.db"
        ).fallbackToDestructiveMigration().build()
    }

    @Provides
    @Singleton
    fun provideWineDao(database: AppDatabase): WineDao {
        return database.wineDao()
    }

    @Provides
    @Singleton
    fun provideScanHistoryDao(database: AppDatabase): ScanHistoryDao {
        return database.scanHistoryDao()
    }

    @Provides
    @Singleton
    fun provideFavoriteDao(database: AppDatabase): FavoriteDao {
        return database.favoriteDao()
    }

    @Provides
    @Singleton
    fun provideCellarDao(database: AppDatabase): CellarDao {
        return database.cellarDao()
    }

    @Provides
    @Singleton
    fun provideGameDao(database: AppDatabase): GameDao {
        return database.gameDao()
    }
}

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        val logging = HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BODY }
        return OkHttpClient.Builder().addInterceptor(logging).build()
    }

    @Provides
    @Singleton
    fun provideRetrofit(client: OkHttpClient): Retrofit {
        val json = Json { ignoreUnknownKeys = true }
        return Retrofit.Builder()
            .baseUrl(BuildConfig.BASE_URL)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
    }

    @Provides
    @Singleton
    fun provideApiService(retrofit: Retrofit): ApiService {
        return retrofit.create(ApiService::class.java)
    }
}

@Module
@InstallIn(SingletonComponent::class)
object GigaChatModule {

    @Provides
    @Singleton
    fun provideGigaChatJson(): Json {
        return Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }
    }

    @Provides
    @Singleton
    @Named("gigachat")
    fun provideGigaChatOkHttpClient(): OkHttpClient {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }
        val trustManager = GigaChatSslConfig.createCombinedTrustManager()
        val sslSocketFactory = GigaChatSslConfig.createSslSocketFactory()
        return OkHttpClient.Builder()
            .addInterceptor(logging)
            .sslSocketFactory(sslSocketFactory, trustManager)
            .hostnameVerifier { _, _ -> true }
            .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
            .writeTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .build()
    }

    @Provides
    @Singleton
    @Named("gigachat_auth")
    fun provideGigaChatAuthRetrofit(
        @Named("gigachat") client: OkHttpClient,
        json: Json
    ): Retrofit {
        return Retrofit.Builder()
            .baseUrl("https://ngw.devices.sberbank.ru:9443/")
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
    }

    @Provides
    @Singleton
    fun provideGigaChatAuthService(
        @Named("gigachat_auth") retrofit: Retrofit
    ): GigaChatAuthService {
        return retrofit.create(GigaChatAuthService::class.java)
    }

    @Provides
    @Singleton
    @Named("gigachat_api")
    fun provideGigaChatApiRetrofit(
        @Named("gigachat") client: OkHttpClient,
        json: Json
    ): Retrofit {
        return Retrofit.Builder()
            .baseUrl("https://api.giga.chat/")
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
    }

    @Provides
    @Singleton
    fun provideGigaChatApiService(
        @Named("gigachat_api") retrofit: Retrofit
    ): GigaChatApiService {
        return retrofit.create(GigaChatApiService::class.java)
    }

    @Provides
    @Singleton
    fun provideGigaChatTokenManager(
        authService: GigaChatAuthService
    ): GigaChatTokenManager {
        return GigaChatTokenManager(authService)
    }
}

@Module
@InstallIn(SingletonComponent::class)
object RepositoryModule {

    @Provides
    @Singleton
    fun provideWineRepository(impl: WineRepositoryImpl): WineRepository {
        return impl
    }

    @Provides
    @Singleton
    fun provideSommelierRepository(impl: SommelierRepositoryImpl): SommelierRepository {
        return impl
    }

    @Provides
    @Singleton
    fun provideScanHistoryRepository(impl: ScanHistoryRepositoryImpl): ScanHistoryRepository {
        return impl
    }

    @Provides
    @Singleton
    fun provideFavoriteRepository(impl: FavoriteRepositoryImpl): FavoriteRepository {
        return impl
    }

    @Provides
    @Singleton
    fun provideCellarRepository(impl: CellarRepositoryImpl): CellarRepository {
        return impl
    }

    @Provides
    @Singleton
    fun provideBadgeRepository(impl: BadgeRepositoryImpl): BadgeRepository {
        return impl
    }
}

package com.wineapp.data.remote.gigachat

import android.util.Base64
import android.util.Log
import com.wineapp.BuildConfig
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GigaChatTokenManager @Inject constructor(
    private val authService: GigaChatAuthService
) {
    private val cache = ConcurrentHashMap<String, CachedToken>()

    data class CachedToken(
        val token: String,
        val expiresAt: Long
    )

    suspend fun getAccessToken(): String {
        val cached = cache["main"]
        val now = System.currentTimeMillis()

        if (cached != null && cached.expiresAt > now + REFRESH_BUFFER_MS) {
            return cached.token
        }

        return refreshAccessToken()
    }

    private suspend fun refreshAccessToken(): String {
        val authKey = BuildConfig.GIGACHAT_AUTH_KEY
        if (authKey.isBlank()) {
            throw IllegalStateException(
                "GIGACHAT_AUTH_KEY is not configured. " +
                "Set it in gradle.properties and rebuild."
            )
        }

        val authorizationHeader = if (authKey.contains(":")) {
            val encoded = Base64.encodeToString(
                authKey.toByteArray(Charsets.UTF_8),
                Base64.NO_WRAP
            )
            "Basic $encoded"
        } else {
            "Basic $authKey"
        }

        val response = authService.getAccessToken(
            rqUid = UUID.randomUUID().toString(),
            authorization = authorizationHeader,
            scope = "GIGACHAT_API_PERS"
        )

        val cached = CachedToken(
            token = response.accessToken,
            expiresAt = response.expiresAt * 1000L
        )
        cache["main"] = cached

        Log.d(TAG, "Token refreshed, expires at ${cached.expiresAt}")
        return cached.token
    }

    companion object {
        private const val TAG = "GigaChatTokenMgr"
        private const val REFRESH_BUFFER_MS = 5 * 60 * 1000L
    }
}

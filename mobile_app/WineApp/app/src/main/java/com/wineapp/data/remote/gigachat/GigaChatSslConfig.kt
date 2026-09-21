package com.wineapp.data.remote.gigachat

import android.annotation.SuppressLint
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManager
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

object GigaChatSslConfig {

    @SuppressLint("TrustAllX509TrustManager")
    fun createTrustAllManager(): X509TrustManager {
        return object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
            override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
            override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
        }
    }

    fun createSslSocketFactory(): SSLSocketFactory {
        val trustManager = createTrustAllManager()
        val sslContext = SSLContext.getInstance("TLS").apply {
            init(null, arrayOf<TrustManager>(trustManager), SecureRandom())
        }
        return sslContext.socketFactory
    }

    fun createCombinedTrustManager(): X509TrustManager {
        val defaultTmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
        defaultTmf.init(null as java.security.KeyStore?)
        val defaultTrustManagers = defaultTmf.trustManagers
        val defaultX509Tm = defaultTrustManagers.firstIsInstanceOrNull<X509TrustManager>()
            ?: throw IllegalStateException("No default X509TrustManager")

        val trustAllManager = createTrustAllManager()

        return object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {
                defaultX509Tm.checkClientTrusted(chain, authType)
            }

            override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
                try {
                    defaultX509Tm.checkServerTrusted(chain, authType)
                } catch (e: Exception) {
                    trustAllManager.checkServerTrusted(chain, authType)
                }
            }

            override fun getAcceptedIssuers(): Array<X509Certificate> = defaultX509Tm.acceptedIssuers
        }
    }

    private inline fun <reified T> Array<*>.firstIsInstanceOrNull(): T? {
        return firstOrNull { it is T } as? T
    }
}

package com.wineapp

import android.graphics.BitmapFactory
import androidx.test.platform.app.InstrumentationRegistry
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

/** Optional real-device network check: run with -e integrationApi true. */
class ApiConnectivityTest {
    @Test fun configuredApiServesReadinessAndCataloguePhoto() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("integrationApi") == "true")
        val client = OkHttpClient.Builder().callTimeout(15, TimeUnit.SECONDS).build()
        fun get(path: String): ByteArray = client.newCall(Request.Builder()
            .url(BuildConfig.BASE_URL.trimEnd('/') + path).build()).execute().use { response ->
            assertEquals("HTTP response for $path", 200, response.code)
            requireNotNull(response.body).bytes()
        }
        val ready = JSONObject(String(get("/ready")))
        assertEquals("ready", ready.getString("status"))
        val bytes = get("/v1/wines/abrau-dyurso-risling-beloe-suhoe-12/image")
        val image = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        assertNotNull("Catalogue photo must decode on Android", image)
        image?.recycle()
    }
}

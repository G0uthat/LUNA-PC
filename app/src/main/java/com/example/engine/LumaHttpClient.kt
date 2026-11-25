package com.example.engine

import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * Single shared base [OkHttpClient] instance for the entire app.
 * Subsystems (GitHub updater, P2P relay, translation) derive specialized timeouts via
 * `LumaHttpClient.shared.newBuilder()` so they share one [ConnectionPool] and one thread pool
 * instead of spawning redundant dispatcher pools and idle sockets.
 */
object LumaHttpClient {

    val shared: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectionPool(ConnectionPool(5, 3, TimeUnit.MINUTES))
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }
}

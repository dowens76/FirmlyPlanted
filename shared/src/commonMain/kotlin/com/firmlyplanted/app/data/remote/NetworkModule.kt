package com.firmlyplanted.app.data.remote

import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.plugins.logging.SIMPLE
import kotlinx.serialization.json.Json

/** OkHttp on Android, NSURLSession (Darwin) on iOS. */
internal expect fun httpClientEngine(): HttpClientEngine

object NetworkModule {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val httpClient: HttpClient by lazy {
        HttpClient(httpClientEngine()) {
            // Non-2xx responses throw, matching the old Retrofit behavior callers rely on.
            expectSuccess = true
            install(Logging) {
                logger = Logger.SIMPLE
                level = LogLevel.INFO
            }
        }
    }

    val esvApi: EsvApiService by lazy { EsvApiService(httpClient, json) }

    val fetchBibleApi: FetchBibleService by lazy { FetchBibleService(httpClient, json) }
}

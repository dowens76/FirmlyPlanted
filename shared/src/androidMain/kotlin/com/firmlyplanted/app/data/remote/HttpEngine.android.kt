package com.firmlyplanted.app.data.remote

import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.okhttp.OkHttp

internal actual fun httpClientEngine(): HttpClientEngine = OkHttp.create()

package com.firmlyplanted.app.data.remote

import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.darwin.Darwin

internal actual fun httpClientEngine(): HttpClientEngine = Darwin.create()

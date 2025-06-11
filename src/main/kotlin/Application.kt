package me.tashila

import io.ktor.server.application.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.logging.DEFAULT
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import me.tashila.config.AppConfig
import me.tashila.plugins.configureAuth
import me.tashila.plugins.configureLogging
import me.tashila.plugins.configureRateLimits
import me.tashila.plugins.configureSerialization
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation as ClientContentNegotiation


fun main(args: Array<String>) {
    io.ktor.server.netty.EngineMain.main(args)
}

fun Application.module() {
    configureLogging()
    configureSerialization()
    configureRateLimits()

    // Auth
    val supabaseConfig = AppConfig.loadSupabaseConfig()
    configureAuth(supabaseConfig)

    // Routes
    val httpClient = HttpClient(CIO) {
        install(ClientContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = true
            })
        }
        install(Logging) {
            logger = Logger.DEFAULT
            level = LogLevel.ALL
        }
    }
    configureRouting(httpClient, supabaseConfig)

    environment.monitor.subscribe(ApplicationStopped) {
        httpClient.close()
    }
}

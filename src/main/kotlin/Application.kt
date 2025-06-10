package me.tashila

import io.ktor.server.application.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json
import io.ktor.client.* // Import HttpClient
import io.ktor.client.engine.cio.* // Import CIO engine
import io.ktor.client.plugins.logging.DEFAULT
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.server.plugins.calllogging.CallLogging
import me.tashila.config.AppConfig
import me.tashila.plugins.configureAuth
import me.tashila.plugins.configureSerialization
import org.slf4j.event.Level
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation as ClientContentNegotiation // Aliasing if needed


fun main(args: Array<String>) {
    io.ktor.server.netty.EngineMain.main(args)
}

fun Application.module() {
    install(CallLogging) {
        level = Level.DEBUG
    }

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

    val supabaseConfig = AppConfig.loadSupabaseConfig()
    configureAuth(supabaseConfig)
    configureRouting(httpClient, supabaseConfig)
    configureSerialization()
}

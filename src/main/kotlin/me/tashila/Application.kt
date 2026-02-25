package me.tashila

import com.aallam.openai.api.BetaOpenAI
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.ktor.server.application.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.logging.DEFAULT
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import me.tashila.chat.ChatService
import me.tashila.config.AppConfig
import me.tashila.data.SUPABASE_ANON_KEY
import me.tashila.data.SUPABASE_URL
import me.tashila.plugins.configureAuth
import me.tashila.plugins.configureLogging
import me.tashila.plugins.configureRateLimits
import me.tashila.plugins.configureSerialization
import me.tashila.plugins.configureStatusPages
import me.tashila.repository.UserAssistantRepository
import me.tashila.repository.UserThreadRepository
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation as ClientContentNegotiation


fun main(args: Array<String>) {
    io.ktor.server.netty.EngineMain.main(args)
}

@OptIn(BetaOpenAI::class)
fun Application.module() {
    val env = environment.config.propertyOrNull("ktor.environment")?.getString()
        ?: System.getenv("KTOR_ENV") // Check an environment variable directly
        ?: System.getProperty("ktor.environment") // Check a system property
        ?: "development" // default fallback
    println("Env: $env")

    val supabaseClient = createSupabaseClient(
        supabaseUrl = SUPABASE_URL,
        supabaseKey = SUPABASE_ANON_KEY
    ) {
        install(Auth)
        install(Postgrest)
    }

    configureStatusPages()
    configureLogging(env)
    configureSerialization()
    configureRateLimits()

    // Auth
    val supabaseConfig = AppConfig.loadSupabaseConfig()
    configureAuth(supabaseConfig)

    // Routes
    val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }
    val httpClient = HttpClient(CIO) {
        install(ClientContentNegotiation) {
            json(json)
        }
        install(Logging) {
            logger = Logger.DEFAULT
            level = when (env) {
                "development" -> LogLevel.ALL
                "production" -> LogLevel.HEADERS
                else -> LogLevel.INFO
            }
        }
    }

    val userAssistantRepository = UserAssistantRepository(supabaseClient)
    val userThreadRepository = UserThreadRepository(supabaseClient)
    val chatService = ChatService(userAssistantRepository, userThreadRepository, json)
    configureRouting(supabaseClient, chatService)

    environment.monitor.subscribe(ApplicationStopped) {
        httpClient.close()
    }
}

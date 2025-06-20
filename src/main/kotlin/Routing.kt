package me.tashila

import io.ktor.client.HttpClient
import io.ktor.server.application.*
import io.ktor.server.auth.authenticate
import io.ktor.server.plugins.ratelimit.RateLimitName
import io.ktor.server.plugins.ratelimit.rateLimit
import io.ktor.server.response.respondText
import io.ktor.server.routing.*
import me.tashila.auth.auth
import me.tashila.chat.AiService
import me.tashila.chat.chat
import me.tashila.config.SupabaseConfig

fun Application.configureRouting(httpClient: HttpClient, supabaseConfig: SupabaseConfig, aiService: AiService) {
    routing {
        this.root()

        rateLimit(RateLimitName("loginAttempts")) {
            this.auth(httpClient, supabaseConfig)
        }

        rateLimit(RateLimitName("protected")) {
            authenticate("auth-jwt") {
                this@routing.chat(aiService)
            }
        }
    }
}

fun Routing.root() {
    get("/") {
        call.respondText("Hello World!")
    }
}

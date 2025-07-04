package me.tashila

import io.github.jan.supabase.SupabaseClient
import io.ktor.server.application.*
import io.ktor.server.plugins.ratelimit.RateLimitName
import io.ktor.server.plugins.ratelimit.rateLimit
import io.ktor.server.response.respondText
import io.ktor.server.routing.*
import me.tashila.auth.auth
import me.tashila.chat.AiService
import me.tashila.chat.chat

fun Application.configureRouting(supabaseClient: SupabaseClient, aiService: AiService) {
    routing {
        root()

        rateLimit(RateLimitName("loginAttempts")) {
            auth( supabaseClient)
        }

        rateLimit(RateLimitName("protected")) {
            chat(aiService)
        }
    }
}

fun Routing.root() {
    get("/") {
        call.respondText("You're probably looking for https://hazle.tashile.me")
    }
}

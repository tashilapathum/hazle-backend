package me.tashila

import io.github.jan.supabase.SupabaseClient
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.*
import io.ktor.server.plugins.ratelimit.RateLimitName
import io.ktor.server.plugins.ratelimit.rateLimit
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondRedirect
import io.ktor.server.routing.*
import me.tashila.auth.auth
import me.tashila.chat.ChatService
import me.tashila.chat.chat

fun Application.configureRouting(supabaseClient: SupabaseClient, chatService: ChatService) {
    routing {
        get("/.well-known/assetlinks.json") {
            call.response.header(
                HttpHeaders.ContentType,
                ContentType.Application.Json.toString()
            )

            call.respond(this::class.java.classLoader.getResourceAsStream(
                ".well-known/assetlinks.json"
            )?.readAllBytes() ?: HttpStatusCode.NotFound)
        }
        root()

        rateLimit(RateLimitName("loginAttempts")) {
            auth( supabaseClient)
        }

        rateLimit(RateLimitName("protected")) {
            chat(chatService)
        }
    }
}

fun Routing.root() {
    get("/") {
        call.respondRedirect("https://hazle.tashile.me", permanent = true)
    }
}

package me.tashila

import io.ktor.client.HttpClient
import io.ktor.server.application.*
import io.ktor.server.auth.authenticate
import io.ktor.server.response.respondText
import io.ktor.server.routing.*
import me.tashila.auth.auth
import me.tashila.chat.chat
import me.tashila.config.SupabaseConfig

fun Application.configureRouting(httpClient: HttpClient, supabaseConfig: SupabaseConfig) {
    routing {
        this.root()

        this.auth(httpClient, supabaseConfig)

        authenticate("auth-jwt") {
            this@routing.chat()
        }
    }
}

fun Routing.root() {
    get("/") {
        call.respondText("Hello World!")
    }
}

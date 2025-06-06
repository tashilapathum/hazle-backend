package me.tashila

import io.ktor.server.application.*
import io.ktor.server.routing.*
import me.tashila.chat.root

fun Application.configureRouting() {
    routing {
        this.root()
    }
}

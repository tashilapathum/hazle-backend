package me.tashila.chat

import chat.Message
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.application.log
import io.ktor.server.request.receive
import io.ktor.server.response.respondText
import io.ktor.server.routing.Routing
import io.ktor.server.routing.get
import io.ktor.server.routing.post

fun Routing.chat() {
    post("/chat") {
        val message = call.receive<Message>()
        val userMessage = message.text
        println("Received message from client: $userMessage")

        if (userMessage.isBlank()) {
            call.respondText("Please provide a message", status = HttpStatusCode.BadRequest)
            return@post
        }

        try {
            val aiResponse = AiService().getAiResponse(userMessage)
            call.respondText(aiResponse)
        } catch (e: Exception) {
            call.application.log.error("Error in /chat route: ${e.message}", e)
            call.respondText("Failed to get AI response: ${e.message}", status = HttpStatusCode.InternalServerError)
        }
    }
}
package me.tashila.chat

import chat.Message
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.application.log
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Routing
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import me.tashila.auth.BackendErrorMessage
import me.tashila.data.MAX_MESSAGE_LENGTH

fun Routing.chat(aiService: AiService) {
    post("/chat") {
        val message = call.receive<Message>()
        val userMessage = message.text
        println("Received message from client: $userMessage")

        if (userMessage.isBlank()) {
            call.respond(
                HttpStatusCode.BadRequest,
                BackendErrorMessage("Please provide a message.")
            )
            return@post
        }

        if (userMessage.length > MAX_MESSAGE_LENGTH) {
            call.respond(
                HttpStatusCode.BadRequest,
                BackendErrorMessage("Message exceeds maximum allowed length of $MAX_MESSAGE_LENGTH characters.")
            )
            return@post
        }

        try {
            val aiResponse = aiService.getAssistantResponse(userMessage)
            call.respondText(aiResponse)
        } catch (e: Exception) {
            call.application.log.error("Error in /chat route: ${e.message}", e)
            call.respondText("Failed to get AI response: ${e.message}", status = HttpStatusCode.InternalServerError)
        }
    }
}
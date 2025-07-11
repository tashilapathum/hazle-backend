package me.tashila.chat

import chat.Message
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.log
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Routing
import io.ktor.server.routing.post
import me.tashila.auth.BackendErrorMessage
import me.tashila.data.MAX_MESSAGE_LENGTH
import kotlin.time.Clock.System
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
fun Routing.chat(chatService: ChatService) {
    authenticate("auth-jwt") {
        post("/chat") {
            val incomingMessage = call.receive<Message>()
            val userMessageText = incomingMessage.text
            val clientProvidedThreadId = incomingMessage.aiThreadId

            val principal = call.principal<JWTPrincipal>()
            val userId = principal?.payload?.subject

            // --- Validations ---

            if (userMessageText.isBlank()) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    BackendErrorMessage("Please provide a message.")
                )
                return@post
            }

            if (userMessageText.length > MAX_MESSAGE_LENGTH) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    BackendErrorMessage("Message exceeds maximum allowed length of $MAX_MESSAGE_LENGTH characters.")
                )
                return@post
            }

            if (userId == null) {
                call.respond(
                    HttpStatusCode.Unauthorized,
                    BackendErrorMessage("User ID not found in authentication token."))
                return@post
            }

            val actualOpenAIThreadId: String
            val aiResponseText: String

            try {
                // Ensure the user has an assistant.
                chatService.getOrCreateUserAssistant(userId)

                if (clientProvidedThreadId.isNullOrEmpty()) {
                    // Create a new thread if none provided
                    val newUserThread = chatService.createThreadForUser(userId)
                    actualOpenAIThreadId = newUserThread.openaiThreadId
                } else { // Continue existing thread
                    actualOpenAIThreadId = clientProvidedThreadId
                }

                aiResponseText = chatService.getAssistantResponse(
                    userId = userId,
                    openaiThreadId = actualOpenAIThreadId,
                    message = userMessageText
                )

                // Construct a new Message object representing the AI's response
                val aiResponseMessage = Message(
                    id = System.now().toEpochMilliseconds(), // Generate a new ID
                    text = aiResponseText,
                    isFromMe = false, // This is from the AI, so 'false'
                    aiThreadId = actualOpenAIThreadId, // Send back the actual thread ID used
                    timestamp = System.now() // New timestamp for the AI's message
                )

                // Respond to the client with the AI's Message object
                call.respond(HttpStatusCode.OK, aiResponseMessage)

            } catch (e: Exception) {
                call.application.log.error("Error in /chat route for user $userId: ${e.message}", e)
                call.respond(
                    HttpStatusCode.InternalServerError,
                    BackendErrorMessage("An error occurred during chat processing: ${e.message}")
                )
            }
        }
    }
}
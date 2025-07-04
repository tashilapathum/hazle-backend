package me.tashila.chat

import chat.Message
import com.auth0.jwt.JWT
import com.auth0.jwt.interfaces.DecodedJWT
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.application.log
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.authentication
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Routing
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import me.tashila.auth.BackendErrorMessage
import me.tashila.data.MAX_MESSAGE_LENGTH

fun Routing.chat(aiService: AiService) {
    authenticate("auth-jwt") {
        post("/chat") {
            val message = call.receive<Message>()
            val userMessage = message.text

            val principal = call.principal<JWTPrincipal>()
            val userId = principal?.payload?.subject

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
                aiService.getOrCreateUserChatState(userId!!)
                val aiResponse = aiService.getAssistantResponse(userId, userMessage)
                call.respondText(aiResponse)
            } catch (e: Exception) {
                call.application.log.error("Error in /chat route: ${e.message}", e)
                call.respondText("Failed to get AI response: ${e.message}", status = HttpStatusCode.InternalServerError)
            }
        }
    }
}
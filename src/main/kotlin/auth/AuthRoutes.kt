package me.tashila.auth

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.application.log
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.Json
import me.tashila.config.SupabaseConfig

fun Route.auth(
    httpClient: HttpClient,
    supabaseConfig: SupabaseConfig
) {

    route("/auth") {
        post("/signup") {
            val signUpRequest = call.receive<SupabaseSignUpRequest>()

            try {
                val response: HttpResponse = httpClient.post("${supabaseConfig.url}/auth/v1/signup") {
                    headers {
                        append(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                        append("apikey", supabaseConfig.anonKey)
                    }
                    setBody(signUpRequest)
                }

                val responseBodyString = response.bodyAsText() // Read body once

                if (response.status.isSuccess()) {
                    val supabaseAuthResponse = Json.decodeFromString<SupabaseAuthResponse>(responseBodyString)
                    call.respond(HttpStatusCode.OK, supabaseAuthResponse)
                } else {
                    try { // Attempt to parse the error response from Supabase
                        val supabaseError = Json.decodeFromString<SupabaseErrorResponse>(responseBodyString)

                        when (supabaseError.errorCode) {
                            "user_already_exists" -> {
                                call.respond(HttpStatusCode.Conflict, mapOf("message" to "User with this email already exists"))
                            }
                            "invalid_email_or_password" -> {
                                call.respond(HttpStatusCode.BadRequest, mapOf("message" to "Invalid email or password format"))
                            }
                            else -> {
                                call.respond(
                                    HttpStatusCode.InternalServerError,
                                    mapOf("message" to "Signup failed: ${supabaseError.msg ?: supabaseError.error ?: "Unknown Supabase error"}")
                                )
                            }
                        }
                    } catch (e: Exception) {
                        call.respond(
                            HttpStatusCode.InternalServerError,
                            mapOf("message" to "Signup failed: Unexpected response from Supabase. Raw: $responseBodyString")
                        )
                    }
                }
            } catch (e: Exception) {
                call.respond(HttpStatusCode.InternalServerError, mapOf("message" to "Internal server error during signup process: ${e.message}"))
            }
        }

        post("/signin") {
            val signInRequest = call.receive<SupabaseSignInRequest>()
            try {
                val response: HttpResponse = httpClient.post("${supabaseConfig.url}/auth/v1/token?grant_type=password") {
                    headers {
                        append(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                        append("apikey", supabaseConfig.serviceRoleKey)
                    }
                    setBody(signInRequest)
                }
                call.respond(response.status, Json.decodeFromString<SupabaseAuthResponse>(response.bodyAsText()))
            } catch (e: Exception) {
                call.respond(HttpStatusCode.InternalServerError, "Signin failed: ${e.message}")
            }
        }

        post("/refresh") {
            try {
                // 1. Receive the request body from the client (containing the refresh token)
                val request = call.receive<RefreshTokenRequest>()

                // 2. Construct the request to Supabase's refresh token endpoint
                val response = httpClient.post("${supabaseConfig.url}/auth/v1/token?grant_type=refresh_token") {
                    headers {
                        append(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                        append("apikey", supabaseConfig.serviceRoleKey)
                        append(HttpHeaders.Authorization, "Bearer ${supabaseConfig.serviceRoleKey}")
                    }
                    // Set the request body that Supabase expects for refresh
                    setBody(SupabaseRefreshTokenBody(request.refreshToken))
                }

                // 3. Receive and respond with Supabase's response
                val authResponse = response.body<SupabaseAuthResponse>()
                call.respond(response.status, authResponse)
            } catch (e: Exception) {
                call.application.log.error("Refresh token error: ${e.message}", e)
                call.respond(HttpStatusCode.Unauthorized, "Session refresh failed: ${e.message}")
            }
        }
    }
}
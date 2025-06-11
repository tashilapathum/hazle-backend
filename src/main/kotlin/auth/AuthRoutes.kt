package me.tashila.auth

import io.ktor.client.HttpClient
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.application.log
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.Json
import me.tashila.config.SupabaseConfig
import me.tashila.data.MAX_EMAIL_LENGTH
import me.tashila.data.MAX_PASSWORD_LENGTH

fun Route.auth(
    httpClient: HttpClient,
    supabaseConfig: SupabaseConfig
) {

    route("/auth") {
        post("/signup") {
            val signUpRequest = call.receive<SupabaseSignUpRequest>()

            if (signUpRequest.email.length > MAX_EMAIL_LENGTH) {
                call.respond(HttpStatusCode.BadRequest, BackendErrorMessage("Email address is too long. Maximum $MAX_EMAIL_LENGTH characters allowed."))
                return@post
            }

            if (signUpRequest.password.length > MAX_PASSWORD_LENGTH) {
                call.respond(HttpStatusCode.BadRequest, BackendErrorMessage("Password cannot exceed $MAX_PASSWORD_LENGTH characters."))
                return@post
            }

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
                                call.respond(HttpStatusCode.Conflict, BackendErrorMessage("User with this email already exists"))
                            }
                            "invalid_email_or_password" -> {
                                call.respond(HttpStatusCode.BadRequest, BackendErrorMessage("Invalid email or password format"))
                            }
                            else -> {
                                call.respond(
                                    HttpStatusCode.InternalServerError,
                                    BackendErrorMessage("Signup failed: ${supabaseError.msg ?: supabaseError.error ?: "Unknown Supabase error"}")
                                )
                            }
                        }
                    } catch (e: Exception) {
                        call.respond(
                            HttpStatusCode.InternalServerError,
                            BackendErrorMessage("Signup failed: Unexpected response from Supabase. Raw: $responseBodyString")
                        )
                    }
                }
            } catch (e: Exception) {
                call.respond(HttpStatusCode.InternalServerError, BackendErrorMessage("Internal server error during signup process: ${e.message}"))
            }
        }

        post("/signin") {
            val signInRequest = call.receive<SupabaseSignInRequest>()
            try {
                val response: HttpResponse = httpClient.post("${supabaseConfig.url}/auth/v1/token?grant_type=password") {
                    headers {
                        append(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                        append("apikey", supabaseConfig.anonKey)
                    }
                    setBody(signInRequest)
                }

                val responseBodyString = response.bodyAsText()

                if (response.status.isSuccess()) {
                    val supabaseAuthResponse = Json.decodeFromString<SupabaseAuthResponse>(responseBodyString)
                    call.respond(HttpStatusCode.OK, supabaseAuthResponse)
                } else {
                    try {
                        val supabaseError = Json.decodeFromString<SupabaseErrorResponse>(responseBodyString)

                        // Map Supabase specific error codes to Ktor HTTP statuses and messages
                        when (supabaseError.msg) {
                            "Invalid login credentials" -> {
                                call.respond(HttpStatusCode.Unauthorized, BackendErrorMessage("Invalid email or password"))
                            }
                            "Email not confirmed" -> {
                                call.respond(HttpStatusCode.Forbidden, BackendErrorMessage("Please confirm your email address to sign in"))
                            }
                            else -> {
                                call.respond(
                                    HttpStatusCode.InternalServerError,
                                    BackendErrorMessage("Signin failed: ${supabaseError.msg ?: supabaseError.errorCode ?: supabaseError.error ?: "Unknown Supabase error"}")
                                )
                            }
                        }
                    } catch (e: Exception) {
                        call.application.log.error("Internal server error during signin process: ${e.message}", e)
                        call.respond(
                            HttpStatusCode.InternalServerError,
                            BackendErrorMessage("Signin failed: Unexpected error response format from Supabase. Raw: $responseBodyString")
                        )
                    }
                }
            } catch (e: Exception) {
                call.application.log.error("Internal server error during signin process: ${e.message}", e)
                call.respond(HttpStatusCode.InternalServerError, BackendErrorMessage("Internal server error during signin process: ${e.message}"))
            }
        }

        post("/refresh") {
            try {
                val request = call.receive<RefreshTokenRequest>()

                val response: HttpResponse = httpClient.post("${supabaseConfig.url}/auth/v1/token?grant_type=refresh_token") {
                    headers {
                        append(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                        append("apikey", supabaseConfig.anonKey)
                        append(HttpHeaders.Authorization, "Bearer ${supabaseConfig.anonKey}")
                    }
                    setBody(SupabaseRefreshTokenBody(request.refreshToken))
                }

                val responseBodyString = response.bodyAsText()

                if (response.status.isSuccess()) {
                    val authResponse = Json.decodeFromString<SupabaseAuthResponse>(responseBodyString)
                    call.respond(HttpStatusCode.OK, authResponse)
                } else { // Handle error responses from Supabase
                    try {
                        val supabaseError = Json.decodeFromString<SupabaseErrorResponse>(responseBodyString)

                        when (supabaseError.msg) {
                            "Invalid Refresh Token" -> { // Common Supabase refresh error message
                                call.respond(HttpStatusCode.Unauthorized, BackendErrorMessage("Invalid or expired refresh token. Please sign in again."))
                            }
                            else -> {
                                call.respond(
                                    HttpStatusCode.InternalServerError,
                                    BackendErrorMessage("Session refresh failed: ${supabaseError.msg ?: supabaseError.errorCode ?: supabaseError.error ?: "Unknown Supabase error"}")
                                )
                            }
                        }
                    } catch (e: Exception) {
                        call.respond(
                            HttpStatusCode.InternalServerError,
                            BackendErrorMessage("Session refresh failed: Unexpected error response format from Supabase. Raw: $responseBodyString")
                        )
                    }
                }
            } catch (e: Exception) {
                call.application.log.error("Internal server error during refresh process: ${e.message}", e)
                call.respond(HttpStatusCode.InternalServerError, BackendErrorMessage("Internal server error during refresh process: ${e.message}"))
            }
        }
    }
}
package me.tashila.auth

import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import me.tashila.data.MAX_EMAIL_LENGTH
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.exception.AuthErrorCode
import io.github.jan.supabase.auth.exception.AuthRestException
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.user.UserInfo
import io.github.jan.supabase.auth.user.UserSession
import io.ktor.server.application.log
import io.ktor.server.http.content.resources
import io.ktor.server.http.content.static
import io.ktor.server.http.content.staticResources

fun Route.auth(supabase: SupabaseClient) {
    route("/auth") {
        post("/signup") {
            val signUpRequest = call.receive<SupabaseSignUpRequest>()

            if (signUpRequest.email.length > MAX_EMAIL_LENGTH) {
                call.respond(HttpStatusCode.BadRequest, BackendErrorMessage("Email too long"))
                return@post
            }

            try {
                val userInfo: UserInfo? = supabase.auth.signUpWith(Email) {
                    email = signUpRequest.email
                    password = signUpRequest.password
                }

                if (userInfo != null) {
                    val user = userInfo.toSupabaseUser()
                    val session = supabase.auth.currentSessionOrNull()
                    if (session != null) {
                        call.respond(HttpStatusCode.OK, session.toSupabaseAuthResponse())
                    } else {
                        call.respond(HttpStatusCode.OK, user)
                    }
                } else {
                    call.respond(
                        HttpStatusCode.Accepted,
                        mapOf("message" to "Verification email sent", "email" to signUpRequest.email)
                    )
                }

            } catch (e: AuthRestException) {
                val message = when (e.errorCode) {
                    AuthErrorCode.UserAlreadyExists -> "User already exists"
                    AuthErrorCode.WeakPassword -> "Password too weak"
                    else -> "Signup failed: ${e.message}"
                }
                call.respond(HttpStatusCode.InternalServerError, BackendErrorMessage(message))
            }
        }

        post("/signin") {
            val signInRequest = call.receive<SupabaseSignInRequest>()

            try {
                supabase.auth.signInWith(
                    provider = Email,
                    redirectUrl = "https://api.hazle.tashila.me/auth/confirm"
                ) {
                    email = signInRequest.email
                    password = signInRequest.password
                }

                val session: UserSession? = supabase.auth.currentSessionOrNull()
                if (session != null) {
                    call.respond(HttpStatusCode.OK, session.toSupabaseAuthResponse())
                } else {
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        BackendErrorMessage("Signin failed: Session not found")
                    )
                }

            } catch (e: AuthRestException) {
                val status = when (e.errorCode) {
                    AuthErrorCode.InvalidCredentials -> HttpStatusCode.Unauthorized
                    AuthErrorCode.EmailNotConfirmed -> HttpStatusCode.Forbidden
                    else -> HttpStatusCode.InternalServerError
                }
                call.respond(status, BackendErrorMessage("Signin failed: ${e.message}"))
            }
        }

        post("/refresh") {
            val request = call.receive<RefreshTokenRequest>()

            try {
                val session: UserSession = supabase.auth.refreshSession(request.refreshToken)
                call.respond(HttpStatusCode.OK, session.toSupabaseAuthResponse())
            } catch (e: AuthRestException) {
                val status = when (e.errorCode) {
                    AuthErrorCode.RefreshTokenNotFound,
                    AuthErrorCode.RefreshTokenAlreadyUsed -> HttpStatusCode.Unauthorized
                    else -> HttpStatusCode.InternalServerError
                }
                call.respond(status, BackendErrorMessage("Refresh failed: ${e.message}"))
            }
        }
    }
}

package me.tashila.auth

import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import me.tashila.data.MAX_EMAIL_LENGTH
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.OtpType
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.exception.AuthErrorCode
import io.github.jan.supabase.auth.exception.AuthRestException
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.user.UserSession
import io.ktor.server.html.respondHtml
import kotlinx.html.*

fun Route.auth(supabase: SupabaseClient) {
    route("/auth") {
        post("/signup") {
            val signUpRequest = call.receive<SupabaseSignUpRequest>()

            if (signUpRequest.email.length > MAX_EMAIL_LENGTH) {
                call.respond(HttpStatusCode.BadRequest, BackendErrorMessage("Email too long"))
                return@post
            }

            try {
                supabase.auth.signUpWith(
                    provider = Email,
                    redirectUrl = "https://api.hazle.tashila.me/auth/confirm"
                ) {
                    email = signUpRequest.email
                    password = signUpRequest.password
                }

                // If no exception was thrown, the email was sent successfully.
                // We return Accepted (202) because the account isn't "fully" ready yet.
                call.respond(
                    HttpStatusCode.Accepted,
                    SupabaseVerifyResponse("Verification email sent", signUpRequest.email)
                )

            } catch (e: AuthRestException) {
                val status = when (e.errorCode) {
                    AuthErrorCode.UserAlreadyExists -> HttpStatusCode.Conflict
                    AuthErrorCode.WeakPassword -> HttpStatusCode.BadRequest
                    else -> HttpStatusCode.InternalServerError
                }
                val message = when (e.errorCode) {
                    AuthErrorCode.UserAlreadyExists -> "User already exists"
                    AuthErrorCode.WeakPassword -> "Password too weak"
                    else -> e.message ?: "Signup failed"
                }
                call.respond(status, BackendErrorMessage(message))
            }
        }

        post("/signin") {
            val signInRequest = call.receive<SupabaseSignInRequest>()

            try {
                supabase.auth.signInWith(
                    provider = Email,
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

        get("/confirm") {
            val tokenHash = call.parameters["token_hash"]
            val type = call.parameters["type"] ?: "signup"

            val userAgent = call.request.headers["User-Agent"] ?: ""
            val isMobile = userAgent.lowercase().contains(
                Regex("android|iphone|mobile")
            )

            if (tokenHash == null) {
                call.respond(HttpStatusCode.BadRequest, "Missing token_hash")
                return@get
            }

            try {
                // This function returns Unit but updates the internal state
                supabase.auth.verifyEmailOtp(
                    type = OtpType.Email.valueOf(type.uppercase()),
                    tokenHash = tokenHash
                )

                // Now grab the session that was just created
                val session = supabase.auth.currentSessionOrNull()

                if (session != null) {
                    val redirectUrl = "https://api.hazle.tashila.me/#" +
                            "access_token=${session.accessToken}&" +
                            "refresh_token=${session.refreshToken}&" +
                            "type=$type"

                    if (isMobile) {
                        // This HTML page triggers the app and then "self-destructs" from browser history
                        call.respondHtml {
                            head {
                                script {
                                    unsafe {
                                        +"""
                        window.location.replace("$redirectUrl");
                        """.trimIndent()
                                    }
                                }
                            }
                            body {
                                p { +"Opening Hazle..." }
                            }
                        }
                    } else {
                        // Simple PC Success Page
                        call.respondHtml {
                            body {
                                style = "font-family: sans-serif; text-align: center; padding-top: 50px;"
                                h1 { +"Email Verified!" }
                                p { +"Please return to the app on your phone to continue." }
                            }
                        }
                    }
                } else {
                    call.respond(HttpStatusCode.Unauthorized, "Verification successful, but session not found.")
                }
            } catch (e: Exception) {
                val errorUrl = "https://api.hazle.tashila.me/#" +
                        "error=access_denied&" +
                        "error_description=${e.message?.encodeURLParameter()}"
                call.respondRedirect(errorUrl)
            }
        }

        post("/resend") {
            val email = call.receiveParameters()["email"] ?: return@post call.respond(HttpStatusCode.BadRequest)

            try {
                supabase.auth.resendEmail(OtpType.Email.SIGNUP, email)
                call.respond(HttpStatusCode.OK)
            } catch (e: Exception) {
                call.respond(HttpStatusCode.InternalServerError, e.message ?: "Error")
            }
        }
    }
}

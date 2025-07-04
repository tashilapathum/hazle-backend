package me.tashila.plugins

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.jwt.jwt
import io.ktor.server.response.respond
import me.tashila.auth.BackendErrorMessage
import me.tashila.config.SupabaseConfig

fun Application.configureAuth(supabaseConfig: SupabaseConfig) {
    install(Authentication) {
        jwt("auth-jwt") { // Name this authentication provider
            realm = "Ktor Supabase Auth" // Realm for the JWT provider

            // Configure the JWT verifier
            verifier(
                JWT
                    .require(Algorithm.HMAC256(supabaseConfig.jwtSecret)) // Use the dedicated JWT secret from Supabase
                    .withAudience("authenticated") // Audience claim in Supabase JWTs
                    .withIssuer("${supabaseConfig.url}/auth/v1") // Issuer claim in Supabase JWTs
                    .build()
            )

            // Custom validation logic for the JWT payload
            validate { credential ->
                // Check if the 'sub' (subject/user ID) claim exists and is not empty
                if (credential.payload.subject != null && credential.payload.subject.isNotEmpty()) {
                    JWTPrincipal(credential.payload) // Return a Principal if valid
                } else {
                    null // Authentication failed
                }
            }

            // Define the challenge response for unauthorized access
            challenge { defaultScheme, realm ->
                call.respond(HttpStatusCode.Unauthorized, BackendErrorMessage("Token is not valid or has expired"))
            }
        }
    }
}
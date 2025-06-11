package me.tashila.plugins

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.origin
import io.ktor.server.plugins.ratelimit.*
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import me.tashila.auth.BackendErrorMessage
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

fun Application.configureRateLimits() {
    install(RateLimit) {
        // Global rate limit (applies to all routes by default if no specific limiter is used)
        global {
            rateLimiter(limit = 10, refillPeriod = 60.seconds) // 10 requests per minute
        }

        // Register a named rate limiter for specific routes
        register(RateLimitName("protected")) {
            rateLimiter(limit = 5, refillPeriod = 30.seconds) // 5 requests per 30 seconds
        }

        // Another named rate limiter for login attempts
        register(RateLimitName("loginAttempts")) {
            rateLimiter(limit = 3, refillPeriod = 1.minutes) // 3 login attempts per minute
            requestKey { call -> call.request.origin.remoteHost } // limit by IP address
        }
    }

    install(StatusPages) {
        // Handle the 429 Too Many Requests status code
        status(HttpStatusCode.TooManyRequests) { call, _ ->
            call.respond(HttpStatusCode.TooManyRequests, BackendErrorMessage("Too many requests. Please try again later."))
        }
        exception<Throwable> { call, cause ->
            call.respond(HttpStatusCode.InternalServerError, BackendErrorMessage("An unexpected error occurred: ${cause.message}"))
        }
    }
}
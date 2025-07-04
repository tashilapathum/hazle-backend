package me.tashila.plugins

import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.origin
import io.ktor.server.plugins.ratelimit.*
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

fun Application.configureRateLimits() {
    install(RateLimit) { //TODO: update
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
}
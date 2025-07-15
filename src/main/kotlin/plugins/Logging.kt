package me.tashila.plugins

import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.request.path
import io.sentry.Sentry
import org.slf4j.event.Level

fun Application.configureLogging(env: String) {
    install(CallLogging) {
        // Log MDC properties from call for tracing
        mdc("environment") { env }
        mdc("path") { it.request.path() }

        level = when (env) {
            "development" -> Level.DEBUG
            "production" -> Level.INFO
            else -> Level.INFO
        }
    }

    Sentry.init { options ->
        options.dsn = "https://b398e3a0261e2a169e7375dc7239cb8e@o4509654455418880.ingest.de.sentry.io/4509670837846096"
        options.environment = env
    }
}
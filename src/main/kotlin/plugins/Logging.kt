package me.tashila.plugins

import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.calllogging.CallLogging
import io.sentry.Sentry
import org.slf4j.event.Level

fun Application.configureLogging() {
    install(CallLogging) {
        level = Level.DEBUG
    }

    Sentry.init { options ->
        options.dsn = "https://b398e3a0261e2a169e7375dc7239cb8e@o4509654455418880.ingest.de.sentry.io/4509670837846096"
    }
}
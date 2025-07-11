package me.tashila.plugins

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.application.log
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.path
import io.ktor.server.response.respond
import me.tashila.auth.BackendErrorMessage

fun Application.configureStatusPages() {
    install(StatusPages) {
        exception<Throwable> { call, cause -> // Catch any Throwable
            call.application.log.error("Unhandled exception in ${call.request.path()}:", cause)
            call.respond(HttpStatusCode.InternalServerError, BackendErrorMessage("Internal Server Error: ${cause.localizedMessage}"))
        }
        status(HttpStatusCode.Unauthorized) { call, status ->
            call.application.log.warn("Unauthorized access attempt to ${call.request.path()}. Status: $status")
            call.respond(status, BackendErrorMessage("Unauthorized: Please provide a valid token."))
        }
        status(HttpStatusCode.BadRequest) { call, status ->
            call.application.log.warn("Bad Request to ${call.request.path()}. Status: $status")
            call.respond(status, BackendErrorMessage("Bad Request: Check your request body or headers."))
        }
    }
}
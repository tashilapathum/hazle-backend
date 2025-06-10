
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ktor)
}

group = "me.tashila"
version = "0.0.1"

application {
    mainClass = "io.ktor.server.netty.EngineMain"
}

repositories {
    mavenCentral()
}

dependencies {

    //--------------------------- SERVER ------------------------------//

    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.netty)
    implementation(libs.ktor.client.cio)
    implementation(libs.logback.classic)
    implementation(libs.ktor.server.core)
    testImplementation(libs.ktor.server.test.host)
    testImplementation(libs.kotlin.test.junit)

    // Ktor Client JSON capabilities
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)

    // Auth
    implementation(libs.ktor.server.auth)
    implementation(libs.ktor.server.auth.jwt)

    // Logging
    implementation(libs.ktor.client.logging)
    implementation(libs.ktor.server.call.logging)

    //--------------------------- CLIENT ------------------------------//

    implementation(libs.openai.client)
    implementation(libs.ktor.client.cio)
    implementation(libs.ktor.client.content.negotiation)
}

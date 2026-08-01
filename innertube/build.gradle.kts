plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.client.encoding)
    implementation(libs.ktor.serialization.json)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines)
    // Some responses come back Brotli-encoded; the JDK decodes gzip but not this.
    implementation(libs.brotli.dec)
    // OkHttp logs through SLF4J and warns on every call without a binding.
    runtimeOnly(libs.slf4j.simple)
}

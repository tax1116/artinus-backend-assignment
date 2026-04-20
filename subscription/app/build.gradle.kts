plugins {
    id("spring-boot-convention")
    kotlin("plugin.jpa")
}

dependencies {
    implementation(project(":subscription:domain"))
    implementation(libs.spring.boot.starter.web)
    implementation(libs.spring.boot.starter.actuator)
    implementation(libs.spring.boot.starter.validation)
    implementation(libs.spring.boot.starter.data.jpa)
    implementation(libs.jackson.module.kotlin)
    implementation(libs.kotlin.reflect)

    implementation(libs.resilience4j.circuitbreaker)
    implementation(libs.resilience4j.retry)
    implementation(libs.resilience4j.timelimiter)

    runtimeOnly(libs.h2)

    testImplementation(libs.mockwebserver)
    testImplementation(libs.kotest.assertions)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

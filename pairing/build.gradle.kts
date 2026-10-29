plugins {
    alias(libs.plugins.kotlin.jvm)
    `java-library`
}

// Pure JVM (no Android) so the security-critical pairing logic is unit-tested on the desktop.
dependencies {
    implementation(project(":layouts"))
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.launcher)
}

tasks.test {
    useJUnitPlatform()
}

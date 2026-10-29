plugins {
    alias(libs.plugins.kotlin.jvm)
    `java-library`
}

// Pure JVM: generic IP cameras (LAN scan, ONVIF, RTSP). Frames go to the same FrameSink as BitVision.
dependencies {
    api(project(":bitvision-client"))

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.okhttp.mockwebserver)
    testRuntimeOnly(libs.junit.launcher)
}

tasks.test {
    useJUnitPlatform()
}

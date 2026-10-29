plugins {
    alias(libs.plugins.kotlin.jvm)
    application
}

dependencies {
    implementation(project(":bitvision-client"))
    implementation(project(":ipcam"))
}

application {
    mainClass.set("com.anhourtec.camgrid.cli.MainKt")
}

tasks.named<JavaExec>("run") {
    standardInput = System.`in`
}

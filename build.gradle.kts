import dev.detekt.gradle.extensions.DetektExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension
import org.jlleitschuh.gradle.ktlint.KtlintExtension

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ktlint) apply false
    alias(libs.plugins.detekt) apply false
}

/** Keep in step with `minSdk` in app/build.gradle.kts. */
val androidMinSdk = "25"

subprojects {
    apply(plugin = "org.jlleitschuh.gradle.ktlint")
    apply(plugin = "dev.detekt")

    extensions.configure<KtlintExtension> {
        version.set(rootProject.libs.versions.ktlint.cli)
    }

    extensions.configure<DetektExtension> {
        buildUponDefaultConfig.set(true)
        config.setFrom(rootProject.files("config/detekt/detekt.yml"))
    }

    plugins.withId("org.jetbrains.kotlin.jvm") {
        extensions.configure<KotlinJvmProjectExtension> {
            jvmToolchain(17)
            compilerOptions {
                allWarningsAsErrors.set(true)
            }
        }
        // Libraries the TV app uses compile against the desktop JDK, which has methods older
        // Android TVs lack (they crash with NoSuchMethodError). Check every JDK call against the
        // app's minSdk. The command-line tool only runs on computers, so it is skipped.
        if (name != "bitvision-client-cli") {
            val jar = tasks.named<Jar>("jar")
            val checkAndroidApi =
                tasks.register<Exec>("checkAndroidApi") {
                    group = "verification"
                    description = "Fails if this module calls JDK APIs newer than the TV app's minSdk."
                    dependsOn(jar)
                    val jarFile = jar.flatMap { it.archiveFile }
                    inputs.file(jarFile)
                    commandLine("python3", rootProject.file("scripts/check-android-api.py"), androidMinSdk, jarFile.get().asFile)
                }
            tasks.named("check") { dependsOn(checkAndroidApi) }
        }
    }
}

import dev.detekt.gradle.extensions.DetektExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension
import org.jlleitschuh.gradle.ktlint.KtlintExtension

plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ktlint) apply false
    alias(libs.plugins.detekt) apply false
}

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
    }
}

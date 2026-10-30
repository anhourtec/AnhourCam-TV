plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.anhourtec.camgrid.tv"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.anhourtec.camgrid.tv"
        // Android 7.1: covers Fire OS 6 Fire TV sticks as well as Google TV / Android TV.
        minSdk = 25
        targetSdk = 37
        versionCode = 2
        versionName = "0.2.0-preview"
    }

    // Release signing key: set per computer in ~/.gradle/gradle.properties (or as environment
    // variables on CI), never in the repo. Without them, release builds are simply unsigned.
    fun secret(name: String): String? =
        providers
            .gradleProperty(name)
            .orElse(providers.environmentVariable(name))
            .orNull
            ?.takeIf { it.isNotBlank() }
    val keystore = secret("ANHOURCAM_KEYSTORE_FILE")?.let { file(it.replaceFirst("~", System.getProperty("user.home"))) }
    signingConfigs {
        if (keystore != null && keystore.exists()) {
            create("release") {
                storeFile = keystore
                storePassword = secret("ANHOURCAM_KEYSTORE_PASSWORD")
                keyAlias = secret("ANHOURCAM_KEY_ALIAS") ?: "anhourcam"
                keyPassword = secret("ANHOURCAM_KEY_PASSWORD") ?: secret("ANHOURCAM_KEYSTORE_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.findByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    // The app switches language itself; keep every language in the APK.
    bundle {
        language {
            enableSplit = false
        }
    }

    lint {
        // Also check the pure-JVM modules (:pairing, :ipcam, …) against minSdk: a JDK method newer
        // than the TV's Android crashes there (NoSuchMethodError) even though it compiles.
        checkDependencies = true
        warningsAsErrors = true
        lintConfig = file("lint.xml")
    }

    packaging {
        resources.excludes += setOf("META-INF/*.version", "META-INF/*.kotlin_module", "kotlin/**", "DebugProbesKt.bin")
    }
}

kotlin {
    jvmToolchain(17)
    compilerOptions {
        allWarningsAsErrors.set(true)
    }
}

dependencies {
    implementation(project(":bitvision-client"))
    implementation(project(":pairing"))
    implementation(project(":layouts"))
    implementation(project(":ipcam"))
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.launcher)
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

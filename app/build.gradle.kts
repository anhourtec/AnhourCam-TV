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

    buildTypes {
        release {
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
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.launcher)
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

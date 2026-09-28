import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("com.android.kotlin.multiplatform.library")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.google.devtools.ksp")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.compose")
}

val roomVersion = "2.8.4"
val ktorVersion = "3.3.0"

/**
 * Never commit a real key. Copy local.properties.example to local.properties and put your free
 * api.esv.org key there; it's compiled into a generated `Secrets` object for both platforms.
 */
val generateSecrets by tasks.registering {
    val esvApiKey = providers.provider {
        Properties().apply {
            rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
        }.getProperty("ESV_API_KEY", "")
    }
    val outputDir = layout.buildDirectory.dir("generated/secrets/commonMain/kotlin")
    inputs.property("esvApiKey", esvApiKey)
    outputs.dir(outputDir)
    doLast {
        val escaped = esvApiKey.get().replace("\\", "\\\\").replace("\"", "\\\"").replace("$", "\\$")
        outputDir.get().file("com/firmlyplanted/app/Secrets.kt").asFile.apply {
            parentFile.mkdirs()
            writeText(
                "package com.firmlyplanted.app\n\n" +
                    "internal object Secrets {\n    const val ESV_API_KEY = \"$escaped\"\n}\n",
            )
        }
    }
}

kotlin {
    android {
        namespace = "com.firmlyplanted.app.shared"
        compileSdk = 35
        minSdk = 26

        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }

        // Needed for Compose resources (fonts, license strings) to be packaged into the APK.
        androidResources {
            enable = true
        }
    }

    compilerOptions {
        // Room's generated AppDatabaseConstructor is an expect/actual object.
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }

    listOf(
        iosArm64(),
        iosSimulatorArm64(),
    ).forEach { target ->
        target.binaries.framework {
            baseName = "Shared"
            isStatic = true
        }
    }

    sourceSets {
        commonMain {
            kotlin.srcDir(generateSecrets)
        }
        commonMain.dependencies {
            api(compose.runtime)
            api(compose.foundation)
            api(compose.material3)
            api(compose.ui)
            implementation(compose.components.resources)
            implementation("org.jetbrains.compose.material:material-icons-core:1.7.3")
            implementation("org.jetbrains.androidx.lifecycle:lifecycle-viewmodel-compose:2.9.5")
            implementation("org.jetbrains.androidx.lifecycle:lifecycle-runtime-compose:2.9.5")
            implementation("org.jetbrains.androidx.navigation:navigation-compose:2.9.1")

            api("org.jetbrains.kotlinx:kotlinx-datetime:0.7.1")
            api("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
            api("androidx.room:room-runtime:$roomVersion")
            implementation("androidx.sqlite:sqlite-bundled:2.6.2")
            implementation("io.ktor:ktor-client-core:$ktorVersion")
            implementation("io.ktor:ktor-client-logging:$ktorVersion")
            api("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
        }
        androidMain.dependencies {
            implementation("io.ktor:ktor-client-okhttp:$ktorVersion")
        }
        iosMain.dependencies {
            implementation("io.ktor:ktor-client-darwin:$ktorVersion")
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
        all {
            languageSettings.optIn("androidx.compose.material3.ExperimentalMaterial3Api")
            languageSettings.optIn("kotlin.time.ExperimentalTime")
            languageSettings.optIn("kotlin.uuid.ExperimentalUuidApi")
        }
    }
}

dependencies {
    listOf("kspAndroid", "kspIosArm64", "kspIosSimulatorArm64").forEach {
        add(it, "androidx.room:room-compiler:$roomVersion")
    }
}

compose.resources {
    packageOfResClass = "com.firmlyplanted.app.resources"
}

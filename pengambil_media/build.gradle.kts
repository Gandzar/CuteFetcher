@file:Suppress("UnstableApiUsage")

import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("multiplatform")
    id("com.android.kotlin.multiplatform.library")
}

// Inti PengelolaScraper: model hasil, validasi URL, deteksi platform, rantai fallback.
// Kotlin murni, tanpa dependensi platform. Adapter mesin nyata (Scrapr) ada di modul lain.
kotlin {
    jvm {
        compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
    }

    android {
        namespace = "dev.bagja.streamvortex.pengambil_media"
        compileSdk = Versions.compileSdk
        minSdk = Versions.minSdk
        compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
    }

    withIosTargets()
    withMacosTargets()
    withLinuxTargets()

    applyDefaultHierarchyTemplate()

    sourceSets {
        commonMain.dependencies {
            api(Libs.Kotlinx.coroutinesCore)
            implementation(Libs.Kotlinx.serializationJson)
        }

        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(Libs.Kotlinx.coroutinesTest)
        }
    }
}

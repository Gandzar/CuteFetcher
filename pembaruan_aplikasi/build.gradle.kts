@file:Suppress("UnstableApiUsage")

import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("multiplatform")
    id("com.android.kotlin.multiplatform.library")
}

// Penguraian rilis GitHub -> AppUpdate (model updater YMusic). Kotlin murni; HTTP ada di shell Android.
kotlin {
    jvm {
        compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
    }

    android {
        namespace = "dev.bagja.streamvortex.pembaruan_aplikasi"
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
            api(project(":remote-config")) // AppUpdate
            implementation(Libs.Kotlinx.serializationJson)
        }

        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}

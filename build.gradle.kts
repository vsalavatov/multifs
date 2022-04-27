plugins {
    kotlin("multiplatform") version Versions.kotlin
    kotlin("plugin.serialization") version Versions.kotlinSerialization
    id("maven-publish")
    id("com.android.library") version Versions.androidGradlePlugin
}

group = "dev.salavatov"
version = Versions.multifs

repositories {
    mavenCentral()
    google()
}

kotlin {
    jvm {
        compilations.all {
            kotlinOptions.jvmTarget = "1.8"
            kotlinOptions.freeCompilerArgs += listOf(
                "-Xuse-experimental=kotlinx.coroutines.ExperimentalCoroutinesApi",
                "-opt-in=kotlin.RequiresOptIn"
            )
        }
        testRuns["test"].executionTask.configure {
            useJUnit()
        }
    }
    android() {
        publishAllLibraryVariants()
    }
    js(IR) {
        browser {
            commonWebpackConfig {
                cssSupport.enabled = true
            }
        }
    }
    val hostOs = System.getProperty("os.name")
    val isMingwX64 = hostOs.startsWith("Windows")
    val nativeTarget = when {
        hostOs == "Mac OS X" -> macosX64("native")
        hostOs == "Linux" -> linuxX64("native")
        isMingwX64 -> mingwX64("native")
        else -> throw GradleException("Host OS is not supported in Kotlin/Native.")
    }


    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:${Versions.kotlinCoroutines}")
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:${Versions.kotlinxSerializationJson}")

                implementation("io.ktor:ktor-client-core:${Versions.ktor}")
                implementation("io.ktor:ktor-client-auth:${Versions.ktor}")
                implementation("io.ktor:ktor-serialization-kotlinx-json:${Versions.ktor}")
                implementation("io.ktor:ktor-utils:${Versions.ktor}")
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }
        val commonJvmAndroid = create("commonJvmAndroid") {
            dependsOn(commonMain)
            dependencies {}
        }
        val jvmMain by getting {
            dependsOn(commonJvmAndroid)
            dependencies {
                implementation("io.ktor:ktor-client-cio:${Versions.ktor}")

                implementation("io.ktor:ktor-server-core:${Versions.ktor}")
                implementation("io.ktor:ktor-server-cio:${Versions.ktor}")
                implementation("io.ktor:ktor-utils-jvm:${Versions.ktor}")
            }
        }
        val jvmTest by getting {
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:${Versions.kotlinxCoroutinesTest}")
            }
        }
        val androidMain by getting {
            dependsOn(commonJvmAndroid)
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:${Versions.kotlinCoroutines}")

                implementation("androidx.activity:activity-ktx:${Versions.androidActivityKtx}")
                implementation("com.google.android.gms:play-services-auth:${Versions.googlePlayServicesAuth}")

                implementation("io.ktor:ktor-client-okhttp:${Versions.ktor}")
            }
        }
        val jsMain by getting {
            dependencies {
                implementation("io.ktor:ktor-client-js:${Versions.ktor}")
            }
        }
        val jsTest by getting
    }
}

android {
    sourceSets {
        named("main") {
            val androidMain = "src/androidMain"
            manifest.srcFile("$androidMain/AndroidManifest.xml")
            res.setSrcDirs(listOf("$androidMain/res", "src/commonMain/resources/"))
            java.setSrcDirs(listOf("$androidMain/java"))
            kotlin.setSrcDirs(listOf("$androidMain/kotlin", "src/commonJvmAndroid/kotlin"))
        }
    }
    compileSdk = Versions.androidCompileSdk
    defaultConfig {
        minSdk = Versions.androidMinSdk
        targetSdk = Versions.androidTargetSdk
    }
}

tasks.named<Test>("jvmTest") {
    useJUnitPlatform()
    testLogging {
        showExceptions = true
        showStandardStreams = true
        events = setOf(
            org.gradle.api.tasks.testing.logging.TestLogEvent.FAILED,
            org.gradle.api.tasks.testing.logging.TestLogEvent.PASSED
        )
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}


// some strange non-existent module called "androidAndroidTestRelease" appears after gradle configuration
// here is some fix from the internet (https://discuss.kotlinlang.org/t/disabling-androidandroidtestrelease-source-set-in-gradle-kotlin-dsl-script/21448)
project.extensions.findByType<org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension>()?.let { ext ->
    ext.sourceSets.removeAll { sourceSet ->
        setOf(
            "androidAndroidTestRelease",
            "androidTestFixtures",
            "androidTestFixturesDebug",
            "androidTestFixturesRelease",
        ).contains(sourceSet.name)
    }
}

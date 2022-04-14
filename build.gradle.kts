plugins {
    kotlin("multiplatform") version "1.6.10"
    kotlin("plugin.serialization") version "1.6.10"
    id("maven-publish")
    id("com.android.library") version "7.0.0"
}

group = "dev.salavatov"
version = "0.0.1"

repositories {
    mavenCentral()
    google()

    maven {
        url = uri("https://maven.pkg.jetbrains.space/public/p/ktor/eap") // for ktor 2.0.0-eap
    }
}

val serializationVersion: String by project
val ktorVersion: String by project

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
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:$serializationVersion")

                implementation("io.ktor:ktor-client-core:$ktorVersion")
                implementation("io.ktor:ktor-client-auth:$ktorVersion")
                implementation("io.ktor:ktor-client-serialization:$ktorVersion")
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }
        val jvmMain by getting {
            dependencies {
                implementation("io.ktor:ktor-client-cio:$ktorVersion")

                implementation("io.ktor:ktor-server-core:$ktorVersion")
                implementation("io.ktor:ktor-server-cio:$ktorVersion")
            }
        }
        val jvmTest by getting {
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.5.1")
            }
        }
        val androidMain by getting {
            dependencies {
                implementation("androidx.activity:activity-ktx:1.4.0")
                implementation("com.google.android.gms:play-services-auth:20.1.0")
                implementation("io.ktor:ktor-client-okhttp:$ktorVersion")
            }
        }
        val jsMain by getting {
            dependencies {
                implementation("io.ktor:ktor-client-js:$ktorVersion")
            }
        }
        val jsTest by getting
        val nativeMain by getting {
            dependencies {
                implementation("io.ktor:ktor-client-curl:$ktorVersion")
            }
        }
        val nativeTest by getting
    }
}

android {
    sourceSets {
        named("main") {
            val androidMain = "src/androidMain"
            manifest.srcFile("$androidMain/AndroidManifest.xml")
            res.setSrcDirs(listOf("$androidMain/res", "src/commonMain/resources/"))
            java.setSrcDirs(listOf("$androidMain/java"))
            kotlin.setSrcDirs(listOf("$androidMain/kotlin"))
        }
    }
    compileSdkVersion(29) // TODO
    defaultConfig {
        minSdkVersion(21)
        targetSdkVersion(29)
    }
}

tasks.named<Test>("jvmTest") {
    useJUnitPlatform()
    testLogging {
        showExceptions = true
        showStandardStreams = true
        events = setOf(org.gradle.api.tasks.testing.logging.TestLogEvent.FAILED, org.gradle.api.tasks.testing.logging.TestLogEvent.PASSED)
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


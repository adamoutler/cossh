plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.jetbrains.kotlin.android)
    alias(libs.plugins.jetbrains.kotlin.serialization)
    alias(libs.plugins.paparazzi)
}

val gitCommitCount = try {
    Runtime.getRuntime().exec("git rev-list --count HEAD").inputStream.bufferedReader().readText().trim().toInt()
} catch (e: Exception) {
    1
}

android {
    namespace = "com.adamoutler.ssh"
    compileSdk = 35
    ndkVersion = "29.0.14206865"

    defaultConfig {
        applicationId = "com.adamoutler.cobaltssh"
        minSdk = 26
        targetSdk = 35
        versionCode = gitCommitCount
        versionName = "1.$gitCommitCount"

        // We compile libtermux.so locally using the NDK to ensure it is aligned to 16KB page boundaries.
        // This clears Android 15/16 App Compatibility warnings on strict 16KB page-sized devices.
        externalNativeBuild {
            ndkBuild {
                arguments += listOf("-j" + Runtime.getRuntime().availableProcessors())
            }
        }

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        if (!project.hasProperty("fullTestRun")) {
            testInstrumentationRunnerArguments["notAnnotation"] = "com.adamoutler.ssh.annotations.FullTest"
        }
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    // Link the termux native C code. Re-compiling locally is mandatory
    // for Android 15/16 16KB ELF LOAD segment alignment.
    externalNativeBuild {
        ndkBuild {
            path = file("src/main/jni/Android.mk")
        }
    }

    signingConfigs {
        create("release") {
            storeFile = file(System.getProperty("user.home") + "/.android/release.keystore")
            storePassword = "android"
            keyAlias = "cossh_release"
            keyPassword = "android"
        }
    }

    buildTypes {
        getByName("debug") {
            applicationIdSuffix = ".debug"
        }
        release {
            isMinifyEnabled = true
            ndk {
                debugSymbolLevel = "FULL"
            }
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.12"
    }
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            all {
                if (name.contains("Release")) {
                    it.exclude("**/TerminalExtraKeysUITest.class")
                }
                it.maxParallelForks = (Runtime.getRuntime().availableProcessors() / 2).coerceAtLeast(1)
                if (!project.hasProperty("fullTestRun")) {
                    it.useJUnit {
                        excludeCategories("com.adamoutler.ssh.annotations.FullTest")
                    }
                }
            }
        }
    }
    packaging {
        jniLibs {
            useLegacyPackaging = true
            pickFirsts.add("lib/**/libtermux.so")
        }
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "META-INF/DEPENDENCIES"
            excludes += "META-INF/INDEX.LIST"
            excludes += "META-INF/io.netty.versions.properties"
            excludes += "META-INF/versions/9/OSGI-INF/MANIFEST.MF"
        }
    }
}

configurations.all {
    exclude(group = "com.google.guava", module = "listenablefuture")
    exclude(group = "org.bouncycastle", module = "bcprov-jdk15on")
    exclude(group = "org.bouncycastle", module = "bcpkix-jdk15on")
    exclude(group = "org.bouncycastle", module = "bcutil-jdk15on")
}

dependencies {
    implementation(libs.jose4j)
    implementation(libs.jdom2)
    implementation(libs.commons.io)
    implementation(libs.protobuf.java)
    implementation(libs.guava)
    implementation(libs.netty.codec.http2)
    implementation(libs.netty.codec.http)
    implementation(libs.netty.codec)
    implementation(libs.netty.common)
    implementation(libs.netty.handler)

    testImplementation("org.bouncycastle:bcprov-jdk15on:1.70")
    testImplementation("org.bouncycastle:bcpkix-jdk15on:1.70")
    testImplementation("org.bouncycastle:bcutil-jdk15on:1.70")
    testImplementation(libs.netty.codec.http2)
    testImplementation(libs.netty.codec.http)
    testImplementation(libs.netty.codec)
    testImplementation(libs.netty.common)
    testImplementation(libs.netty.handler)
    testImplementation(libs.netty.transport)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.security.crypto.ktx)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.sshj)
    implementation(libs.slf4j.nop)
    implementation(libs.termux.terminal.view)
    implementation(libs.termux.shared)
    implementation(libs.bouncycastle.prov)
    implementation(libs.bcpkix.jdk18on)
    
    implementation(libs.billing.client)
    implementation(libs.credential.manager)
    implementation(libs.credential.manager.play.services.auth)
    implementation(libs.play.services.auth)
    implementation(libs.google.api.services.drive) {
        exclude(group = "org.apache.httpcomponents")
    }
    implementation(libs.google.api.client.android)
    implementation(libs.google.auth.library.oauth2.http)
    implementation(libs.androidx.work.runtime.ktx)
    implementation("com.google.android.libraries.identity.googleid:googleid:1.1.0")
    
    testImplementation(libs.junit)
    testImplementation(libs.sshd.core)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric) {
        exclude(group = "org.bouncycastle", module = "bcprov-jdk15on")
        exclude(group = "org.bouncycastle", module = "bcpkix-jdk15on")
        exclude(group = "org.bouncycastle", module = "bcutil-jdk15on")
    }
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.ui.test.junit4)
    testImplementation("org.hamcrest:hamcrest:2.2")
    testImplementation("androidx.test:core:1.5.0")
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation("androidx.test.espresso:espresso-intents:3.5.1")
    androidTestImplementation("androidx.test:rules:1.5.0")
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    androidTestImplementation("androidx.test.uiautomator:uiautomator:2.3.0")
    androidTestImplementation(libs.sshd.core)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
    testImplementation(libs.androidx.ui.test.manifest)
}

tasks.withType<Test> {
    testLogging {
        events("passed", "skipped", "failed")
    }
    
    addTestListener(object : org.gradle.api.tasks.testing.TestListener {
        override fun beforeSuite(suite: org.gradle.api.tasks.testing.TestDescriptor) {}
        override fun beforeTest(testDescriptor: org.gradle.api.tasks.testing.TestDescriptor) {}
        override fun afterTest(testDescriptor: org.gradle.api.tasks.testing.TestDescriptor, result: org.gradle.api.tasks.testing.TestResult) {
            val duration = result.endTime - result.startTime
            println("⏱️ TEST-METRIC: ${testDescriptor.className}.${testDescriptor.name} took ${duration}ms")
        }
        override fun afterSuite(suite: org.gradle.api.tasks.testing.TestDescriptor, result: org.gradle.api.tasks.testing.TestResult) {
            if (suite.parent == null) {
                if (!project.hasProperty("fullTestRun")) {
                    println("ℹ️  Standard test suite completed. Note: Long-running @FullTest tests were SKIPPED.")
                    println("ℹ️  Recommendation: Run './gradlew test connectedAndroidTest -PfullTestRun' for a complete overview.")
                } else {
                    println("✅ FULL TEST SUITE EXECUTED.")
                }
            }
        }
    })
}

afterEvaluate {
    tasks.named("installDebug") {
        doFirst {
            val deviceIp = project.findProperty("deviceIp") as? String ?: "192.168.1.39:42513"
            val userHome = System.getProperty("user.home")
            println("🔌 Automatically connecting to device $deviceIp before install...")
            exec {
                environment("ADB_VENDOR_KEYS", "$userHome/.android")
                commandLine("adb", "connect", deviceIp)
                isIgnoreExitValue = true
            }
        }
    }
}

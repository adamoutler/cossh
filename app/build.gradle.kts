plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.jetbrains.kotlin.android)
    alias(libs.plugins.jetbrains.kotlin.serialization)
    alias(libs.plugins.paparazzi)
}

android {
    namespace = "com.adamoutler.ssh"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.adamoutler.ssh"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        if (!project.hasProperty("fullTestRun")) {
            testInstrumentationRunnerArguments["notAnnotation"] = "com.adamoutler.ssh.annotations.FullTest"
        }
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
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
                it.maxParallelForks = (Runtime.getRuntime().availableProcessors() / 2).coerceAtLeast(1)
            }
        }
    }
    packaging {
        jniLibs {
            useLegacyPackaging = true
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
    implementation(libs.androidx.security.crypto.ktx)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.sshj)
    implementation(libs.slf4j.nop)
    implementation(libs.termux.terminal.view)
    implementation(libs.termux.shared)
    implementation(libs.bouncycastle.prov)
    
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

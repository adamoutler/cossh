buildscript {
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        classpath("io.netty:netty-codec-http:4.1.108.Final")
        classpath("org.bitbucket.b_c:jose4j:0.9.4")
        classpath("org.bouncycastle:bcprov-jdk18on:1.84")
        classpath("org.bouncycastle:bcpkix-jdk18on:1.84")
        classpath("org.bouncycastle:bcutil-jdk18on:1.84")
    }
}
// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.jetbrains.kotlin.android) apply false
}

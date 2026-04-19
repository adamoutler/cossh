buildscript {
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        classpath("com.google.protobuf:protobuf-java:3.25.5")
        classpath("com.google.guava:guava:33.3.1-jre")
        classpath("commons-io:commons-io:2.14.0")
        classpath("org.jdom:jdom2:2.0.6.1")
        classpath("org.bitbucket.b_c:jose4j:0.9.6")
        classpath("org.bouncycastle:bcprov-jdk18on:1.80")
        classpath("org.bouncycastle:bcpkix-jdk18on:1.80")
        classpath("org.bouncycastle:bcutil-jdk18on:1.80")
        classpath("org.apache.commons:commons-compress:1.26.1")
        classpath("org.apache.httpcomponents:httpclient:4.5.13")
    }
}

// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.jetbrains.kotlin.android) apply false
}

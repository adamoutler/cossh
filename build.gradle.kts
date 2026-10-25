buildscript {
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        classpath("com.google.protobuf:protobuf-java:3.25.5")
        classpath("com.google.guava:guava:33.3.1-jre")
        classpath("commons-io:commons-io:2.22.0")
        classpath("org.jdom:jdom2:2.0.6.1")
        classpath("org.bitbucket.b_c:jose4j:0.9.6") // updated
        classpath("org.bouncycastle:bcprov-jdk18on:1.86")
        classpath("org.bouncycastle:bcpkix-jdk18on:1.86")
        classpath("org.bouncycastle:bcutil-jdk18on:1.86")
        classpath("org.apache.commons:commons-compress:1.26.1")
        classpath("org.apache.httpcomponents:httpclient:4.5.13")
    }
    configurations.all {
        resolutionStrategy.eachDependency {
            if (requested.group == "io.netty" && requested.name.startsWith("netty-")) {
                useVersion("4.1.139.Final")
            }
            if (requested.group == "org.bouncycastle" && requested.name.endsWith("-jdk18on")) {
                useVersion("1.86")
            }
        }
    }
}

// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.jetbrains.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.detekt)
    id("com.diffplug.spotless") version "8.4.0" apply false
    id("org.sonarqube") version "4.4.1.3373" apply false
}

detekt {
    config.setFrom(files("config/detekt/detekt.yml"))
    source.setFrom(files("app/src/main/kotlin", "app/src/main/java"))
}

allprojects {
    buildscript {
        configurations.all {
            resolutionStrategy.eachDependency {
                if (requested.group == "io.netty" && requested.name.startsWith("netty-")) {
                    useVersion("4.1.139.Final")
                }
                if (requested.group == "org.bouncycastle" && requested.name.endsWith("-jdk18on")) {
                    useVersion("1.86")
                }
                if (requested.group == "org.jetbrains.kotlin" && requested.name.startsWith("kotlin-gradle-plugin")) {
                    useVersion("2.4.21")
                }
            }
        }
    }
    configurations.all {
        resolutionStrategy.eachDependency {
            if (requested.group == "io.netty" && requested.name.startsWith("netty-")) {
                useVersion("4.1.139.Final")
            }
            if (requested.group == "org.bouncycastle" && requested.name.endsWith("-jdk18on")) {
                useVersion("1.86")
            }
        }
    }
    dependencyLocking {
        lockAllConfigurations()
    }
}

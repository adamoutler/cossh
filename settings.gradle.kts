pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
buildscript {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
    dependencies {
        classpath("io.netty:netty-codec-http:4.1.132.Final")
        classpath("io.netty:netty-codec-http2:4.1.132.Final")
        classpath("io.netty:netty-codec:4.1.132.Final")
        classpath("io.netty:netty-common:4.1.132.Final")
        classpath("io.netty:netty-handler:4.1.132.Final")
        classpath("io.netty:netty-transport:4.1.132.Final")
        classpath("io.netty:netty-buffer:4.1.132.Final")
        classpath("io.netty:netty-resolver:4.1.132.Final")
        classpath("io.netty:netty-transport-native-unix-common:4.1.132.Final")
        classpath("org.bitbucket.b_c:jose4j:0.9.6")
        classpath("org.bouncycastle:bcprov-jdk18on:1.84")
        classpath("org.bouncycastle:bcpkix-jdk18on:1.84")
        classpath("org.bouncycastle:bcutil-jdk18on:1.84")
        classpath("org.jdom:jdom2:2.0.6.1")
        classpath("commons-io:commons-io:2.14.0")
        classpath("com.google.protobuf:protobuf-java:3.25.5")
    }
    configurations.classpath {
        resolutionStrategy {
            force("io.netty:netty-codec-http:4.1.132.Final")
            force("io.netty:netty-codec-http2:4.1.132.Final")
            force("io.netty:netty-codec:4.1.132.Final")
            force("io.netty:netty-common:4.1.132.Final")
            force("io.netty:netty-handler:4.1.132.Final")
            force("io.netty:netty-transport:4.1.132.Final")
            force("io.netty:netty-buffer:4.1.132.Final")
            force("io.netty:netty-resolver:4.1.132.Final")
            force("io.netty:netty-transport-native-unix-common:4.1.132.Final")
            force("org.bitbucket.b_c:jose4j:0.9.6")
            force("org.bouncycastle:bcprov-jdk18on:1.84")
            force("org.bouncycastle:bcpkix-jdk18on:1.84")
            force("org.bouncycastle:bcutil-jdk18on:1.84")
            force("org.jdom:jdom2:2.0.6.1")
            force("commons-io:commons-io:2.14.0")
            force("com.google.protobuf:protobuf-java:3.25.5")
            
            dependencySubstitution {
                substitute(module("org.bouncycastle:bcprov-jdk15on")).using(module("org.bouncycastle:bcprov-jdk18on:1.84"))
                substitute(module("org.bouncycastle:bcpkix-jdk15on")).using(module("org.bouncycastle:bcpkix-jdk18on:1.84"))
                substitute(module("org.bouncycastle:bcutil-jdk15on")).using(module("org.bouncycastle:bcutil-jdk18on:1.84"))
            }
        }
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}
rootProject.name = "CoSSH"
include(":app")

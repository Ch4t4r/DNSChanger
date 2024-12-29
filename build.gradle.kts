plugins {
    id("maven-publish")
}

buildscript {
    extra.apply {
        set("nexus_user", providers.gradleProperty("NEXUS_USER").orNull)
        set("nexus_password", providers.gradleProperty("NEXUS_PASSWORD").orNull)
    }

    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        classpath("com.android.tools.build:gradle:8.7.3")
    }
}

allprojects {
    repositories {
        google()
        mavenCentral()
        maven {
            url = uri("https://nexus.frostnerd.com/repository/libs-release/")
            credentials {
                username = rootProject.extra.get("nexus_user")?.toString()
                password = rootProject.extra.get("nexus_password")?.toString()
            }
        }
        maven {
            url = uri("https://oss.sonatype.org/content/repositories/snapshots")
        }
    }
}

tasks.register<Delete>("clean") {
    delete(rootProject.layout.buildDirectory)
}

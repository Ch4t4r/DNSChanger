plugins {
    id("com.android.application")
    id("project-report")
}

android {
    namespace = "com.frostnerd.dnschanger"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.frostnerd.dnschanger"
        minSdk = 26
        targetSdk = 35
        versionCode = 125
        versionName = "1.16.5.11"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures {
        buildConfig = true
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            buildConfigField("Boolean", "SENTRY_ENABLED", "true")
            buildConfigField("String", "SENTRY_DSN", "\"dummy\"")
        }
        debug {
            initWith(getByName("release"))
            isMinifyEnabled = false
            isShrinkResources = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            buildConfigField("Boolean", "SENTRY_ENABLED", "true")
            buildConfigField("String", "SENTRY_DSN", "\"dummy\"")
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    lint {
        abortOnError = false
    }

    packaging {
        resources {
            excludes += "META-INF/library_release.kotlin_module"
        }
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
        unitTests.isIncludeAndroidResources = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
}

dependencies {
    implementation("com.frostnerd.utils:general:1.0.11")
    implementation("com.frostnerd.utils:database:1.1.25")
    implementation("com.frostnerd.utils:materialedittext:1.0.21")
    implementation("com.frostnerd.utils:networking:1.0.5")
    implementation("com.frostnerd.utils:api:1.0.6")
    implementation("com.frostnerd.utilskt:lifecycle:1.2.7")
    implementation("com.frostnerd.utils:preferences:2.4.11")
    implementation("com.frostnerd.utils:preferenceexport:1.0.11")
    implementation("com.frostnerd.utils:design:1.0.19")
    implementation("com.frostnerd.utils:lifecycle:1.0.8")

    implementation("io.sentry:sentry-android:4.0.0-alpha.2") // Updated to stable release

    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.cardview:cardview:1.0.0")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.preference:preference:1.2.1")
    implementation("androidx.activity:activity:1.9.3")
    implementation("androidx.fragment:fragment:1.8.5")
    implementation("androidx.localbroadcastmanager:localbroadcastmanager:1.1.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("com.google.code.gson:gson:2.11.0")
    implementation("org.pcap4j:pcap4j-core:1.8.2")
    implementation("org.pcap4j:pcap4j-packetfactory-static:1.8.2")
    implementation("org.minidns:minidns-core:1.0.0")
    implementation("org.minidns:minidns-hla:1.0.0")

    implementation("com.google.android.play:core:1.10.3") // Note: May need migration for Play Core
}

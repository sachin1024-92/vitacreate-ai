import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// AdMob IDs. These are public identifiers (they ship inside every APK), not secrets.
// Release builds carry the real IDs and earn; the environment can override them.
// Debug builds always use Google's published test IDs, so testing on your own phone shows
// test ads only and can never cause an invalid-traffic strike on the AdMob account.
val admobAppId: String = System.getenv("ADMOB_APP_ID")?.takeIf { it.isNotBlank() }
    ?: "ca-app-pub-7040144214936380~9265551190"
val admobBannerId: String = System.getenv("ADMOB_BANNER_ID")?.takeIf { it.isNotBlank() }
    ?: "ca-app-pub-7040144214936380/2339236109"
val admobTestAppId = "ca-app-pub-3940256099942544~3347511713"
val admobTestBannerId = "ca-app-pub-3940256099942544/9214589741"

// Release signing is read from the environment so no secrets live in the repo.
// Without it, release builds fall back to the debug key so the APK is still installable.
val releaseKeystore: String? = System.getenv("FLOATNOTE_KEYSTORE_FILE")

android {
    namespace = "io.github.sachin102492.floatnote"
    compileSdk = 36

    defaultConfig {
        applicationId = "io.github.sachin102492.floatnote"
        minSdk = 26
        targetSdk = 36
        versionCode = 3
        versionName = "1.2.0"
    }

    buildFeatures {
        buildConfig = true
    }

    // "play" shows one banner ad on the notes list and sells optional supporter tips through
    // Google Play Billing; a tip removes the ad.
    // "foss" has no Google code, no ads and no Internet permission (for F-Droid): every perk
    // is free and it links to donations.
    flavorDimensions += "store"
    productFlavors {
        create("play") {
            dimension = "store"
            buildConfigField("boolean", "PERKS_FREE", "false")
            buildConfigField("String", "DONATE_URL", "\"\"")
        }
        create("foss") {
            dimension = "store"
            versionNameSuffix = "-foss"
            buildConfigField("boolean", "PERKS_FREE", "true")
            buildConfigField("String", "DONATE_URL", "\"https://github.com/sponsors/sachin1024-92\"")
        }
    }

    signingConfigs {
        releaseKeystore?.let { path ->
            create("release") {
                storeFile = file(path)
                storePassword = System.getenv("FLOATNOTE_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("FLOATNOTE_KEY_ALIAS")
                keyPassword = System.getenv("FLOATNOTE_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
            buildConfigField("String", "ADMOB_BANNER_ID", "\"$admobBannerId\"")
            manifestPlaceholders["admobAppId"] = admobAppId
        }
        debug {
            applicationIdSuffix = ".debug"
            buildConfigField("String", "ADMOB_BANNER_ID", "\"$admobTestBannerId\"")
            manifestPlaceholders["admobAppId"] = admobTestAppId
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    // Keep the APK free of Google-only metadata so F-Droid can build it reproducibly.
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    lint {
        abortOnError = false
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("androidx.activity:activity-ktx:1.10.1")
    implementation("androidx.recyclerview:recyclerview:1.4.0")
    implementation("androidx.coordinatorlayout:coordinatorlayout:1.3.0")
    implementation("androidx.dynamicanimation:dynamicanimation:1.0.0")
    implementation("com.google.android.material:material:1.12.0")
    "playImplementation"("com.android.billingclient:billing-ktx:8.0.0")
    "playImplementation"("com.google.android.gms:play-services-ads:23.6.0")
    "playImplementation"("com.google.android.ump:user-messaging-platform:3.1.0")
}

import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

/**
 * Doc cau hinh Supabase tu local.properties (khong commit) roi day vao BuildConfig.
 * Khoa anon la khoa public cua Supabase — app TV chi doc, RLS chan moi thao tac ghi
 * ngoai watch_progress. Nhung van khong nen hardcode trong source.
 */
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
fun conf(key: String): String = (localProps.getProperty(key) ?: System.getenv(key) ?: "")

android {
    namespace = "com.youtube.tv"
    // Cac thu vien androidx moi nhat doi compile chong API 37 tro len.
    // targetSdk giu o 36: khong can opt-in cac thay doi hanh vi cua API 37.
    compileSdk = 37

    defaultConfig {
        applicationId = "com.youtube.tv"
        minSdk = 23
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"

        buildConfigField("String", "SUPABASE_URL", "\"${conf("SUPABASE_URL")}\"")
        buildConfigField("String", "SUPABASE_ANON_KEY", "\"${conf("SUPABASE_ANON_KEY")}\"")
        // Khong bat buoc. Neu dien thi man hinh Ket noi tren TV se nhac dung dia
        // chi nay, do phai doc thuoc, vi du: youtube.nhatoi.com
        buildConfigField("String", "ADMIN_URL", "\"${conf("ADMIN_URL")}\"")
    }

    /**
     * Chu ky cho ban release. Tao keystore mot lan roi khai bao duong dan trong
     * local.properties — xem docs/SETUP.md. Neu chua co keystore, task
     * assembleRelease van chay nhung APK khong duoc ky nen khong cai duoc.
     */
    val keystorePath = conf("YOUTUBE_KEYSTORE")
    val hasKeystore = keystorePath.isNotEmpty() && file(keystorePath).exists()

    signingConfigs {
        if (hasKeystore) {
            create("release") {
                storeFile = file(keystorePath)
                storePassword = conf("YOUTUBE_KEYSTORE_PASSWORD")
                keyAlias = conf("YOUTUBE_KEY_ALIAS").ifEmpty { "youtube" }
                keyPassword = conf("YOUTUBE_KEY_PASSWORD").ifEmpty {
                    conf("YOUTUBE_KEYSTORE_PASSWORD")
                }
            }
        }
    }

    buildTypes {
        release {
            if (hasKeystore) signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
        debug {
            applicationIdSuffix = ".debug"
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.foundation)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.tv.material)

    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)

    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)

    implementation(libs.youtube.player)
}

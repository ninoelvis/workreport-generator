plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.workreport.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.workreport.app"
        // 最低支持 Android 8.0 (API 26)
        minSdk = 26
        targetSdk = 34
        versionCode = 26
        versionName = "1.4.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true
    }

    signingConfigs {
        create("release") {
            // 签名证书与历史版本保持一致，保证可直接覆盖安装。
            // 证书路径按优先级依次尝试：环境变量 WR_KEYSTORE → 工程内 keystore/ → 工程上一级 keystore/。
            // 用「存在性」判断而不是直接取第一个，避免环境变量指向失效路径时构建失败。
            val keystoreCandidates = listOfNotNull(
                System.getenv("WR_KEYSTORE"),
                "keystore/release.keystore",
                "../keystore/release.keystore",
            )
            storeFile = keystoreCandidates
                .map { file(it) }
                .firstOrNull { it.exists() }
                ?: file("keystore/release.keystore")
            storePassword = System.getenv("WR_KEYSTORE_PASS") ?: "workreport2026"
            keyAlias = System.getenv("WR_KEY_ALIAS") ?: "workreport"
            keyPassword = System.getenv("WR_KEY_PASS") ?: "workreport2026"
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = false
            isShrinkResources = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = false
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        // 「关于」页要显示版本号，直接读 BuildConfig.VERSION_NAME，避免手写死
        buildConfig = true
    }

    packaging {
        resources {
            excludes += setOf(
                "/META-INF/{AL2.0,LGPL2.1}",
                "/META-INF/DEPENDENCIES",
                "/META-INF/*.kotlin_module",
                "DebugProbesKt.bin",
            )
        }
    }

    // 导出文件名含中文，保留 UTF-8
    androidResources {
        noCompress += listOf()
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.incremental", "true")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.splashscreen)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    debugImplementation(libs.androidx.ui.tooling)

    implementation(libs.androidx.navigation.compose)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.androidx.documentfile)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.okhttp)
    implementation(libs.zip4j)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
}

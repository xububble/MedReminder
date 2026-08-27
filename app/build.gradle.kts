plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val releaseKeystore = rootProject.file("keystore/medreminder.jks")
val releaseStorePassword = providers.gradleProperty("releaseStorePassword")
    .orElse(providers.environmentVariable("MEDREMINDER_STORE_PASSWORD"))
    .orNull
val releaseKeyPassword = providers.gradleProperty("releaseKeyPassword")
    .orElse(providers.environmentVariable("MEDREMINDER_KEY_PASSWORD"))
    .orNull

android {
    namespace = "com.zwen.medreminder"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.zwen.medreminder"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"
    }

    signingConfigs {
        if (releaseKeystore.exists() && releaseStorePassword != null && releaseKeyPassword != null) {
            create("release") {
                storeFile = releaseKeystore
                storePassword = releaseStorePassword
                keyAlias = "medreminder"
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (signingConfigs.names.contains("release")) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    // 零第三方依赖：纯平台 API + Kotlin 标准库，满足 <3MB 与「不引入第三方 UI 组件库」约束
}

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace = "vn.navara.launcher"
    compileSdk = 34
    defaultConfig {
        applicationId = "vn.navara.launcher"
        minSdk = 28
        targetSdk = 34
        versionCode = 17
        versionName = "0.17"
        // Tbox S1 là arm64: chỉ đóng gói thư viện native (MapLibre) cho arm64-v8a để APK nhẹ hơn nhiều.
        // Nếu máy báo không cài được hoặc bản đồ lỗi, thêm "armeabi-v7a" vào danh sách này.
        ndk { abiFilters += listOf("arm64-v8a") }
        resourceConfigurations += listOf("vi", "en")
        // Khoá MapTiler: khai báo MAPTILER_KEY=... trong gradle.properties (hoặc nhập trong Cài đặt của app)
        buildConfigField("String", "MAPTILER_KEY", "\"" + (project.findProperty("MAPTILER_KEY") ?: "") + "\"")
    }
    buildTypes {
        release {
            isMinifyEnabled = true          // R8: bỏ mã không dùng (đặc biệt bộ icon Material mở rộng rất nặng)
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("debug")   // ký bằng khoá debug để cài thẳng được; đổi sang khoá riêng nếu cần
        }
    }
    packaging {
        resources { excludes += listOf("/META-INF/{AL2.0,LGPL2.1}", "/META-INF/*.version", "DebugProbesKt.bin") }
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    composeOptions { kotlinCompilerExtensionVersion = "1.5.14" }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += listOf(
            "-opt-in=androidx.compose.foundation.ExperimentalFoundationApi",
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
            "-opt-in=androidx.compose.ui.text.ExperimentalTextApi"
        )
    }
}
dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("org.maplibre.gl:android-sdk:11.8.0")   // MapLibre Native: bản đồ vector mã nguồn mở
}

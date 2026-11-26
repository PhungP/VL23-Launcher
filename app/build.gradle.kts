import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
// Khoá phát hành: khai báo trong keystore.properties (nằm ở thư mục gốc dự án, đã có trong .gitignore, KHÔNG đưa lên Git).
// Có tệp này thì Android Studio / Gradle tự ký bản release bằng khoá đó. Không có thì bản release không được ký:
// dùng Build > Generate Signed App Bundle / APK của Android Studio (hỏi mật khẩu) hoặc ./release.sh.
// Không bao giờ ký release bằng khoá debug, vì khoá debug mỗi máy một khác và sẽ không cập nhật đè được bản đang cài.
val ksFile = rootProject.file("keystore.properties")
val ksProps = Properties().apply { if (ksFile.exists()) ksFile.inputStream().use { load(it) } }
val ksPath: String? = ksProps.getProperty("storeFile")?.let { if (it.startsWith("~")) System.getProperty("user.home") + it.substring(1) else it }
val useReleaseKey = ksPath != null && file(ksPath).exists() && System.getenv("VL23_NO_GRADLE_SIGN") == null

android {
    namespace = "vn.navara.launcher"
    compileSdk = 34
    defaultConfig {
        applicationId = "vn.navara.launcher"
        minSdk = 28
        targetSdk = 34
        versionCode = 30
        versionName = "0.30"
        // Tbox S1 là arm64: chỉ đóng gói thư viện native (MapLibre) cho arm64-v8a để APK nhẹ hơn nhiều.
        // Nếu máy báo không cài được hoặc bản đồ lỗi, thêm "armeabi-v7a" vào danh sách này.
        ndk { abiFilters += listOf("arm64-v8a") }
        resourceConfigurations += listOf("vi", "en")
        // Khoá MapTiler: khai báo MAPTILER_KEY=... trong gradle.properties (hoặc nhập trong Cài đặt của app)
        buildConfigField("String", "MAPTILER_KEY", "\"" + (project.findProperty("MAPTILER_KEY") ?: "") + "\"")
    }
    signingConfigs {
        if (useReleaseKey) create("vl23Release") {
            storeFile = file(ksPath!!)
            storePassword = ksProps.getProperty("storePassword")
            keyAlias = ksProps.getProperty("keyAlias", "key0")
            keyPassword = ksProps.getProperty("keyPassword") ?: ksProps.getProperty("storePassword")
        }
    }
    buildTypes {
        release {
            if (useReleaseKey) signingConfig = signingConfigs.getByName("vl23Release")
            isMinifyEnabled = true          // R8: bỏ mã không dùng (đặc biệt bộ icon Material mở rộng rất nặng)
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Không ký trong Gradle: bản release phải được ký bằng khoá phát hành thật qua ./release.sh (hỏi mật khẩu khi chạy).
            // Trước đây dùng khoá debug của từng máy build nên APK có thể bị khác khoá bản đang cài và không cập nhật đè được.
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
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")   // org.json thật cho unit test JVM (trên Android là thư viện hệ thống)
    implementation("org.maplibre.gl:android-sdk:11.8.0")   // MapLibre Native: bản đồ vector mã nguồn mở
}

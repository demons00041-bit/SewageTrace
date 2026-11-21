plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.sewagetrace.app"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.sewagetrace.app"
        minSdk = 26
        targetSdk = 37
        versionCode = 2
        versionName = "1.1-demo"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.09.00")
    implementation(composeBom)

    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("androidx.activity:activity-compose:1.12.4")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.4")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    // 地图：MapLibre Native + OpenFreeMap 矢量瓦片（开源、免 Key、免费用，支持 3D 倾斜）
    // 用 OpenGL ES 版而不是默认的 Vulkan 版：模拟器和老机型兼容性更好
    implementation("org.maplibre.gl:android-sdk-opengl:13.7.1")

    // 持久化：Room（SQLite），注解处理走 KSP
    implementation("androidx.room:room-runtime:2.8.5")
    ksp("androidx.room:room-compiler:2.8.5")

    debugImplementation("androidx.compose.ui:ui-tooling")

    // 单元测试（JVM）：android.jar 里的 org.json 是空桩，测试要用真实实现
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20250517")
}

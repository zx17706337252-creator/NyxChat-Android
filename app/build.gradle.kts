plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")   // version declared in root build.gradle.kts
    id("com.google.dagger.hilt.android")  // version declared in root build.gradle.kts
}

android {
    namespace = "com.nyxchat"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.nyxchat"
        minSdk = 26; targetSdk = 35
        versionCode = 1; versionName = "1.0"
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        debug {
            // 调试包不混淆，加快 CI 编译速度
            isMinifyEnabled = false
        }
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }

    // ── Lint ─────────────────────────────────────────────────────────────────
    // 默认行为：release 构建会因任何 lint error 中断。
    // abortOnError=false：让构建继续；htmlReport 方便在 CI Artifacts 中查看；
    // 忽略 NewApi（Compose 内部大量 @RequiresApi 会误报）和
    // OldTargetApi（security-crypto 1.1.0-alpha06 自身 targetSdkVersion 较旧）。
    lint {
        abortOnError = false
        htmlReport = true
        disable += setOf(
            "NewApi",            // Compose 内部大量 RequiresApi 注解，误报多
            "OldTargetApi",      // security-crypto alpha 库自身 targetSdk 较旧
            "MissingTranslation" // 没有多语言翻译，不应阻断构建
        )
    }

    // ── 打包冲突处理 ──────────────────────────────────────────────────────────
    // OkHttp / Kotlin 多个依赖携带相同的 META-INF 文件，不处理会导致
    // "More than one file was found with OS independent path" 错误。
    packaging {
        resources {
            excludes += setOf(
                "META-INF/DEPENDENCIES",
                "META-INF/LICENSE",
                "META-INF/LICENSE.txt",
                "META-INF/NOTICE",
                "META-INF/NOTICE.txt",
                "META-INF/*.kotlin_module",
                "META-INF/AL2.0",
                "META-INF/LGPL2.1"
            )
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.10.01")
    implementation(composeBom)
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui-text-google-fonts")
    implementation("androidx.navigation:navigation-compose:2.8.3")
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.google.code.gson:gson:2.11.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("io.coil-kt:coil-compose:2.7.0")
    // AppCompat (required by Theme.AppCompat used in themes.xml)
    implementation("androidx.appcompat:appcompat:1.7.0")
    // WorkManager
    implementation("androidx.work:work-runtime-ktx:2.9.1")
    // Room database
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")
    // Hilt DI
    implementation("com.google.dagger:hilt-android:2.51.1")
    ksp("com.google.dagger:hilt-compiler:2.51.1")
    implementation("androidx.hilt:hilt-navigation-compose:1.2.0")
    // Encrypted SharedPreferences (used by StorageManager for API key storage)
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
}
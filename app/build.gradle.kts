plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
    id("kotlin-parcelize")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "com.ihvpn.app"
    compileSdk = 36
    buildToolsVersion = "35.0.0" // CI'da (android-build.yml) kurulan sürümle eşleşir

    defaultConfig {
        applicationId = "com.ihvpn.app"
        minSdk = 29
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.3"
        vectorDrawables { useSupportLibrary = true }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        debug {
            isMinifyEnabled = false
            applicationIdSuffix = ".debug"
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    packaging {
        resources.excludes.add("META-INF/*")
    }
}

dependencies {
    // --- Compose ---
    val composeBom = platform("androidx.compose:compose-bom:2024.09.03")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.navigation:navigation-compose:2.8.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")

    // --- Room (yerel veritabanı: profiller, abonelikler, trafik geçmişi) ---
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // --- Güvenli depolama (profil şifreleri/UUID'leri şifreli saklamak için) ---
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    // --- Biyometrik uygulama kilidi ---
    implementation("androidx.biometric:biometric:1.1.0")

    // --- WorkManager (otomatik yeniden bağlanma, aylık sayaç sıfırlama, toplu ping testi) ---
    implementation("androidx.work:work-runtime-ktx:2.9.1")

    // --- JSON (Hiddify/Xray config ayrıştırma, yedekleme) ---
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")

    // --- Ağ (abonelik linki indirme) ---
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // --- QR kod tarama ---
    implementation("com.journeyapps:zxing-android-embedded:4.3.0")

    // --- Xray-core (gerçek VPN çekirdeği, xhttp dahil tüm transportları destekler) ---
    // DÜZELTME (2. kez): com.github.2dust:AndroidLibXrayLite bir Maven/JitPack paketi
    // DEĞİL — bu, Go kaynak kodu deposu; JitPack bunu derleyemiyor (Go/gomobile toolchain'i
    // yok), bu yüzden sürüm numarası ne olursa olsun "Could not find" hatası alıyorduk.
    // Gerçek kullanım şekli: bu depo Go+gomobile ile derlenip bir libv2ray.aar üretilir ve
    // o .aar dosya olarak projeye eklenir. CI (.github/workflows/android-build.yml) bunu
    // gomobile ile GERÇEKTEN derleyip app/libs/libv2ray.aar olarak buraya koyuyor.
    // Yerelde (Android Studio'da) derlemek için: CI'nin ürettiği "libv2ray-aar" artifact'ini
    // indirip app/libs/ klasörüne koy (bkz. README.md "Yerel derleme" bölümü).
    implementation(files("libs/libv2ray.aar"))

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.core:core-splashscreen:1.0.1")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
}
